package top.iceclean.chatspace.session.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.iceclean.chatspace.cache.GroupCache;
import top.iceclean.chatspace.cache.ZoneCache;
import top.iceclean.chatspace.infrastructure.constant.SessionType;
import top.iceclean.chatspace.infrastructure.po.Session;
import top.iceclean.chatspace.infrastructure.vo.FriendVO;
import top.iceclean.chatspace.infrastructure.vo.GroupVO;
import top.iceclean.chatspace.infrastructure.vo.SessionVO;
import top.iceclean.chatspace.session.mapper.SessionMapper;
import top.iceclean.chatspace.session.service.SessionService;
import top.iceclean.feign.FriendClient;
import top.iceclean.feign.GroupClient;
import top.iceclean.feign.MessageClient;

/**
 * @author : Ice'Clean
 * @date : 2022-06-11
 */
@Service
public class SessionServiceImpl implements SessionService {
    private final SessionMapper sessionMapper;
    private final FriendClient friendClient;
    private final GroupClient groupClient;
    private final MessageClient messageClient;

    /** 群组缓存 */
    private final GroupCache groupCache;

    public SessionServiceImpl(SessionMapper sessionMapper,
                              FriendClient friendClient, GroupClient groupClient,
                              MessageClient messageClient, GroupCache groupCache) {
        this.sessionMapper = sessionMapper;
        this.friendClient = friendClient;
        this.groupClient = groupClient;
        this.messageClient = messageClient;
        this.groupCache = groupCache;
    }

    @Override
    @Transactional
    public Session createSession(SessionType type, int targetId) {
        // 创建一个指定类型的会话并返回会话 ID
        Session newSession = new Session(type.value(), targetId);
        sessionMapper.insert(newSession);
        // 如果是好友类型的，targetId 就是会话 ID
        if (type == SessionType.FRIEND) {
            newSession.setTargetId(newSession.getId());
            sessionMapper.updateById(newSession);
        }
        return newSession;
    }

    @Override
    public SessionVO getSessionVO(int sessionId) {
        // 首先获取 session 对象
        Session session = sessionMapper.selectById(sessionId);
        return new SessionVO(sessionId, SessionType.get(session.getType()), session.getTargetId());
    }

    @Override
    public SessionVO getSessionVO(int sessionId, int userId) {
        // 首先获取 session 对象
        Session session = sessionMapper.selectById(sessionId);
        SessionVO sessionVO = new SessionVO(sessionId, SessionType.get(session.getType()), session.getTargetId());
        // 获取会话中的最新消息 ID
        int latestMsgId = messageClient.getLastMsgId(sessionId);
        // 根据类型获取详细信息
        if (session.getType() == SessionType.FRIEND.value()) {
            // 获取好友响应对象
            FriendVO friendVO = friendClient.getFriendVO(sessionId, userId);
            // 获取未读消息数（会话中最新一条消息 ID - 用户上一次最后一条阅读的消息 ID）
            int badgeNum = latestMsgId - friendVO.getLastMsgId();
            // 填充详细信息
            sessionVO.setDetail(friendVO, badgeNum);
        } else {
            // 获取群聊响应对象
            GroupVO groupVO = groupCache.getGroupVO(session.getTargetId());
            // 获取未读消息数
            int badgeNum = latestMsgId - groupVO.getLastMsgId();
            // 填充详细信息
            sessionVO.setDetail(groupVO, badgeNum);
        }
        return sessionVO;
    }

    @Override
    public Integer findSessionId(int type, int targetId) {
        Session session = sessionMapper.selectOne(new LambdaQueryWrapper<Session>()
                .select(Session::getId)
                .eq(Session::getType, type)
                .eq(Session::getTargetId, targetId)
                .last("limit 1"));
        return session == null ? null : session.getId();
    }

    @Override
    public void updateLastMsgId(int sessionId, int userId) {
        // 获取会话，根据类型获取具体映射和最新的消息 ID
        Session session = sessionMapper.selectById(sessionId);
        int lastMsgId = messageClient.getLastMsgId(sessionId);
        if (session.getType() == SessionType.FRIEND.value()) {
            friendClient.updateLastMsgId(session.getId(), userId, lastMsgId);
        } else if (session.getType() == SessionType.GROUP.value()) {
            groupClient.updateLastMsgId(session.getTargetId(), userId, lastMsgId);
        }
    }
}
