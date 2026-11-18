package ro.upt.cafe.agent.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ro.upt.cafe.agent.config.AgentProperties;

/** Reads the menu from Server A (GET /api/v1/menu) so the agent proposes promos on real menu_item_ids. */
@Component
public class MenuCatalog {

    private final RestClient rest;
    private final AgentProperties props;

    public MenuCatalog(RestClient agentRestClient, AgentProperties props) {
        this.rest = agentRestClient;
        this.props = props;
    }

    public List<MenuItemRef> load() {
        List<MenuItemRef> items = rest.get().uri(props.menuUrl()).retrieve()
                .body(new ParameterizedTypeReference<List<MenuItemRef>>() {});
        return items == null ? List.of() : items;
    }

    /** Maps a menu item to ESPRESSO / DECAF / MATCHA / TEA / HOT_CHOCOLATE. */
    public static String family(MenuItemRef item) {
        String raw = ((item.beverageGroup() == null ? "" : item.beverageGroup()) + " "
                + (item.name() == null ? "" : item.name())).toUpperCase(Locale.ROOT);
        if (raw.contains("DECAF")) return "DECAF";
        if (raw.contains("MATCHA")) return "MATCHA";
        if (raw.contains("CHOC") || raw.contains("COCOA")) return "HOT_CHOCOLATE";
        if (raw.contains("TEA")) return "TEA";
        return "ESPRESSO";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MenuItemRef(String itemId, String name, String beverageGroup, BigDecimal basePriceRon) {}
}
