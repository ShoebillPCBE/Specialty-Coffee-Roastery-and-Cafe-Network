package ro.upt.cafe.mcp.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ro.upt.cafe.mcp.config.CafeProperties;
import ro.upt.cafe.mcp.model.BatchView;
import ro.upt.cafe.mcp.model.SalesRecord;

/** Read-side REST client for Server B (query-service, Member 2). */
@Component
public class ServerBClient {

    private final RestClient rest;
    private final CafeProperties.ServerB cfg;

    public ServerBClient(@Qualifier("serverBRestClient") RestClient rest, CafeProperties props) {
        this.rest = rest;
        this.cfg = props.serverB();
    }

    public List<BatchView> batches(String cafeId) {
        List<BatchView> body = rest.get()
                .uri(cfg.batchesPath(), cafeId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<BatchView>>() {});
        return body == null ? List.of() : body;
    }

    public List<SalesRecord> dailySales(String cafeId, int days) {
        List<SalesRecord> body = rest.get()
                .uri(cfg.salesPath(), cafeId, days)
                .retrieve()
                .body(new ParameterizedTypeReference<List<SalesRecord>>() {});
        return body == null ? List.of() : body;
    }
}
