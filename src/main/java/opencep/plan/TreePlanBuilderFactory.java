package opencep.plan;

public final class TreePlanBuilderFactory {
  private TreePlanBuilderFactory() {}

  public static TreePlanBuilder createTreePlanBuilder(TreePlanBuilderParameters params) {
    return new TreePlanBuilder(params == null ? new TreePlanBuilderParameters() : params);
  }
}
