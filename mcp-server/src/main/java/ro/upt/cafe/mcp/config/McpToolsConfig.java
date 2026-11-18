package ro.upt.cafe.mcp.config;

import java.time.Clock;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ro.upt.cafe.mcp.tools.CafeNetworkTools;

@Configuration
public class McpToolsConfig {

    /** Registers every @Tool method of CafeNetworkTools with the MCP server. */
    @Bean
    public ToolCallbackProvider cafeNetworkToolCallbacks(CafeNetworkTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    public RestClient serverARestClient(RestClient.Builder builder, CafeProperties props) {
        return builder.clone().baseUrl(props.serverA().baseUrl()).build();
    }

    @Bean
    public RestClient serverBRestClient(RestClient.Builder builder, CafeProperties props) {
        return builder.clone().baseUrl(props.serverB().baseUrl()).build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
