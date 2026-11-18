package ro.upt.cafe.mcp.client;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ro.upt.cafe.mcp.config.CafeProperties;
import ro.upt.cafe.mcp.model.FlashPromoRequest;
import ro.upt.cafe.mcp.model.TransferRequest;

/** Write-side REST client for Server A (command-service, Member 1). */
@Component
public class ServerAClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final CafeProperties.ServerA cfg;

    public ServerAClient(@Qualifier("serverARestClient") RestClient rest, CafeProperties props) {
        this.rest = rest;
        this.cfg = props.serverA();
    }

    public Map<String, Object> proposeFlashPromo(FlashPromoRequest request) {
        return post(cfg.flashPromoPath(), request);
    }

    public Map<String, Object> proposeTransfer(TransferRequest request) {
        return post(cfg.transferPath(), request);
    }

    private Map<String, Object> post(String path, Object body) {
        Map<String, Object> response = rest.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP);
        return response == null ? Map.of() : response;
    }
}
