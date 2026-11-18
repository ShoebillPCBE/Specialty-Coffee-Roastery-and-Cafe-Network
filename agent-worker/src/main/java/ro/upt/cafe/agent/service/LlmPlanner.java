package ro.upt.cafe.agent.service;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import ro.upt.cafe.agent.config.AgentProperties;

/**
 * LLM-driven planner: gives the model this morning's snapshot plus the 4 MCP tools and lets it
 * decide which flash promos / inter-cafe transfers to propose (with its reasoning as "reason").
 */
@Component
public class LlmPlanner {

    static final String SYSTEM_PROMPT = """
            You are the autonomous morning operations agent of a specialty coffee roastery & cafe network.
            Every morning at 07:00 you must cut waste of ingredients that are about to go stale or expire
            and prevent stock-outs before the morning rush, by creating PENDING proposals that a human
            barista/manager approves.

            You have 4 MCP tools:
            - get_at_risk_batches / get_drink_sales_velocity (read; the snapshot already contains their output,
              call them again only if you need a different window)
            - propose_flash_menu_promo (write): happy-hour bundle that consumes at-risk stock at a cafe
            - propose_inter_cafe_transfer (write): move surplus stock from a slow cafe to a busy nearby cafe

            Decision rules:
            1. Batch still within its freshness limit (AGING_COFFEE, MATCHA_NEAR_OXIDATION) AND its drink family is
               SLOW_MOVING here AND another cafe within %s km sells that family faster (ACCELERATING or clearly
               higher avg cups/day) -> propose_inter_cafe_transfer of roughly half of the remaining quantity.
            2. Otherwise, for at-risk stock (STALE_COFFEE, OXIDIZING_MATCHA, MILK_EXPIRING, EXPIRING) -> one
               propose_flash_menu_promo per cafe bundling the menu items that use it
               (oat/dairy/soy milk -> matcha lattes, hot chocolate, milk coffees; matcha tin -> matcha drinks;
               decaf beans -> decaf drinks). Discount 10-40%%, duration 2-4 hours (afternoon off-peak).
            3. EXPIRED batches must never be promoted or transferred; only mention them in the summary.
            4. Only use menu_item_ids that appear in the menu list. Never invent cafe ids or batch ids.
            5. At most %d proposals in total, no duplicates. If nothing is at risk, propose nothing.
            6. Every "reason" must be specific: cite batch ids, quantities, age / hours left and sales numbers,
               e.g. "25%% off Iced Matcha & Hot Chocolate 14:00-17:00 to clear 1.2L oat milk (expires in 30h)
               and matcha Tin #4 (opened 26 days ago) before oxidation; matcha sells only 4 cups/day here".

            When done, answer with a short summary (max 8 lines) of what you proposed and why.
            """;

    private final ChatClient chatClient;
    private final AgentProperties props;

    public LlmPlanner(ChatClient agentChatClient, AgentProperties props) {
        this.chatClient = agentChatClient;
        this.props = props;
    }

    public String plan(String snapshotJson, List<ToolCallback> tools) {
        String system = SYSTEM_PROMPT.formatted(props.maxTransferDistanceKm(), props.maxProposalsPerRun());
        String user = """
                Morning snapshot of the cafe network (cafes, distances in km, menu, and for every cafe the output
                of get_at_risk_batches and get_drink_sales_velocity):

                %s

                Analyse it and create the proposals now.""".formatted(snapshotJson);
        return chatClient.prompt()
                // explicit messages: the snapshot JSON contains braces that must not be treated as template vars
                .messages(new SystemMessage(system), new UserMessage(user))
                .toolCallbacks(tools)
                .call()
                .content();
    }
}
