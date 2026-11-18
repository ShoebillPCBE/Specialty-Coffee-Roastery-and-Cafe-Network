package ro.upt.cafe.agent.model;

import java.time.Instant;

/** One MCP tool call made during an agent run (by the LLM or by the rule-based planner). */
public record ToolInvocation(Instant at, String tool, String arguments, String result, boolean write, boolean ok) {}
