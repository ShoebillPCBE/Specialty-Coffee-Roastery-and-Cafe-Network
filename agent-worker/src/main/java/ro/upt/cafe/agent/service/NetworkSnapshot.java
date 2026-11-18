package ro.upt.cafe.agent.service;

import java.util.List;
import java.util.Map;
import ro.upt.cafe.agent.config.AgentProperties.Cafe;

/** Everything the agent read this morning, handed to the LLM (or the rule-based planner). */
public record NetworkSnapshot(List<CafeSnapshot> cafes,
                              Map<String, Map<String, Double>> distancesKm,
                              List<MenuCatalog.MenuItemRef> menu) {

    /** Raw JSON returned by the two MCP read tools for one cafe. */
    public record CafeSnapshot(Cafe cafe, String atRiskBatchesJson, String salesVelocityJson) {}
}
