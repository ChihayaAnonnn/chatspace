package top.iceclean.chatspace.gateway.loadbalance;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import top.iceclean.chatspace.gateway.loadbalance.hash.ConsistentHashing;
import top.iceclean.chatspace.gateway.utils.JwtUtils;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

class HashReservationFilterTest {
    @Test void httpUpgradeCompletionDoesNotReleaseLiveWebSocketReservation() {
        ConsistentHashing hash = mock(ConsistentHashing.class);
        HashReservationFilter filter = new HashReservationFilter(hash, new ConsistentHashingProperties());
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/space/ws/chat")
                .header("Sec-WebSocket-Protocol", JwtUtils.createJWT("10001") + ",1"));
        AtomicReference<ServerWebExchange> routed = new AtomicReference<>();
        filter.filter(exchange, e -> {
            e.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("ws://10.0.0.1:19019/space/ws/chat"));
            routed.set(e);
            return Mono.empty();
        }).block();
        verifyNoInteractions(hash);
        filter.release(routed.get()).block();
        verify(hash).userRemove(eq("10.0.0.1:19019"), eq("10001"), anyString());
    }
}
