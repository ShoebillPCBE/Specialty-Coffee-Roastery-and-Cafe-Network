package ro.upt.cafe.mcp.model;

/** Body POSTed to Server A to create a PENDING inter-cafe stock transfer. */
public record TransferRequest(String fromCafeId,
                              String toCafeId,
                              String batchId,
                              double quantityGrams,
                              String reason,
                              String proposedBy) {}
