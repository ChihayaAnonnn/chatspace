package top.iceclean.chatspace.realtime.server;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;
import top.iceclean.chatspace.infrastructure.utils.RedissonUtils;
import top.iceclean.chatspace.realtime.handler.AuthHandler;
import top.iceclean.chatspace.realtime.handler.ChatHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import top.iceclean.chatspace.realtime.handler.ExceptionHandler;
import top.iceclean.chatspace.realtime.processor.message.ChatProcessor;
import top.iceclean.chatspace.realtime.processor.message.UserProcessor;
import top.iceclean.chatspace.realtime.processor.site.SiteProcessor;
import top.iceclean.chatspace.realtime.processor.site.ZoneProcessor;
import top.iceclean.chatspace.realtime.service.SiteService;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

/**
 * Websocket 服务端，由 Netty 构建
 * @author : Ice'Clean
 * @date : 2022-09-29
 */
@Slf4j
@Component
public class WebSocketServer {
    /** Netty 服务配置 */
    private final ServerProperties serverProperties;

    private final SiteService siteService;
    private final SiteProcessor siteProcessor;
    private final RedissonUtils redissonUtils;

    /** 消息处理器 */
    private final ChatProcessor chatProcessor;
    private final UserProcessor userProcessor;
    private final ZoneProcessor zoneProcessor;

    public WebSocketServer(ServerProperties serverProperties, SiteService siteService, SiteProcessor siteProcessor, RedissonUtils redissonUtils,
                           ChatProcessor chatProcessor, UserProcessor userProcessor, ZoneProcessor zoneProcessor) {
        this.serverProperties = serverProperties;
        this.siteService = siteService;
        this.siteProcessor = siteProcessor;
        this.redissonUtils = redissonUtils;
        this.chatProcessor = chatProcessor;
        this.userProcessor = userProcessor;
        this.zoneProcessor = zoneProcessor;
        log.info("超时控制：{} {} {}", serverProperties.timeout.getRead(), serverProperties.timeout.getWrite(), serverProperties.timeout.getIdle());
        log.info("服务注册：{}, {} {}", serverProperties.register.getNacosIp(), serverProperties.register.getIp(), serverProperties.register.getPort());
        log.info("SSL 认证：{} {}", serverProperties.ssl.getFile(), serverProperties.ssl.getPassword());

    }

    public void start() throws InterruptedException {
        // 将服务注册到 netty
        register();

        // 设置线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup(64);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        // 设置管道处理器
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) throws Exception {
                // SSL 证书引擎获取
                SSLEngine engine = createSslContext(serverProperties.ssl.getPassword()).createSSLEngine();
                engine.setUseClientMode(false);
                socketChannel.pipeline()
                        // FIXME SSL 处理器（内部暂时使用不加密方式调用）
                        // .addLast(new SslHandler(engine))
                        // Netty 自带 Http 编解码器
                        .addLast(new HttpServerCodec())
                        // 用于处理大数据流的块传输处理器
                        .addLast(new ChunkedWriteHandler())
                        // Http 数据聚合器
                        .addLast(new HttpObjectAggregator(64 * 1024))
                        // 增加一个权限校验处理器
                        .addLast("AuthIn", new AuthHandler.Inbound())
                        .addLast("AuthOut", new AuthHandler.Outbound())
                        // WebSocket 数据压缩
                        .addLast(new WebSocketServerCompressionHandler())
                        // WebSocket 处理器
                        .addLast(new WebSocketServerProtocolHandler("/space/ws/chat", "token", true, 10 * 1024))
                        // 心跳机制
                        .addLast(new IdleStateHandler(serverProperties.timeout.getRead(), serverProperties.timeout.getWrite(), serverProperties.timeout.getIdle()))
                        // 聊天业务处理器
                        .addLast(new ChatHandler(siteService, siteProcessor, redissonUtils, chatProcessor, userProcessor, zoneProcessor))
                        // 异常处理器
                        .addLast(new ExceptionHandler());
            }
        });

        ChannelFuture future = bootstrap.bind(serverProperties.register.getPort()).sync();
        future.channel().closeFuture().sync();
    }

    public void register() {
        System.out.println("开始注册到 Netty");
        try {
            // 获取 Nacos 服务
            NamingService namingService = NamingFactory.createNamingService(serverProperties.register.getNacosIp() + ":8848");
            // 将服务注册到注册中心
            Instance instance = new Instance();
            instance.setIp(serverProperties.register.getIp());
            instance.setPort(serverProperties.register.getPort());
            instance.addMetadata("max-user-num", "" + serverProperties.register.getMaxUserNum());
            instance.addMetadata("access-space", serverProperties.register.getAccessSpace().toString());
            instance.addMetadata("start-time", DateUtils.getTime());
            namingService.registerInstance("chatspace-netty", instance);
            log.info("Netty 服务注册成功：{}", instance);
        } catch (NacosException e) {
            log.error("Netty 服务注册失败", e);
        }
    }

    public SSLContext createSslContext(String password) throws KeyStoreException, IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException, CertificateException {
        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream ksInputStream = new ClassPathResource(serverProperties.ssl.getFile()).getInputStream();
        ks.load(ksInputStream, password.toCharArray());
        // KeyManagerFactory充当基于密钥内容源的密钥管理器的工厂
        // getDefaultAlgorithm:获取默认的 KeyManagerFactory 算法名称
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        // SSLContext的实例表示安全套接字协议的实现，它充当用于安全套接字工厂或 SSLEngine 的工厂
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }
}
