package ro.upt.cafe.agent.model;

import java.time.Instant;
import java.util.List;

/** Report of one agent cycle; returned by POST /api/v1/agent/trigger-now and GET /api/v1/agent/runs. */
public record AgentRun(String runId,
                       Trigger trigger,
                       Mode mode,
                       Status status,
                       Instant startedAt,
                       Instant finishedAt,
                       List<String> cafesAnalysed,
                       String summary,
                       int proposalsCreated,
                       List<ToolInvocation> toolCalls,
                       List<String> warnings) {

    public enum Trigger { SCHEDULED, MANUAL }

    /** LLM = model reasoned and called the tools; RULE_BASED = deterministic planner (no key / LLM failure). */
    public enum Mode { LLM, RULE_BASED }

    public enum Status { SUCCESS, PARTIAL, FAILED }
}
