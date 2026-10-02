package opencep.plan;

public record LocalSearchParameters(
    Approach approach,
    int stepLimit,
    int neighborhoodSize,
    int tabuCapacity,
    double cooling,
    long seed) {
  public enum Approach {
    TABU_SEARCH,
    SIMULATED_ANNEALING
  }

  public LocalSearchParameters() {
    this(Approach.TABU_SEARCH, 100, 20, 1000, 0.99, 0);
  }

  public LocalSearchParameters {
    if (approach == null
        || stepLimit < 0
        || neighborhoodSize < 1
        || tabuCapacity < 1
        || cooling <= 0
        || cooling >= 1) throw new IllegalArgumentException("Invalid local-search parameters");
  }
}
