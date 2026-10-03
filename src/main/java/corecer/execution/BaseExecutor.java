package corecer.execution;

import corecer.execution.cea.Traverser;
import corecer.execution.structures.CDS.CDSNode;
import corecer.execution.structures.output.CDSComplexEventGrouping;
import corecer.execution.structures.states.State;
import corecer.execution.watcher.ExecutorWatcher;
import corecer.parser.plan.LogicalPlan;
import corecer.parser.plan.query.ConsumptionPolicy;
import corecer.parser.plan.query.TimeWindow;
import corecer.runtime.events.Event;
import corecer.runtime.predicates.BitSetGenerator;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

public abstract class BaseExecutor {

    Consumer<CDSComplexEventGrouping> matchCallback;
    String query;

    final Traverser traverser;
    final BitSetGenerator bitSetGenerator;
    final boolean discardPartials;
    final ExecutorWatcher watcher = new ExecutorWatcher();

    Map<State<?>, CDSNode> states;
    Collection<State<?>> activeFinalStates;


    BaseExecutor(Traverser traverser, BitSetGenerator bitSetGenerator, boolean discardPartials) {
        this.traverser = traverser;
        this.bitSetGenerator = bitSetGenerator;
        this.discardPartials = discardPartials;
        setupCleanExecutor();
    }

    public static BaseExecutor fromPlan(LogicalPlan plan) {
        ExecutorFactory executorFactory = new ExecutorFactory(plan);
        if (plan.getPartitions().isEmpty()) {
            if (plan.getTimeWindow().getKind() == TimeWindow.Kind.NONE) {
                return executorFactory.newSimpleExecutor();
            } else {
                return executorFactory.newTimeWindowExecutor(plan.getTimeWindow());
            }
        }
        return executorFactory.newPartitionExecutor(plan);

    }

    /**
     * Use the set callback to enumerate the outputs generated with a triggering
     * {@link Event}. Clean the executor if the {@link ConsumptionPolicy} is ANY.
     */
    void enumerate(Event triggeringEvent) {
        enumerate(triggeringEvent, 0);
    }

    void enumerate(Event triggeringEvent, long limit) {
        watcher.update();
        if (matchCallback != null) {
            CDSComplexEventGrouping complexEventGrouping = new CDSComplexEventGrouping(triggeringEvent, limit);
            for (State<?> state : activeFinalStates) {
                complexEventGrouping.addCDSNode(states.get(state));
            }
            matchCallback.accept(complexEventGrouping);
        }
        if (discardPartials) {
            setupCleanExecutor();
        }
    }

    /**
     * Clean the executor. This is called after triggering output and having the ANY
     * {@link corecer.parser.plan.query.ConsumptionPolicy ConsumptionPolicy}.
     *
     */
    abstract void setupCleanExecutor();

    /**
     * Receives a new {@link Event} and updates the states the CEA is currently
     * executing.
     *
     * @param event
     */
    public abstract boolean sendEvent(Event event);

    /**
     * Sets the given function as a callback for when a triggering {@link Event} is
     * found, for it to enumerate the output. This callback receives a
     * {@link CDSComplexEventGrouping} as argument.
     *
     * @param callback The callback function to use for enumeration.
     */
    public void setMatchCallback(Consumer<CDSComplexEventGrouping> callback) {
        matchCallback = callback;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public ExecutorWatcher getWatcher() {
        return watcher;
    }

    public JSONObject toJSON() {
        JSONObject out = new JSONObject();
        out.put("query", query);
        out.put("stats", watcher.toJSON());

        return out;
    }
}
