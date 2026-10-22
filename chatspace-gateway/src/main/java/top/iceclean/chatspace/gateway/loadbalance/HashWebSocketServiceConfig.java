package top.iceclean.chatspace.gateway.loadbalance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import reactor.core.publisher.Mono;
import java.util.List;

/** 归属名额跟随 WebSocket 会话关闭，不能跟随 HTTP 升级请求完成。 */
@Configuration
public class HashWebSocketServiceConfig {
    @Bean
    @org.springframework.context.annotation.Primary
    public WebSocketService hashWebSocketService(HashReservationFilter reservations) {
        HandshakeWebSocketService delegate = new HandshakeWebSocketService();
        return (exchange, handler) -> delegate.handleRequest(exchange, new WebSocketHandler() {
            @Override public List<String> getSubProtocols() { return handler.getSubProtocols(); }
            @Override public Mono<Void> handle(WebSocketSession session) {
                return Mono.when(handler.handle(session),
                        session.closeStatus().then(Mono.defer(() -> reservations.release(exchange))));
            }
        });
    }
}
