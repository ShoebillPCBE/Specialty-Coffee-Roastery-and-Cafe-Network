package ro.upt.cafe.mcp.tools;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import ro.upt.cafe.mcp.client.ServerAClient;
import ro.upt.cafe.mcp.client.ServerBClient;
import ro.upt.cafe.mcp.config.CafeProperties;
import ro.upt.cafe.mcp.model.AtRiskBatch;
import ro.upt.cafe.mcp.model.AtRiskReport;
import ro.upt.cafe.mcp.model.FlashPromoRequest;
import ro.upt.cafe.mcp.model.ToolResult;
import ro.upt.cafe.mcp.model.TransferRequest;

/**
 * The 4 MCP tools exposed by this server.
 * Parameter names are intentionally snake_case so the published JSON schema matches the
 * tool contract (cafe_id, hours_to_expiry_or_stale, ...).
 */
@Service
@SuppressWarnings("java:S117") // snake_case parameter names are part of the MCP contract
public class CafeNetworkTools {

    private static final Logger log = LoggerFactory.getLogger(CafeNetworkTools.class);
    static final String PROPOSED_BY = "ai-agent";

    private final ServerBClient serverB;
    private final ServerAClient serverA;
    private final FreshnessRules freshnessRules;
    private final VelocityCalculator velocityCalculator;
    private final CafeProperties.Freshness freshness;
    private final Clock clock;

    public CafeNetworkTools(ServerBClient serverB, ServerAClient serverA, FreshnessRules freshnessRules,
                            VelocityCalculator velocityCalculator, CafeProperties props, Clock clock) {
        this.serverB = serverB;
        this.serverA = serverA;
        this.freshnessRules = freshnessRules;
        this.velocityCalculator = velocityCalculator;
        this.freshness = props.freshness();
        this.clock = clock;
    }

    // ------------------------------------------------------------------ READ TOOLS (Server B)

    @Tool(name = "get_at_risk_batches", description = """
            READ tool. Returns the ingredient batches of one cafe that are going stale or expiring:
            coffee beans (espresso/filter/decaf) aged past 21 days since roast, matcha tins open longer
            than 25 days, and milk (dairy/oat/soy) or other perishables expiring within the given window.
            Batches that will cross a limit inside the window are included with severity MEDIUM.""")
    public Object getAtRiskBatches(
            @ToolParam(description = "Cafe identifier, e.g. CAFE-01") String cafe_id,
            @ToolParam(description = "Look-ahead window in hours for expiry/staleness (default 48)", required = false)
            Integer hours_to_expiry_or_stale) {
        int window = hours_to_expiry_or_stale == null || hours_to_expiry_or_stale <= 0
                ? freshness.milkExpiryWindowHours() : hours_to_expiry_or_stale;
        try {
            List<AtRiskBatch> atRisk = freshnessRules.evaluate(serverB.batches(cafe_id), window, LocalDateTime.now(clock));
            String rules = "coffee>%dd since roast, matcha>%dd open, perishables expiring within %dh"
                    .formatted(freshness.coffeeStaleAfterDays(), freshness.matchaOxidizedAfterDays(), window);
            return new AtRiskReport(cafe_id, LocalDateTime.now(clock).withNano(0).toString(), window, rules, atRisk.size(), atRisk);
        } catch (RestClientException e) {
            return failure("get_at_risk_batches", "Server B (query-service) unreachable or returned an error", e);
        }
    }

    @Tool(name = "get_drink_sales_velocity", description = """
            READ tool. Returns cup sales of one cafe over the last N days aggregated per drink family
            (ESPRESSO, DECAF, MATCHA, TEA, HOT_CHOCOLATE): total cups, average cups/day, last-day cups,
            trend vs average and a status of SLOW_MOVING (promo candidate), STEADY or ACCELERATING
            (may stock out before rush hour).""")
    public Object getDrinkSalesVelocity(
            @ToolParam(description = "Cafe identifier, e.g. CAFE-01") String cafe_id,
            @ToolParam(description = "Number of past days to analyse (default 7)", required = false) Integer days) {
        int window = days == null || days <= 0 ? 7 : Math.min(days, 90);
        try {
            return velocityCalculator.compute(cafe_id, window, serverB.dailySales(cafe_id, window));
        } catch (RestClientException e) {
            return failure("get_drink_sales_velocity", "Server B (query-service) unreachable or returned an error", e);
        }
    }

    // ------------------------------------------------------------------ WRITE TOOLS (Server A)

    @Tool(name = "propose_flash_menu_promo", description = """
            WRITE tool. Creates a PENDING happy-hour / flash-menu bundle on Server A that a barista or
            manager must approve. Use it to clear stock that is about to go stale, e.g. 25% off Iced Matcha
            and Hot Chocolate for 3 hours to use up 1.2L oat milk and matcha Tin #4 before oxidation.
            The reason is shown to the manager - explain which batches it clears and why.""")
    public ToolResult proposeFlashMenuPromo(
            @ToolParam(description = "Cafe identifier where the promo runs") String cafe_id,
            @ToolParam(description = "Menu item ids included in the bundle (from the cafe menu)") List<String> menu_item_ids,
            @ToolParam(description = "Discount percentage, 5-50") Integer discount_pct,
            @ToolParam(description = "How long the promo lasts, in hours (1-12)") Integer duration_hours,
            @ToolParam(description = "Human-readable reasoning: which at-risk batches/slow sellers this addresses") String reason) {
        if (isBlank(cafe_id) || menu_item_ids == null || menu_item_ids.isEmpty()) {
            return ToolResult.error("cafe_id and at least one menu_item_id are required");
        }
        if (discount_pct == null || discount_pct < 5 || discount_pct > 50) {
            return ToolResult.error("discount_pct must be between 5 and 50");
        }
        if (duration_hours == null || duration_hours < 1 || duration_hours > 12) {
            return ToolResult.error("duration_hours must be between 1 and 12");
        }
        if (isBlank(reason)) {
            return ToolResult.error("reason is required so the manager can review the proposal");
        }
        try {
            Map<String, Object> created = serverA.proposeFlashPromo(new FlashPromoRequest(
                    cafe_id, menu_item_ids, discount_pct, duration_hours, reason.trim(), PROPOSED_BY));
            log.info("Flash promo proposed for {}: {}% on {} for {}h", cafe_id, discount_pct, menu_item_ids, duration_hours);
            return ToolResult.ok("PENDING flash promo created on Server A; awaiting manager approval", created);
        } catch (RestClientException e) {
            return failure("propose_flash_menu_promo", "Server A (command-service) rejected or is unreachable", e);
        }
    }

    @Tool(name = "propose_inter_cafe_transfer", description = """
            WRITE tool. Creates a PENDING inter-cafe stock transfer on Server A, e.g. moving surplus decaf
            beans or ceremonial matcha from a slow cafe to a high-demand cafe nearby before it goes stale
            or the other cafe stocks out. Requires manager approval.""")
    public ToolResult proposeInterCafeTransfer(
            @ToolParam(description = "Cafe that has the surplus") String from_cafe_id,
            @ToolParam(description = "Cafe that needs the stock") String to_cafe_id,
            @ToolParam(description = "Batch id to move (from get_at_risk_batches)") String batch_id,
            @ToolParam(description = "Quantity to move, in grams (or ml for milk)") Double quantity_grams,
            @ToolParam(description = "Human-readable reasoning shown to the manager") String reason) {
        if (isBlank(from_cafe_id) || isBlank(to_cafe_id) || isBlank(batch_id)) {
            return ToolResult.error("from_cafe_id, to_cafe_id and batch_id are required");
        }
        if (from_cafe_id.equalsIgnoreCase(to_cafe_id)) {
            return ToolResult.error("from_cafe_id and to_cafe_id must be different cafes");
        }
        if (quantity_grams == null || quantity_grams <= 0) {
            return ToolResult.error("quantity_grams must be positive");
        }
        if (isBlank(reason)) {
            return ToolResult.error("reason is required so the manager can review the proposal");
        }
        try {
            Map<String, Object> created = serverA.proposeTransfer(new TransferRequest(
                    from_cafe_id, to_cafe_id, batch_id, quantity_grams, reason.trim(), PROPOSED_BY));
            log.info("Transfer proposed: {} g of {} from {} to {}", quantity_grams, batch_id, from_cafe_id, to_cafe_id);
            return ToolResult.ok("PENDING inter-cafe transfer created on Server A; awaiting manager approval", created);
        } catch (RestClientException e) {
            return failure("propose_inter_cafe_transfer", "Server A (command-service) rejected or is unreachable", e);
        }
    }

    private static ToolResult failure(String tool, String message, Exception e) {
        log.warn("{} failed: {}", tool, e.getMessage());
        return ToolResult.error(message + ": " + e.getMessage());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
