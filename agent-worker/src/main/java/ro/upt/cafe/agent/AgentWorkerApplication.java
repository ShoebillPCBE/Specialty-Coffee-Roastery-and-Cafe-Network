package ro.upt.cafe.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Autonomous agent worker (container "agent-worker").
 * Every morning at 07:00 it queries every cafe through the remote MCP server, lets the LLM reason
 * over stale stock vs. stock-out risk and creates PENDING promo / transfer proposals.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class AgentWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentWorkerApplication.class, args);
    }
}
