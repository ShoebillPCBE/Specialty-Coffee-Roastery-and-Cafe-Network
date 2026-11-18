package ro.upt.cafe.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ro.upt.cafe.agent.config.AgentProperties;
import ro.upt.cafe.agent.config.AgentProperties.Cafe;
import ro.upt.cafe.agent.service.MenuCatalog.MenuItemRef;
import ro.upt.cafe.agent.service.NetworkSnapshot;
import ro.upt.cafe.agent.service.NetworkSnapshot.CafeSnapshot;
import ro.upt.cafe.agent.service.RuleBasedPlanner;

class RuleBasedPlannerTest {

    static final Cafe C1 = new Cafe("CAFE-01", "Roastery Victoriei", 45.7537, 21.2257);
    static final Cafe C2 = new Cafe("CAFE-02", "Campus Cafe", 45.7472, 21.2390);

    final AgentProperties props = new AgentProperties("Europe/Bucharest", false, 48, 7, 6, 3.0, "", List.of(C1, C2));
    final RuleBasedPlanner planner = new RuleBasedPlanner(props, new ObjectMapper());

    record Call(String tool, Map<String, Object> args) {}

    @Test
    @SuppressWarnings("unchecked")
    void transfersAgingDecafToBusyCafeAndPromotesExpiringMilkAndMatcha() {
        String atRiskC1 = """
                {"cafeId":"CAFE-01","batches":[
                  {"batchId":"B-DEC-7","name":"Decaf Colombia","category":"DECAF_BEAN","remainingQuantity":1000,"unit":"g",
                   "riskType":"AGING_COFFEE","severity":"MEDIUM","note":"roasted 20 days ago"},
                  {"batchId":"B-OAT-2","name":"Oat milk","category":"OAT_MILK","remainingQuantity":1200,"unit":"ml",
                   "riskType":"MILK_EXPIRING","severity":"HIGH","note":"expires in 30h"},
                  {"batchId":"B-MAT-4","name":"Matcha Tin #4","category":"MATCHA_TIN","remainingQuantity":80,"unit":"g",
                   "riskType":"OXIDIZING_MATCHA","severity":"HIGH","note":"opened 27 days ago"},
                  {"batchId":"B-OLD","name":"Old milk","category":"DAIRY_MILK","remainingQuantity":500,"unit":"ml",
                   "riskType":"EXPIRED","severity":"CRITICAL","note":"expired"}]}""";
        String velocityC1 = """
                {"drinks":[{"beverageGroup":"DECAF","avgCupsPerDay":2.0,"status":"SLOW_MOVING"},
                           {"beverageGroup":"MATCHA","avgCupsPerDay":4.0,"status":"SLOW_MOVING"}]}""";
        String velocityC2 = """
                {"drinks":[{"beverageGroup":"DECAF","avgCupsPerDay":18.0,"status":"ACCELERATING"},
                           {"beverageGroup":"MATCHA","avgCupsPerDay":25.0,"status":"STEADY"}]}""";

        NetworkSnapshot snapshot = new NetworkSnapshot(
                List.of(new CafeSnapshot(C1, atRiskC1, velocityC1), new CafeSnapshot(C2, "{\"batches\":[]}", velocityC2)),
                Map.of("CAFE-01", Map.of("CAFE-02", 1.25), "CAFE-02", Map.of("CAFE-01", 1.25)),
                List.of(new MenuItemRef("M-ICED-MATCHA", "Iced Matcha", "MATCHA", BigDecimal.TEN),
                        new MenuItemRef("M-HOT-CHOC", "Hot Chocolate", "HOT_CHOCOLATE", BigDecimal.TEN),
                        new MenuItemRef("M-ESPRESSO", "Espresso", "ESPRESSO", BigDecimal.ONE)));

        List<Call> calls = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String summary = planner.plan(snapshot, (tool, args) -> {
            calls.add(new Call(tool, args));
            return "{\"ok\":true}";
        }, warnings);

        assertThat(calls).extracting(Call::tool)
                .containsExactly("propose_inter_cafe_transfer", "propose_flash_menu_promo");

        Map<String, Object> transfer = calls.get(0).args();
        assertThat(transfer).containsEntry("from_cafe_id", "CAFE-01").containsEntry("to_cafe_id", "CAFE-02")
                .containsEntry("batch_id", "B-DEC-7").containsEntry("quantity_grams", 500.0);

        Map<String, Object> promo = calls.get(1).args();
        assertThat(promo).containsEntry("cafe_id", "CAFE-01").containsEntry("discount_pct", 25);
        assertThat((List<Object>) promo.get("menu_item_ids")).containsExactly("M-ICED-MATCHA", "M-HOT-CHOC");
        assertThat((String) promo.get("reason")).contains("B-OAT-2", "B-MAT-4").doesNotContain("B-OLD");

        assertThat(warnings).anyMatch(w -> w.contains("B-OLD") && w.contains("expired"));
        assertThat(summary).contains("PENDING");
    }

    @Test
    void proposesNothingWhenNothingIsAtRisk() {
        NetworkSnapshot snapshot = new NetworkSnapshot(
                List.of(new CafeSnapshot(C1, "{\"batches\":[]}", "{\"drinks\":[]}")), Map.of(), List.of());
        List<Call> calls = new ArrayList<>();

        String summary = planner.plan(snapshot, (tool, args) -> {
            calls.add(new Call(tool, args));
            return "{\"ok\":true}";
        }, new ArrayList<>());

        assertThat(calls).isEmpty();
        assertThat(summary).contains("nothing at risk");
    }
}
