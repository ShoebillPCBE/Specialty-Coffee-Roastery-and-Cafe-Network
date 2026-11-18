package ro.upt.cafe.agent.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(String zone,
                              boolean llmEnabled,
                              int atRiskWindowHours,
                              int salesDays,
                              int maxProposalsPerRun,
                              double maxTransferDistanceKm,
                              String menuUrl,
                              List<Cafe> cafes) {

    public record Cafe(String id, String name, double lat, double lng) {}

    public AgentProperties {
        cafes = cafes == null ? List.of() : List.copyOf(cafes);
    }
}
