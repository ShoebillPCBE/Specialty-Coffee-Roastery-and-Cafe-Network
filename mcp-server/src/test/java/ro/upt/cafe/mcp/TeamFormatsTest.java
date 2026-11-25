package ro.upt.cafe.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.upt.cafe.mcp.model.BatchView;
import ro.upt.cafe.mcp.model.SalesRecord;

/** The MCP server must read the JSON shapes Member 1 and Member 2 actually produce. */
class TeamFormatsTest {

    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void readsMember2BatchRegisteredEvent() throws Exception {
        String json = """
                [{"eventId":"e1","eventType":"BatchRegistered","timestamp":"2026-11-20T08:00:00Z",
                  "batchId":"B-MAT-04","roasteryId":"R1","ingredientCategory":"MATCHA_TIN",
                  "ingredientName":"Ceremonial matcha","origin":"Uji","process":null,
                  "roastDate":null,"quantity":80.0,"unit":"g","cafeId":"CAFE-01",
                  "openedAt":"2026-10-25T09:30:00","expiryDate":"2027-03-01"},
                 {"batchId":"B-DEC-07","ingredientCategory":"DECAF_BEAN","ingredientName":"Colombia decaf",
                  "roastDate":"2026-11-01","openedAt":"2026-11-10","quantity":1000,"unit":"g","cafeId":"CAFE-01"}]
                """;
        List<BatchView> batches = List.of(mapper.readValue(json, BatchView[].class));

        BatchView matcha = batches.get(0);
        assertThat(matcha.category()).isEqualTo("MATCHA_TIN");
        assertThat(matcha.name()).isEqualTo("Ceremonial matcha");
        assertThat(matcha.remainingQuantity()).isEqualTo(80.0);
        assertThat(matcha.roastOrOpenDate()).isEqualTo(LocalDate.of(2026, 10, 25)); // matcha: openedAt
        assertThat(matcha.expiryDate()).isEqualTo(LocalDate.of(2027, 3, 1));

        assertThat(batches.get(1).roastOrOpenDate()).isEqualTo(LocalDate.of(2026, 11, 1)); // coffee: roastDate
    }

    @Test
    void readsMember1IngredientBatch() throws Exception {
        BatchView b = mapper.readValue("""
                {"batchId":"B-OAT","cafeId":"CAFE-02","name":"Oat milk","category":"OAT_MILK",
                 "remainingQuantity":1200,"roastOrOpenDate":"2026-11-22","expiryDate":"2026-11-26"}""", BatchView.class);
        assertThat(b.category()).isEqualTo("OAT_MILK");
        assertThat(b.remainingQuantity()).isEqualTo(1200.0);
        assertThat(b.roastOrOpenDate()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    void readsSalesRowsAndRawOrderPlacedEvents() throws Exception {
        SalesRecord daily = mapper.readValue("{\"date\":\"2026-11-24\",\"beverageGroup\":\"MATCHA\",\"cups\":12}", SalesRecord.class);
        SalesRecord order = mapper.readValue("""
                {"orderId":"ORD-1","cafeId":"CAFE-01","menuItemId":"M-1","menuItemName":"Iced Matcha",
                 "beverageGroup":"MATCHA","cupsSold":2,"timestamp":"2026-11-24T14:05:00Z"}""", SalesRecord.class);
        assertThat(daily.cups()).isEqualTo(12);
        assertThat(order.cups()).isEqualTo(2);
        assertThat(order.date()).isEqualTo(LocalDate.of(2026, 11, 24));
    }
}
