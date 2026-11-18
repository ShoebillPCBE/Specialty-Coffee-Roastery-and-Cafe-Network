package ro.upt.cafe.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/** One row of the daily cup-sales projection served by Server B. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesRecord(LocalDate date, String beverageGroup, int cups) {}
