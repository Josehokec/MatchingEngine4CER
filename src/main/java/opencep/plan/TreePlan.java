package opencep.plan;

import opencep.base.Pattern;

public record TreePlan(Pattern pattern, TreePlanNode root, double cost) {}
