package cersrt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thompson construction with cached epsilon closures and grouped outgoing transitions. */
public final class CompiledPattern {
  private static final Guard TRUE = (e, r) -> true;

  private record Transition(int target, Guard guard, boolean take, String register) {}

  private record Fragment(int start, int end) {}

  private record Label(Guard guard, boolean take, String register) {}

  record Edge(State target, Guard guard, boolean take, String register) {}

  static final class State {
    final BitSet nodes;
    final boolean accepting;
    private List<Edge> outgoing;

    State(BitSet nodes, boolean accepting) {
      this.nodes = (BitSet) nodes.clone();
      this.accepting = accepting;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof State state && nodes.equals(state.nodes);
    }

    @Override
    public int hashCode() {
      return nodes.hashCode();
    }
  }

  private final List<List<Transition>> graph = new ArrayList<>();
  private final Map<BitSet, State> states = new LinkedHashMap<>();
  private final BitSet[] closures;
  private final int finalNode;
  private final State start;

  private CompiledPattern(Expression expression) {
    Fragment fragment = build(expression, Expression.Selection.STRICT);
    finalNode = fragment.end;
    closures = new BitSet[graph.size()];
    for (int i = 0; i < graph.size(); i++) closures[i] = closure(i);
    start = state(closures[fragment.start]);
    // Materialize reachable closure states once, rather than discover/cache them in the stream
    // loop.
    List<State> pending = new ArrayList<>();
    pending.add(start);
    for (int i = 0; i < pending.size(); i++) {
      State current = pending.get(i);
      Map<Label, BitSet> targets = new LinkedHashMap<>();
      for (int node = current.nodes.nextSetBit(0);
          node >= 0;
          node = current.nodes.nextSetBit(node + 1)) {
        for (Transition transition : graph.get(node)) {
          if (transition.guard == null) continue;
          Label label = new Label(transition.guard, transition.take, transition.register);
          targets.computeIfAbsent(label, ignored -> new BitSet()).or(closures[transition.target]);
        }
      }
      List<Edge> edges = new ArrayList<>();
      for (var entry : targets.entrySet()) {
        boolean unseen = !states.containsKey(entry.getValue());
        State target = state(entry.getValue());
        if (unseen) pending.add(target);
        Label label = entry.getKey();
        edges.add(new Edge(target, label.guard, label.take, label.register));
      }
      current.outgoing = List.copyOf(edges);
    }
  }

  public static CompiledPattern compile(Expression expression) {
    return new CompiledPattern(expression);
  }

  public int stateCount() {
    return states.size();
  }

  public int transitionCount() {
    return states.values().stream().mapToInt(s -> s.outgoing.size()).sum();
  }

  State start() {
    return start;
  }

  List<Edge> outgoing(State state) {
    return state.outgoing;
  }

  private State state(BitSet nodes) {
    return states.computeIfAbsent(
        (BitSet) nodes.clone(), key -> new State(key, key.get(finalNode)));
  }

  private int node() {
    graph.add(new ArrayList<>());
    return graph.size() - 1;
  }

  private void epsilon(int from, int to) {
    graph.get(from).add(new Transition(to, null, false, null));
  }

  private void skip(int node, Guard guard) {
    graph.get(node).add(new Transition(node, guard, false, null));
  }

  private Fragment build(Expression expression, Expression.Selection selection) {
    if (expression instanceof Expression.Selected selected)
      return build(selected.child(), selected.selection());
    if (expression instanceof Expression.Atom atom) {
      int start = node();
      int end = node();
      graph.get(start).add(new Transition(end, atom.guard(), true, atom.register()));
      return new Fragment(start, end);
    }
    if (expression instanceof Expression.Epsilon) {
      int start = node();
      int end = node();
      epsilon(start, end);
      return new Fragment(start, end);
    }
    if (expression instanceof Expression.Sequence sequence) {
      Fragment result = build(sequence.children().get(0), Expression.Selection.STRICT);
      for (Expression child : sequence.children().subList(1, sequence.children().size())) {
        Fragment next = build(child, Expression.Selection.STRICT);
        int gap = node();
        epsilon(result.end, gap);
        epsilon(gap, next.start);
        if (selection == Expression.Selection.ANY) skip(gap, TRUE);
        else if (selection == Expression.Selection.NEXT) {
          Guard first = singleEventGuard(child);
          skip(gap, (e, r) -> !first.test(e, r));
        }
        result = new Fragment(result.start, next.end);
      }
      return result;
    }
    if (expression instanceof Expression.Choice choice) {
      int start = node();
      int end = node();
      for (Expression child : choice.children()) {
        Fragment branch = build(child, Expression.Selection.STRICT);
        epsilon(start, branch.start);
        epsilon(branch.end, end);
      }
      return new Fragment(start, end);
    }
    if (expression instanceof Expression.Repeat repeat) {
      int start = node();
      int end = node();
      Fragment child = build(repeat.child(), Expression.Selection.STRICT);
      epsilon(start, end);
      epsilon(start, child.start);
      epsilon(child.end, end);
      int gap = node();
      epsilon(child.end, gap);
      epsilon(gap, child.start);
      // A selected repetition has no leading/trailing gap inside its own expression.
      if (selection == Expression.Selection.ANY) skip(gap, TRUE);
      else if (selection == Expression.Selection.NEXT) {
        Guard first = singleEventGuard(repeat.child());
        skip(gap, (e, r) -> !first.test(e, r));
      }
      return new Fragment(start, end);
    }
    throw new IllegalArgumentException("Unknown expression: " + expression);
  }

  private Guard singleEventGuard(Expression expression) {
    if (expression instanceof Expression.Selected selected)
      return singleEventGuard(selected.child());
    if (expression instanceof Expression.Atom atom) return atom.guard();
    if (expression instanceof Expression.Choice choice) {
      List<Guard> guards = choice.children().stream().map(this::singleEventGuard).toList();
      return (e, r) -> guards.stream().anyMatch(g -> g.test(e, r));
    }
    throw new IllegalArgumentException(
        "Skip-till-next (@) supports single-event operands and their choices; composite complement"
            + " is unsupported by the source NSRA matcher");
  }

  private BitSet closure(int start) {
    BitSet result = new BitSet();
    ArrayDeque<Integer> pending = new ArrayDeque<>();
    result.set(start);
    pending.add(start);
    while (!pending.isEmpty()) {
      for (Transition transition : graph.get(pending.removeFirst())) {
        if (transition.guard == null && !result.get(transition.target)) {
          result.set(transition.target);
          pending.add(transition.target);
        }
      }
    }
    return result;
  }
}
