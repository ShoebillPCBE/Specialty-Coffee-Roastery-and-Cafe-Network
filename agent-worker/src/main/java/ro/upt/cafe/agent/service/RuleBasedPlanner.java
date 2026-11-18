package ro.upt.cafe.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import ro.upt.cafe.agent.config.AgentProperties;

/**
 * Deterministic fallback used when no LLM key is configured or the LLM call fails, so the
 * live demo (POST /api/v1/agent/trigger-now) always produces sensible proposals.
 */
@Component
public class RuleBasedPlanner {

    /** Executes an MCP tool and returns its JSON result. */
    @FunctionalInterface
    public interface ToolExecutor {
        String call(String tool, Map<String, Object> arguments);
    }

    static final Set<String> TRANSFERABLE = Set.of("AGING_COFFEE", "MATCHA_NEAR_OXIDATION");
    static final String TAG = "[rule-based] ";

    private final AgentProperties props;
    private final ObjectMapper mapper;

    public RuleBasedPlanner(AgentProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public String plan(NetworkSnapshot snapshot, ToolExecutor tools, List<String> warnings) {
        Map<String, Map<String, JsonNode>> velocityByCafe = new LinkedHashMap<>();
        for (NetworkSnapshot.CafeSnapshot c : snapshot.cafes()) {
            velocityByCafe.put(c.cafe().id(), velocityIndex(c.salesVelocityJson()));
        }

        List<String> summary = new ArrayList<>();
        int budget = props.maxProposalsPerRun();

        for (NetworkSnapshot.CafeSnapshot c : snapshot.cafes()) {
            String cafeId = c.cafe().id();
            JsonNode atRisk = parse(c.atRiskBatchesJson());
            JsonNode batches = atRisk.path("batches");
            if (!batches.isArray() || batches.isEmpty()) {
                if (!atRisk.path("ok").asBoolean(true)) {
                    warnings.add(cafeId + ": " + atRisk.path("message").asText());
                }
                continue;
            }

            Set<String> promoFamilies = new LinkedHashSet<>();
            List<String> promoReasons = new ArrayList<>();
            boolean urgent = false;

            for (JsonNode b : batches) {
                if (budget <= 0) break;
                String batchId = b.path("batchId").asText();
                String riskType = b.path("riskType").asText();
                String category = b.path("category").asText().toUpperCase(Locale.ROOT);
                double qty = b.path("remainingQuantity").asDouble();
                String note = b.path("note").asText();
                String label = "%s %s (%s, %s %s)".formatted(b.path("name").asText(batchId), batchId, note,
                        trim(qty), b.path("unit").asText("g"));

                if ("EXPIRED".equals(riskType)) {
                    warnings.add(cafeId + ": " + label + " is expired - discard, not promoted");
                    continue;
                }
                List<String> families = familiesFor(category);
                String family = families.get(0);

                if (TRANSFERABLE.contains(riskType)) {
                    Optional<String> target = bestTransferTarget(cafeId, family, snapshot, velocityByCafe);
                    JsonNode here = velocityByCafe.get(cafeId).get(family);
                    boolean slowHere = here == null || "SLOW_MOVING".equals(here.path("status").asText());
                    if (target.isPresent() && slowHere) {
                        double move = Math.max(50, Math.floor(qty / 2 / 50) * 50);
                        String to = target.get();
                        JsonNode there = velocityByCafe.get(to).get(family);
                        String reason = TAG + "Move %s g of %s from %s (sells %s %s cups/day) to %s (%s cups/day, %s, %s km away) before it passes its freshness limit."
                                .formatted(trim(move), label, cafeId, avg(here), family, to, avg(there),
                                        there.path("status").asText(), snapshot.distancesKm().get(cafeId).get(to));
                        String result = tools.call(McpToolGateway.TRANSFER, Map.of(
                                "from_cafe_id", cafeId, "to_cafe_id", to, "batch_id", batchId,
                                "quantity_grams", move, "reason", reason));
                        budget--;
                        summary.add("Transfer %s g %s %s -> %s: %s".formatted(trim(move), batchId, cafeId, to, okOrFail(result)));
                        continue;
                    }
                }
                promoFamilies.addAll(families);
                promoReasons.add(label);
                urgent |= Set.of("HIGH", "CRITICAL").contains(b.path("severity").asText());
            }

            if (!promoFamilies.isEmpty() && budget > 0) {
                List<MenuCatalog.MenuItemRef> items = snapshot.menu().stream()
                        .filter(m -> promoFamilies.contains(MenuCatalog.family(m)))
                        .limit(4)
                        .toList();
                if (items.isEmpty()) {
                    warnings.add(cafeId + ": no menu items found for " + promoFamilies + " - promo skipped");
                    continue;
                }
                int discount = urgent ? 25 : 15;
                String names = String.join(" & ", items.stream().map(MenuCatalog.MenuItemRef::name).toList());
                String reason = TAG + "%d%% off %s for 3h (14:00-17:00) to use up %s before it goes to waste."
                        .formatted(discount, names, String.join("; ", promoReasons));
                String result = tools.call(McpToolGateway.FLASH_PROMO, Map.of(
                        "cafe_id", cafeId,
                        "menu_item_ids", items.stream().map(MenuCatalog.MenuItemRef::itemId).toList(),
                        "discount_pct", discount, "duration_hours", 3, "reason", reason));
                budget--;
                summary.add("Flash promo %s %d%% on %s: %s".formatted(cafeId, discount, names, okOrFail(result)));
            }
        }
        if (summary.isEmpty()) {
            return "Rule-based planner: nothing at risk that needs a proposal today.";
        }
        return "Rule-based planner created:\n- " + String.join("\n- ", summary);
    }

    /** Nearby cafe where this drink family sells faster (prefers ACCELERATING, then highest avg). */
    Optional<String> bestTransferTarget(String fromCafe, String family, NetworkSnapshot snapshot,
                                        Map<String, Map<String, JsonNode>> velocity) {
        double hereAvg = Optional.ofNullable(velocity.get(fromCafe).get(family))
                .map(n -> n.path("avgCupsPerDay").asDouble()).orElse(0.0);
        String best = null;
        double bestScore = 0;
        for (Map.Entry<String, Double> e : snapshot.distancesKm().getOrDefault(fromCafe, Map.of()).entrySet()) {
            if (e.getValue() > props.maxTransferDistanceKm()) continue;
            JsonNode v = velocity.getOrDefault(e.getKey(), Map.of()).get(family);
            if (v == null) continue;
            double avg = v.path("avgCupsPerDay").asDouble();
            boolean accelerating = "ACCELERATING".equals(v.path("status").asText());
            if (!accelerating && avg < hereAvg * 1.5 + 1) continue;
            double score = avg + (accelerating ? 1000 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return Optional.ofNullable(best);
    }

    static List<String> familiesFor(String category) {
        return switch (category) {
            case "DECAF_BEAN" -> List.of("DECAF");
            case "ESPRESSO_BEAN", "FILTER_BEAN" -> List.of("ESPRESSO");
            case "MATCHA_TIN" -> List.of("MATCHA");
            case "LOOSE_TEA" -> List.of("TEA");
            case "COCOA_BATCH" -> List.of("HOT_CHOCOLATE");
            default -> List.of("MATCHA", "HOT_CHOCOLATE"); // milk: milk-heavy drinks
        };
    }

    private Map<String, JsonNode> velocityIndex(String json) {
        Map<String, JsonNode> index = new LinkedHashMap<>();
        for (JsonNode d : parse(json).path("drinks")) {
            index.put(d.path("beverageGroup").asText(), d);
        }
        return index;
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private String okOrFail(String result) {
        JsonNode r = parse(result);
        return r.path("ok").asBoolean(false) ? "PENDING" : "FAILED (" + r.path("message").asText() + ")";
    }

    private static String avg(JsonNode velocity) {
        return velocity == null ? "0" : trim(velocity.path("avgCupsPerDay").asDouble());
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
