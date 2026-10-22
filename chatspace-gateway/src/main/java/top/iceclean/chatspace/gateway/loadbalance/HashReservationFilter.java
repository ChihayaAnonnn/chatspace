package top.iceclean.chatspace.gateway.loadbalance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.iceclean.chatspace.gateway.loadbalance.hash.ConsistentHashing;
import top.iceclean.chatspace.gateway.utils.JwtUtils;
import java.net.URI;
import java.util.UUID;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/** 给每次握手独立代次，会话关闭或握手失败时释放自己的路由名额。 */
@Slf4j
@Component
public class HashReservationFilter implements GlobalFilter, Ordered {
    private final ConsistentHashing hash;
    private final ConsistentHashingProperties properties;
    public HashReservationFilter(ConsistentHashing hash, ConsistentHashingProperties properties) {
        this.hash=hash; this.properties=properties;
    }
    @Override public int getOrder() { return 10149; }
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if(!properties.getWebsocketUrls().contains(exchange.getRequest().getPath().value()))return chain.filter(exchange);
        String protocol=exchange.getRequest().getHeaders().getFirst("Sec-WebSocket-Protocol");
        if(protocol==null)return chain.filter(exchange);
        String uid=JwtUtils.parseJWT(protocol.split(",")[0].trim()).getSubject();
        String lease=UUID.randomUUID().toString();
        ServerWebExchange routed=exchange.mutate().request(exchange.getRequest().mutate().header("hash-connection-id",lease).build()).build();
        routed.getAttributes().put("hash.uid", uid);
        routed.getAttributes().put("hash.lease", lease);
        return chain.filter(routed).onErrorResume(error -> release(routed).then(Mono.error(error)));
    }
    Mono<Void> release(ServerWebExchange exchange) {
        String uid = exchange.getAttribute("hash.uid");
        String lease = exchange.getAttribute("hash.lease");
        if (uid == null || lease == null) return Mono.empty();
        return release(exchange, uid, lease);
    }
    private Mono<Void> release(ServerWebExchange exchange,String uid,String lease) {
        return Mono.<Void>fromRunnable(()->{
            URI target=exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
            if(target!=null && ("ws".equals(target.getScheme()) || "wss".equals(target.getScheme()))) {
                hash.userRemove(target.getHost()+":"+target.getPort(),uid,lease);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
