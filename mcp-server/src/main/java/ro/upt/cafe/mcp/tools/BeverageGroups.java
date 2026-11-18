package ro.upt.cafe.mcp.tools;

import java.util.List;
import java.util.Locale;

/** Normalises free-form beverage names coming from Server B into the 5 menu families. */
public final class BeverageGroups {

    public static final List<String> ALL = List.of("ESPRESSO", "DECAF", "MATCHA", "TEA", "HOT_CHOCOLATE");

    private BeverageGroups() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String g = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (g.contains("DECAF")) return "DECAF";
        if (g.contains("MATCHA")) return "MATCHA";
        if (g.contains("CHOC") || g.contains("COCOA")) return "HOT_CHOCOLATE";
        if (g.contains("TEA")) return "TEA";
        if (g.contains("ESPRESSO") || g.contains("COFFEE") || g.contains("LATTE") || g.contains("CAPPUCCINO")
                || g.contains("AMERICANO") || g.contains("FLAT_WHITE") || g.contains("FILTER")) return "ESPRESSO";
        return g;
    }
}
