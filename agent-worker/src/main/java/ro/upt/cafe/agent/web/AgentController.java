package ro.upt.cafe.agent.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.upt.cafe.agent.model.AgentRun;
import ro.upt.cafe.agent.service.AgentRunService;
import ro.upt.cafe.agent.service.McpToolGateway;

@RestController
@RequestMapping("/api/v1/agent")
@CrossOrigin
public class AgentController {

    private final AgentRunService agent;
    private final McpToolGateway gateway;

    public AgentController(AgentRunService agent, McpToolGateway gateway) {
        this.agent = agent;
        this.gateway = gateway;
    }

    /** Runs the morning cycle immediately (live demo / lab grading). */
    @PostMapping("/trigger-now")
    public ResponseEntity<Object> triggerNow() {
        return agent.runCycle(AgentRun.Trigger.MANUAL)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "An agent run is already in progress, try again in a moment")));
    }

    @GetMapping("/runs")
    public List<AgentRun> runs() {
        return agent.history();
    }

    @GetMapping("/runs/latest")
    public ResponseEntity<AgentRun> latest() {
        List<AgentRun> runs = agent.history();
        return runs.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(runs.get(0));
    }

    /** Tools discovered on the remote MCP server + whether the LLM is configured. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Object tools;
        try {
            tools = gateway.listTools().stream().map(McpToolGateway.RemoteTool::name).toList();
        } catch (RuntimeException e) {
            tools = "MCP server unreachable: " + e.getMessage();
        }
        return Map.of("llmConfigured", agent.llmConfigured(), "mcpTools", tools,
                "schedule", "0 0 7 * * * (Europe/Bucharest)");
    }
}
