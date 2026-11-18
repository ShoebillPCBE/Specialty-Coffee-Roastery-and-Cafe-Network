package ro.upt.cafe.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Remote MCP server (container "mcp-server").
 * Exposes 4 tools over Streamable HTTP / SSE on the Docker network; each tool wraps
 * REST calls to Server A (command-service) or Server B (query-service).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
