package top.iceclean.chatspace.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import top.iceclean.chatspace.gateway.utils.JwtUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class TokenFilterTest {
    @Test void browserWebSocketUsesSubprotocolAndForwardsAuthenticatedUser() {
        String token = JwtUtils.createJWT("10001");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/space/ws/chat")
                .header("Sec-WebSocket-Protocol", token + ", 1"));
        AtomicBoolean called = new AtomicBoolean();
        new TokenFilter().filter(exchange, routed -> {
            called.set(true);
            assertEquals("10001", routed.getRequest().getHeaders().getFirst("auth-user"));
            assertEquals(token + ",1", routed.getRequest().getHeaders().getFirst("Sec-WebSocket-Protocol"));
            return Mono.empty();
        }).block();
        assertTrue(called.get());
    }
    @Test void ordinaryHttpCannotUseWebSocketProtocolAsAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/space/friend/list")
                .header("Sec-WebSocket-Protocol", JwtUtils.createJWT("10001") + ",1"));
        new TokenFilter().filter(exchange, routed -> { fail("需要 token 请求头"); return Mono.empty(); }).block();
        assertEquals(401, exchange.getResponse().getRawStatusCode());
    }
}
