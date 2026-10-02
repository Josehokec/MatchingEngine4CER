package opencep.parallel;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import opencep.base.*;
import opencep.evaluation.*;
import opencep.stream.OutputStream;

/** Each worker has an independent tree; bounded mailboxes provide backpressure. */
public final class DataParallelEvaluationManager implements EvaluationManager {
  private final List<Pattern> patterns;
  private final EvaluationMechanismParameters evaluation;
  private final ParallelExecutionParameters parameters;
  private final List<TreeBasedEvaluationMechanism> mechanisms = new ArrayList<>();

  public DataParallelEvaluationManager(
      List<Pattern> patterns,
      EvaluationMechanismParameters evaluation,
      ParallelExecutionParameters parameters) {
    this.patterns = List.copyOf(patterns);
    this.evaluation = evaluation;
    this.parameters = parameters;
    if (evaluation.optimizer().isAdaptivityEnabled())
      throw new IllegalArgumentException(
          "Adaptive statistics collectors require sequential execution");
    for (var p : patterns)
      if (p.consumptionPolicy.strategy != opencep.misc.SelectionStrategies.MATCH_ANY
          || !p.consumptionPolicy.freezeNames.isEmpty())
        throw new IllegalArgumentException(
            "Single/next/freeze policies require sequential execution");
  }

  public void eval(Iterable<Event> events, OutputStream<PatternMatch> matches) {
    Duration window =
        patterns.stream().map(p -> p.window).max(Comparator.naturalOrder()).orElseThrow();
    DataParallelExecutionAlgorithm routing =
        switch (parameters.algorithm()) {
          case RIP_ALGORITHM -> new RIPParallelExecutionAlgorithm(
              parameters.units(), window, parameters.multiple());
          case GROUP_BY_KEY_ALGORITHM -> new GroupByKeyParallelExecutionAlgorithm(
              parameters.units(), parameters.key());
          case HYPER_CUBE_ALGORITHM -> new HyperCubeParallelExecutionAlgorithm(
              parameters.units(), parameters.attributes(), patterns);
        };
    int count = routing.units();
    var failure = new AtomicReference<Throwable>();
    Object end = new Object();
    var queues = new ArrayList<BlockingQueue<Object>>();
    var futures = new ArrayList<Future<?>>();
    mechanisms.clear();
    for (int i = 0; i < count; i++) {
      final int unit = i;
      var queue = new ArrayBlockingQueue<Object>(1024);
      queues.add(queue);
      var mechanism =
          new TreeBasedEvaluationMechanism(
              patterns,
              evaluation,
              m -> {
                if (routing.isOwner(unit, m)) matches.addItem(m);
              });
      mechanisms.add(mechanism);
    }
    var executor = Executors.newFixedThreadPool(count);
    try {
      for (int i = 0; i < count; i++) {
        final int unit = i;
        var queue = queues.get(i);
        var mechanism = mechanisms.get(i);
        futures.add(
            executor.submit(
                () -> {
                  try {
                    Object item;
                    while ((item = queue.take()) != end) mechanism.handleEvent((Event) item);
                    mechanism.finish();
                  } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                    throw new IllegalStateException("Worker " + unit + " failed", e);
                  } finally {
                    mechanism.close();
                  }
                }));
      }
      Instant latest = null;
      for (var event : events) {
        if (latest != null && event.timestamp.isBefore(latest))
          throw new IllegalArgumentException("Input timestamps must be nondecreasing");
        latest = event.timestamp;
        for (int unit : routing.classify(event)) send(queues.get(unit), event, failure);
      }
      for (var queue : queues) send(queue, end, failure);
      for (var future : futures) future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Parallel evaluation interrupted", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Parallel evaluation failed", e.getCause());
    } finally {
      executor.shutdownNow();
      matches.close();
    }
  }

  private static void send(
      BlockingQueue<Object> queue, Object item, AtomicReference<Throwable> failure)
      throws InterruptedException {
    while (!queue.offer(item, 100, TimeUnit.MILLISECONDS))
      if (failure.get() != null)
        throw new IllegalStateException("Parallel worker failed", failure.get());
    if (failure.get() != null)
      throw new IllegalStateException("Parallel worker failed", failure.get());
  }

  public String getStructureSummary() {
    return mechanisms.stream()
        .map(TreeBasedEvaluationMechanism::getStructureSummary)
        .toList()
        .toString();
  }
}
