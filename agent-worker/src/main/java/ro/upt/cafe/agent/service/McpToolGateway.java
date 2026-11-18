package ro.upt.cafe.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ro.upt.cafe.agent.model.ToolInvocation;

/**
 * Thin layer over the Spring-AI-managed MCP clients (one per configured remote MCP server).
 * Connects lazily, lists tools and executes tool calls, recording every call for the run report.
 */
@Component
public class McpToolGateway {

    public static final String AT_RISK = "get_at_risk_batches";
    public static final String VELOCITY = "get_drink_sales_velocity";
    public static final String FLASH_PROMO = "propose_flash_menu_promo";
    public static final String TRANSFER = "propose_inter_cafe_transfer";
    static final Set<String> WRITE_TOOLS = Set.of(FLASH_PROMO, TRANSFER);

    private static final Logger log = LoggerFactory.getLogger(McpToolGateway.class);

    private final List<McpSyncClient> clients;
    private final ObjectMapper mapper;

    public McpToolGateway(List<McpSyncClient> clients, ObjectMapper mapper) {
        this.clients = clients;
        this.mapper = mapper;
    }

    /** All tools of all connected MCP servers. */
    public List<RemoteTool> listTools() {
        List<RemoteTool> tools = new ArrayList<>();
        for (McpSyncClient client : connectedClients()) {
            for (McpSchema.Tool tool : client.listTools().tools()) {
                tools.add(new RemoteTool(client, tool));
            }
        }
        return tools;
    }

    /** Calls a tool by name and records it into the given recorder. */
    public String call(String toolName, Map<String, Object> arguments, List<ToolInvocation> recorder) {
        RemoteTool tool = listTools().stream().filter(t -> t.name().equals(toolName)).findFirst()
                .orElseThrow(() -> new IllegalStateException("MCP tool not found: " + toolName));
        return execute(tool, arguments, recorder);
    }

    String execute(RemoteTool tool, Map<String, Object> arguments, List<ToolInvocation> recorder) {
        String argsJson = toJson(arguments);
        String result;
        boolean ok;
        try {
            McpSchema.CallToolResult response = tool.client().callTool(new McpSchema.CallToolRequest(tool.name(), arguments));
            result = response.content().stream()
                    .map(c -> c instanceof McpSchema.TextContent text ? text.text() : String.valueOf(c))
                    .collect(Collectors.joining("\n"));
            ok = !Boolean.TRUE.equals(response.isError()) && !result.contains("\"ok\":false");
        } catch (RuntimeException e) {
            log.warn("MCP tool {} failed: {}", tool.name(), e.getMessage());
            result = "{\"ok\":false,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
            ok = false;
        }
        synchronized (recorder) {
            recorder.add(new ToolInvocation(Instant.now(), tool.name(), argsJson, result, WRITE_TOOLS.contains(tool.name()), ok));
        }
        return result;
    }

    String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private List<McpSyncClient> connectedClients() {
        if (clients.isEmpty()) {
            throw new IllegalStateException("No MCP client configured (spring.ai.mcp.client.streamable-http.connections)");
        }
        for (McpSyncClient client : clients) {
            if (!client.isInitialized()) {
                client.initialize();
                log.info("Connected to MCP server {}", client.getServerInfo());
            }
        }
        return clients;
    }

    public record RemoteTool(McpSyncClient client, McpSchema.Tool tool) {
        public String name() {
            return tool.name();
        }
    }
}
