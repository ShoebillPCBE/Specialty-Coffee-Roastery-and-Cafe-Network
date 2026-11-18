package ro.upt.cafe.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.tool.ToolCallbackProvider;

@SpringBootTest
class McpServerApplicationTests {

    @Autowired
    @Qualifier("cafeNetworkToolCallbacks")
    ToolCallbackProvider tools;

    @Test
    void exposesTheFourCafeTools() {
        assertThat(Arrays.stream(tools.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .containsExactlyInAnyOrder("get_at_risk_batches", "get_drink_sales_velocity",
                        "propose_flash_menu_promo", "propose_inter_cafe_transfer");
    }

    @Test
    void toolSchemaUsesSnakeCaseParameters() {
        String schema = Arrays.stream(tools.getToolCallbacks())
                .filter(t -> t.getToolDefinition().name().equals("propose_inter_cafe_transfer"))
                .findFirst().orElseThrow().getToolDefinition().inputSchema();
        assertThat(schema).contains("from_cafe_id", "to_cafe_id", "batch_id", "quantity_grams", "reason");
    }
}
