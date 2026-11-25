package ro.upt.cafe.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * One row of the daily cup-sales projection served by Server B.
 * Also accepts raw OrderPlacedEvent rows from command-service
 * (timestamp + beverageGroup + cupsSold); VelocityCalculator sums them per day.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesRecord(LocalDate date, String beverageGroup, int cups) {

    @JsonCreator
    public static SalesRecord fromJson(@JsonProperty("date") String date,
                                       @JsonProperty("timestamp") String timestamp,
                                       @JsonProperty("beverageGroup") String beverageGroup,
                                       @JsonProperty("cups") Integer cups,
                                       @JsonProperty("cupsSold") Integer cupsSold) {
        return new SalesRecord(BatchView.parseDate(date != null ? date : timestamp), beverageGroup,
                cups != null ? cups : cupsSold != null ? cupsSold : 0);
    }
}
