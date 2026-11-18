package ro.upt.cafe.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ro.upt.cafe.agent.service.AgentRunService;

/** Starts without an MCP server or LLM key (lazy MCP connection, rule-based fallback). */
@SpringBootTest
class AgentWorkerApplicationTests {

    @Autowired
    AgentRunService agent;

    @Test
    void contextLoadsWithoutLlmKey() {
        assertThat(agent.llmConfigured()).isFalse();
    }
}
