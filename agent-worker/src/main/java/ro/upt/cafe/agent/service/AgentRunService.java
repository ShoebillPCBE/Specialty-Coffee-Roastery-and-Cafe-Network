package ro.upt.cafe.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ro.upt.cafe.agent.config.AgentProperties;
import ro.upt.cafe.agent.config.AgentProperties.Cafe;
import ro.upt.cafe.agent.model.AgentRun;
import ro.upt.cafe.agent.model.AgentRun.Mode;
import ro.upt.cafe.agent.model.AgentRun.Status;
import ro.upt.cafe.agent.model.AgentRun.Trigger;
import ro.upt.cafe.agent.model.ToolInvocation;

/**
 * One agent cycle: read every cafe via the MCP read tools, then let the LLM (or the rule-based
 * fallback) call the MCP write tools to create PENDING proposals on Server A.
 */
@Service
public class AgentRunService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunService.class);
    private static final int HISTORY_SIZE = 20;

    private final McpToolGateway gateway;
    private final MenuCatalog menuCatalog;
    private final LlmPlanner llmPlanner;
    private final RuleBasedPlanner ruleBasedPlanner;
    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final String apiKey;

    private final ReentrantLock running = new ReentrantLock();
    private final Deque<AgentRun> history = new ArrayDeque<>();

    public AgentRunService(McpToolGateway gateway, MenuCatalog menuCatalog, LlmPlanner llmPlanner,
                           RuleBasedPlanner ruleBasedPlanner, AgentProperties props, ObjectMapper mapper,
                           @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.gateway = gateway;
        this.menuCatalog = menuCatalog;
        this.llmPlanner = llmPlanner;
        this.ruleBasedPlanner = ruleBasedPlanner;
        this.props = props;
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    /** Runs a cycle, or returns empty if another cycle is still in progress. */
    public Optional<AgentRun> runCycle(Trigger trigger) {
        if (!running.tryLock()) {
            return Optional.empty();
        }
        try {
            AgentRun run = doRun(trigger);
            synchronized (history) {
                history.addFirst(run);
                while (history.size() > HISTORY_SIZE) history.removeLast();
            }
            return Optional.of(run);
        } finally {
            running.unlock();
        }
    }

    public List<AgentRun> history() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    public boolean llmConfigured() {
        return props.llmEnabled() && apiKey != null && !apiKey.isBlank() && !"not-set".equals(apiKey);
    }

    private AgentRun doRun(Trigger trigger) {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        Instant started = Instant.now();
        List<ToolInvocation> calls = Collections.synchronizedList(new ArrayList<>());
        List<String> warnings = new ArrayList<>();
        log.info("Agent run {} ({}) started for {} cafes", runId, trigger, props.cafes().size());

        NetworkSnapshot snapshot;
        try {
            snapshot = readNetwork(calls, warnings);
        } catch (RuntimeException e) {
            log.error("Agent run {} could not read the network via MCP", runId, e);
            warnings.add("MCP server unreachable: " + e.getMessage());
            return new AgentRun(runId, trigger, Mode.RULE_BASED, Status.FAILED, started, Instant.now(),
                    List.of(), "Could not reach the MCP server - no proposals created.", 0, List.copyOf(calls), warnings);
        }

        Mode mode = Mode.RULE_BASED;
        String summary = null;
        if (llmConfigured()) {
            try {
                List<ToolCallback> tools = gateway.listTools().stream()
                        .<ToolCallback>map(t -> new RecordingMcpToolCallback(gateway, t, calls, mapper))
                        .toList();
                summary = llmPlanner.plan(gateway.toJson(snapshotForLlm(snapshot)), tools);
                mode = Mode.LLM;
            } catch (RuntimeException e) {
                log.warn("LLM planning failed, falling back to rule-based planner: {}", e.getMessage());
                warnings.add("LLM unavailable (" + e.getMessage() + ") - used rule-based planner");
            }
        } else {
            warnings.add("No LLM API key configured (AI_API_KEY) - used rule-based planner");
        }
        if (mode == Mode.RULE_BASED) {
            summary = ruleBasedPlanner.plan(snapshot, (tool, args) -> gateway.call(tool, args, calls), warnings);
        }

        List<ToolInvocation> recorded = List.copyOf(calls);
        long proposals = recorded.stream().filter(c -> c.write() && c.ok()).count();
        long failedWrites = recorded.stream().filter(c -> c.write() && !c.ok()).count();
        Status status = failedWrites > 0 || hasReadFailures(recorded) ? Status.PARTIAL : Status.SUCCESS;
        AgentRun run = new AgentRun(runId, trigger, mode, status, started, Instant.now(),
                snapshot.cafes().stream().map(c -> c.cafe().id()).toList(),
                summary, (int) proposals, recorded, warnings);
        log.info("Agent run {} finished: mode={} proposals={} status={}", runId, mode, proposals, status);
        return run;
    }

    private NetworkSnapshot readNetwork(List<ToolInvocation> calls, List<String> warnings) {
        List<NetworkSnapshot.CafeSnapshot> cafes = new ArrayList<>();
        for (Cafe cafe : props.cafes()) {
            String atRisk = gateway.call(McpToolGateway.AT_RISK,
                    Map.of("cafe_id", cafe.id(), "hours_to_expiry_or_stale", props.atRiskWindowHours()), calls);
            String velocity = gateway.call(McpToolGateway.VELOCITY,
                    Map.of("cafe_id", cafe.id(), "days", props.salesDays()), calls);
            cafes.add(new NetworkSnapshot.CafeSnapshot(cafe, atRisk, velocity));
        }
        List<MenuCatalog.MenuItemRef> menu = List.of();
        try {
            menu = menuCatalog.load();
        } catch (RuntimeException e) {
            warnings.add("Menu not available from Server A: " + e.getMessage());
        }
        return new NetworkSnapshot(cafes, Geo.distanceMatrix(props.cafes()), menu);
    }

    /** Embeds the tool outputs as JSON objects (not escaped strings) to keep the prompt readable. */
    private Map<String, Object> snapshotForLlm(NetworkSnapshot snapshot) {
        List<Map<String, Object>> cafes = new ArrayList<>();
        for (NetworkSnapshot.CafeSnapshot c : snapshot.cafes()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cafe_id", c.cafe().id());
            m.put("name", c.cafe().name());
            m.put("get_at_risk_batches", readTree(c.atRiskBatchesJson()));
            m.put("get_drink_sales_velocity", readTree(c.salesVelocityJson()));
            cafes.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("cafes", cafes);
        root.put("distances_km", snapshot.distancesKm());
        root.put("menu", snapshot.menu());
        return root;
    }

    private Object readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }

    private static boolean hasReadFailures(List<ToolInvocation> calls) {
        return calls.stream().anyMatch(c -> !c.write() && !c.ok());
    }
}
