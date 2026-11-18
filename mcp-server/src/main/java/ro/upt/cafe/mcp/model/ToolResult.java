package ro.upt.cafe.mcp.model;

import java.util.Map;

/** Uniform envelope for write tools / failures so the LLM always gets parseable JSON. */
public record ToolResult(boolean ok, String message, Map<String, Object> data) {

    public static ToolResult ok(String message, Map<String, Object> data) {
        return new ToolResult(true, message, data);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message, Map.of());
    }
}
