package ro.upt.cafe.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import ro.upt.cafe.agent.model.ToolInvocation;

/**
 * Exposes one remote MCP tool to the LLM (Spring AI ToolCallback) and records each invocation,
 * so the run report shows exactly which proposals the model created and why.
 */
class RecordingMcpToolCallback implements ToolCallback {

    private final McpToolGateway gateway;
    private final McpToolGateway.RemoteTool tool;
    private final List<ToolInvocation> recorder;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    RecordingMcpToolCallback(McpToolGateway gateway, McpToolGateway.RemoteTool tool,
                             List<ToolInvocation> recorder, ObjectMapper mapper) {
        this.gateway = gateway;
        this.tool = tool;
        this.recorder = recorder;
        this.mapper = mapper;
        this.definition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.tool().description())
                .inputSchema(gateway.toJson(tool.tool().inputSchema()))
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        Map<String, Object> args;
        try {
            args = toolInput == null || toolInput.isBlank() ? Map.of()
                    : mapper.readValue(toolInput, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return "{\"ok\":false,\"message\":\"arguments must be a JSON object\"}";
        }
        return gateway.execute(tool, args, recorder);
    }
}
