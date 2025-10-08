package top.iceclean.chatspace.realtime.processor.message;

import com.alibaba.fastjson.JSON;
import top.iceclean.chatspace.cache.SessionCache;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.SessionType;
import top.iceclean.chatspace.infrastructure.dto.ChatRequestDTO;
import top.iceclean.chatspace.infrastructure.vo.*;
import top.iceclean.chatspace.realtime.common.DataGenerator;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.infrastructure.constant.WsType;
import top.iceclean.chatspace.infrastructure.dto.MessageDTO;
import top.iceclean.chatspace.infrastructure.po.SessionRequest;
import top.iceclean.chatspace.realtime.mq.MessageProducer;

import java.util.Set;

/**
 * 消息处理器，一个普通的类
 * @author : Ice'Clean
 * @date : 2022-09-29
 */
@Component
public class ChatProcessor {
    /** 用户缓存服务 */
    private final UserCache userCache;
    /** 会话缓存服务 */
    private final SessionCache sessionCache;
    /** 消息队列生产者 */
    private final MessageProducer messageProducer;

    @Autowired
    public ChatProcessor(UserCache uc, SessionCache sc, MessageProducer messageProducer) {
        this.userCache = uc;
        this.sessionCache = sc;
        this.messageProducer = messageProducer;
    }

    /**
     * 处理聊天消息
     * @param content 聊天消息 JSON 串
     */
    public void sendMessage(String content) {
        // 转化为消息类型
        MessageDTO messageDTO = JSON.parseObject(content, MessageDTO.class);
        // 替换换行符
        messageDTO.setContent(messageDTO.getContent().replace("\\n", " "));
        // 发送消息到指定的会话
        BaseProcessor.castMessage(WsType.SEND_MESSAGE, new DataGenerator.ChatMessage(messageDTO));
        // 使用消息队列，持久化消息
        messageProducer.newMessage(messageDTO);
    }

    /**
     * 处理聊天建立请求消息
     * @param reqSyn 聊天建立请求消息
     */
    public void chatRequestSyn(String reqSyn) {
        ChatRequestDTO.RequestSyn reqSynDTO = JSON.parseObject(reqSyn, ChatRequestDTO.RequestSyn.class);
        // 如果是建立连接的话，则构造聊天请求消息
        ChatRequestVO.RequestSyn reqSynVO = new ChatRequestVO.RequestSyn();
        reqSynVO.setType(reqSynDTO.getType());
        reqSynVO.setSite(SessionRepository.getSite());
        // 向目标用户发送私聊请求
        BaseProcessor.castMessage(WsType.CHAT_REQUEST_SYN, reqSynDTO.getTargetId(), reqSynVO);
    }

    /**
     * 处理聊天建立请求的确认消息
     * @param synAck 聊天建立请求的确认消息
     */
    public void chatRequestSynAck(String synAck) {
        // 提取出聊天消息响应消息（目标用户 + 是否同意）
        ChatRequestDTO.SynAck ackDTO = JSON.parseObject(synAck, ChatRequestDTO.SynAck.class);
        UserVO userVO = SessionRepository.getUser();
        // 构造聊天响应消息
        ChatRequestVO.SynAck synAckVO = new ChatRequestVO.SynAck();
        synAckVO.setUser(userVO);
        synAckVO.setAccess(ackDTO.getAccess());
        // 如果当前用户同意的话
        if (Boolean.TRUE.equals(ackDTO.getAccess())) {
            // 因为对方将要加入新的会话，要先从原来的会话中退出
            exitNowSession(ackDTO.getTargetId());
            // 根据类型为双方获取临时会话或者好友会话
            if (ackDTO.getType() == SessionType.TEMP) {
                // 先获取当前用户可能在的临时会话
                Integer userTempSessionId = sessionCache.getUserTempSession(userVO.getUserId());
                // 是临时会话的话，且当前用户处于一个临时会话中，就直接使用这个临时会话（形成小团体）
                synAckVO.setSessionId(userTempSessionId != null ? userTempSessionId :
                        // 否则分配一个新的临时会话
                        sessionCache.newTempSession(userVO.getUserId(), ackDTO.getTargetId()));
            } else {
                // 是好友会话的话，当前用户也需要从原来的会话退出来
                exitNowSession(userVO.getUserId());
                // 最后获取好友会话
                synAckVO.setSessionId(userCache.getFriendSession(userVO.getUserId(), ackDTO.getTargetId()));
            }
            // 同意的话，还需要给自己发一次消息以获取会话 ID
            BaseProcessor.castMessage(WsType.CHAT_REQUEST_SYN_ACK, userVO.getUserId(), synAckVO);
        }
        // 向目标用户发送聊天响应
        BaseProcessor.castMessage(WsType.CHAT_REQUEST_SYN_ACK, ackDTO.getTargetId(), synAckVO);
    }

    /**
     * 处理聊天断开请求消息
     * @param reqFin 聊天断开请求消息
     */
    public void chatRequestFin(String reqFin) {
        ChatRequestDTO.RequestFin reqFinDTO = JSON.parseObject(reqFin, ChatRequestDTO.RequestFin.class);
        // 获取当前用户 ID
        int userId = SessionRepository.getUser().getUserId();
        // 根据是临时会话还是好友会话，执行不同的会话退出策略
        if (reqFinDTO.getTargetId() < 0) {
            exitTempSession(userId, reqFinDTO.getTargetId());
        } else {
            exitFriendSession(userId, reqFinDTO.getTargetId());
        }
    }

    /**
     * 将用户从指定的临时会话中移出
     * @param userId 指定的用户
     * @param sessionId 指定会话 ID
     */
    private void exitTempSession(int userId, int sessionId) {
        // 获取当前用户
        UserVO user = SessionRepository.get(userId).getUser();
        // 构造聊天连接断开确认消息
        ChatRequestVO.FinAck finAckVO = new ChatRequestVO.FinAck(user, sessionId);
        // 先移除用户，再向会话中剩下的用户发送该用户的离开消息（兼判断用户是否在群聊中，不在的话返回空集，不予发送消息）
        Set<Integer> userIds = sessionCache.removeUserFromTempSession(user.getUserId(), sessionId);
        if (!userIds.isEmpty()) {
            // 向会话中所有用户发送
            userIds.forEach(uId -> BaseProcessor.castMessage(WsType.CHAT_REQUEST_FIN_ACK, uId, finAckVO));
            // 同时向自己发送
            BaseProcessor.castMessage(WsType.CHAT_REQUEST_FIN_ACK, user.getUserId(), finAckVO);
        }
   }

    /**
     * 将用户从可能所在的好友会话中移出（同时移除好友会话）
     * @param userId 指定的用户
     * @param friendId 好友会话 ID
     */
    private void exitFriendSession(int userId, int friendId) {
        // 获取当前用户
        UserVO user = SessionRepository.get(userId).getUser();
        // 构造聊天连接断开确认消息
        ChatRequestVO.FinAck finAckVO = new ChatRequestVO.FinAck(user, friendId);
        // 先获取好友会话里的用户
        Set<Integer> friendUser = userCache.getFriendSessionUsers(friendId);
        if (friendUser.remove(user.getUserId())) {
            // 移除用户成功则通知会话中的另一个用户，同时关闭会话
            friendUser.forEach(uId -> BaseProcessor.castMessage(WsType.CHAT_REQUEST_FIN_ACK, uId, finAckVO));
            // 同时向自己发送
            BaseProcessor.castMessage(WsType.CHAT_REQUEST_FIN_ACK, user.getUserId(), finAckVO);
            userCache.removeFriendSession(friendId);
        }
    }

    /** 将指定用户从其当前所在的会话中移出（如果存在的话） */
    public void exitNowSession(int userId) {
        // 是否在临时会话中，在则移出
        Integer sessionId = sessionCache.getUserTempSession(userId);
        if (sessionId != null) {
            exitTempSession(userId, sessionId);
        }
        // 是否在好友会话中，在也移出
        Integer friendId = userCache.getUserFriendSession(userId);
        if (friendId != null) {
            exitFriendSession(userId, friendId);
        }
    }

    public void requestMsg(SessionRequest request) {
        // 发送实时的请求消息
        BaseProcessor.castMessage(WsType.SESSION_REQUEST, new DataGenerator.RequestMessage(request));
    }

    public void tokenExpiredMsg(int userId) {
        // 发送实时的 token 失效通知，并主动断开连接
        BaseProcessor.castMessage(WsType.TOKEN_EXPIRED, new DataGenerator.TokenExpire(userId));
        SessionRepository.get(userId).getChannel().close();
    }
}
