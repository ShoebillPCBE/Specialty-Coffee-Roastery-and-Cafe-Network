package ro.upt.cafe.mcp.model;

import java.util.List;

public record AtRiskReport(String cafeId,
                           String evaluatedAt,
                           int windowHours,
                           String rules,
                           int atRiskCount,
                           List<AtRiskBatch> batches) {}
