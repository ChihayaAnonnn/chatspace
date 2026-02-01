package top.iceclean.chatspace.realtime.handler;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.chatspace.infrastructure.utils.RedissonUtils;
import top.iceclean.chatspace.infrastructure.vo.UserVO;
import top.iceclean.chatspace.infrastructure.common.Monitor;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.processor.message.BaseProcessor;
import top.iceclean.chatspace.realtime.processor.site.SiteProcessor;
import top.iceclean.chatspace.realtime.processor.message.ChatProcessor;
import top.iceclean.chatspace.realtime.processor.message.UserProcessor;
import top.iceclean.chatspace.realtime.processor.site.ZoneProcessor;
import top.iceclean.chatspace.realtime.service.SiteService;
import top.iceclean.chatspace.realtime.share.ServerSession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import top.iceclean.chatspace.infrastructure.dto.WsMessageDTO;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.po.User;

/**
 * 聊天处理器（入栈）
 * @author : Ice'Clean
 * @date : 2022-09-29
 */
@Slf4j
public class ChatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final SiteService siteService;
    private final SiteProcessor siteProcessor;
    private final RedissonUtils redissonUtils;

    /** 消息处理器 */
    private final ChatProcessor chatProcessor;
    private final UserProcessor userProcessor;
    private final ZoneProcessor zoneProcessor;

    private final UserCache userCache;


    public ChatHandler(SiteService siteService, SiteProcessor siteProcessor, RedissonUtils redissonUtils,
                       ChatProcessor chatProcessor, UserProcessor userProcessor, ZoneProcessor zoneProcessor, UserCache userCache) {
        this.siteService = siteService;
        this.siteProcessor = siteProcessor;
        this.redissonUtils = redissonUtils;
        this.chatProcessor = chatProcessor;
        this.userProcessor = userProcessor;
        this.zoneProcessor = zoneProcessor;
        this.userCache = userCache;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        // 更新当前线程的用户 ID
        int userId = HandlerHelper.getUserId(ctx);
        SessionRepository.update(userId);
        // 检查用户是否初始化完毕
        if (!HandlerHelper.isInitialized(ctx)) {
            log.info("用户 {} 正在初始化，忽略消息", userId);
            return;
        }
        // 检查用户 token
        if (!checkToken(ctx)) {
            return;
        }
        Monitor.newUserEvent();
        // 将文本转化为 ws 消息对象
        WsMessageDTO wsMessageDTO = JSON.parseObject(msg.text(), WsMessageDTO.class);
        String wsContent = wsMessageDTO.getWsContent().toString();
        // log.info("收到消息：{}", wsMessageDTO);
        // 对不同消息类型作出不同处理
        switch (wsMessageDTO.getWsTypeEnum()) {
            // 心跳
            case HEART_BEAT: log.info("【用户心跳】{}", wsContent); break;
            // 聊天消息
            case SEND_MESSAGE: chatProcessor.sendMessage(wsContent); break;
            // 位置更新消息
            case SITE_UPDATE: siteProcessor.updateSite(SiteDTO.parse(wsContent)); break;
            // 聊天建立请求消息
            case CHAT_REQUEST_SYN: chatProcessor.chatRequestSyn(wsContent); break;
            // 回应聊天建立请求消息
            case CHAT_REQUEST_SYN_ACK: chatProcessor.chatRequestSynAck(wsContent); break;
            // 聊天断开请求消息
            case CHAT_REQUEST_FIN: chatProcessor.chatRequestFin(wsContent); break;
            // 区域进入确认
            case ZONE_IN_ACK: siteProcessor.updateSite(zoneProcessor.enterZone()); break;
            // 区域退出
            case EXIT_ZONE: siteProcessor.updateSite(zoneProcessor.exitZone(userId)); break;
            // 语音聊天候选人
            case RTC_CANDIDATE:
                if (SessionRepository.getUser().getUserId().equals(7)) {
                    BaseProcessor.castMessage(WsType.RTC_CANDIDATE, 1, wsContent);
                } else {
                    BaseProcessor.castMessage(WsType.RTC_CANDIDATE, 7, wsContent);
                }
                break;
            // 语音聊天请求
            case RTC_OFFER:
                wsContent = wsContent.replace("\\r\\n", "\\n");
                if (SessionRepository.getUser().getUserId().equals(7)) {
                    BaseProcessor.castMessage(WsType.RTC_OFFER, 1, wsContent);
                } else {
                    BaseProcessor.castMessage(WsType.RTC_OFFER, 7, wsContent);
                }
                break;
            // 语音聊天确认
            case RTC_ANSWER:
                wsContent = wsContent.replace("\\r\\n", "\\n");
                if (SessionRepository.getUser().getUserId().equals(7)) {
                    BaseProcessor.castMessage(WsType.RTC_ANSWER, 1, wsContent);
                } else {
                    BaseProcessor.castMessage(WsType.RTC_ANSWER, 7, wsContent);
                }
                break;
            default:
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // 握手成功后，就升级为 WebSocket 了，要移出两个权限校验器
            ctx.channel().pipeline().remove("AuthIn");
            ctx.channel().pipeline().remove("AuthOut");

            // 为新用户进行初始化
            enterSpace(ctx);
        } else if (evt instanceof IdleStateEvent) {
            // 连接读写超时
            IdleStateEvent stateEvent = (IdleStateEvent) evt;
            SessionRepository.update(HandlerHelper.getUserId(ctx));
            if (stateEvent.state() == IdleState.READER_IDLE) {
                log.warn("用户 READ 超时：" + SessionRepository.getUser().getUserName());
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("用户连接断开，[session={}]", SessionRepository.get());
        Monitor.change(Monitor.Type.USER_CONNECTION, -1);
        // 如果是强制下线的话，则不需要清理标识
        if (HandlerHelper.isForcedOffline(ctx)) {
            return;
        }

        // 将用户 ID 设置为管道 ID，然后将会话移除以及做善后工作
        SessionRepository.update(HandlerHelper.getUserId(ctx));
        ServerSession serverSession = SessionRepository.get();
        if (serverSession != null) {
            // 设置为下线状态，防止历史消息被执行
            serverSession.offline();
            // 将用户当前位置保存起来
            siteService.updateUserSite(serverSession.getSite());
            UserVO user = serverSession.getUser();
            log.info("用户取得分布式锁，执行下线操作：{}", serverSession.getSite());
            // 将用户从可能的会话中移出
            chatProcessor.exitNowSession(user);
            // 将当前用户的痕迹从空间中消除
            siteProcessor.exitSpace(serverSession.getSite());
            // 断开与当前用户的会话
            SessionRepository.remove();
            // 发送用户下线消息
            userProcessor.changeOnline(user.getUserId(), false);

        }
        super.channelInactive(ctx);
    }

    private boolean checkToken(ChannelHandlerContext ctx) {
        // 如果当前用户状态已经被清除，则说明已经出发连接关闭事件，不予理睬，同时发送断开连接的消息
        if (SessionRepository.get() == null) {
            log.info("用户连接已关闭，不予操作");
            userProcessor.forcedOffline(ctx.channel(), "用户连接已关闭，请重新登录");
            ctx.close();
            return false;
        }

        // 获取用户 ID、token 和存放在缓存中的 token
        int userId = HandlerHelper.getUserId(ctx);
        String token = HandlerHelper.getToken(ctx);
        String cacheToken = userCache.getToken(userId);

        // 如果 cacheToken 为空，直接发送 token 过期消息，然后清除用户状态
        if (cacheToken == null) {
            chatProcessor.tokenExpiredMsg(userId);
            return false;
        }

        // 如果 cacheToken 和当前的 token 不等，则判定为强制下线
        if (!cacheToken.equals(token)) {
            // 向用户发送 ws 消息，为通道设置强制下线标识，最后关闭连接
            userProcessor.forcedOffline(ctx.channel(), "有新设备登录该账号，当前登录已被强制下线");
            HandlerHelper.setForcedOffline(ctx);
            ctx.close();
            log.info("用户 " + userId + " 身份验证失败，强制下线");
            return false;
        }

        // 全部检验通过则身份认证成功
        return true;
    }

    private void enterSpace(ChannelHandlerContext ctx) {
        // 获取当前用户以及进入的空间 ID
        int spaceId = HandlerHelper.getSpaceId(ctx);
        int userId = HandlerHelper.getUserId(ctx);
        log.info("用户 {} 进入空间 {}", userId, spaceId);

        long start = System.currentTimeMillis();
        // 设置用户上线状态
        User user = userProcessor.changeOnline(userId, true);
        // 先从数据库获取位置信息
        Site site = siteService.getUserSite(userId);
        site.setSpaceId(spaceId);
        // 先更新最新数据
        siteService.updateUserSite(site);

        // 为当前用户新建会话并绑定通道
        SessionRepository.bind(ctx.channel(), user, site);
        HandlerHelper.setInitState(ctx, true);

        // 初始化当前用户在空间中的信息
        siteProcessor.enterSpace();

        // 初始化完毕
        Monitor.change(Monitor.Type.USER_CONNECTION, 1);
        log.info("】】】用户握手成功：{}，耗时：{}", userId, System.currentTimeMillis() - start);
    }
}
