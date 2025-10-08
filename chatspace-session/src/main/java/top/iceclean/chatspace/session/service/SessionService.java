package top.iceclean.chatspace.session.service;


import top.iceclean.chatspace.infrastructure.constant.SessionType;
import top.iceclean.chatspace.infrastructure.po.Session;
import top.iceclean.chatspace.infrastructure.vo.SessionVO;
import top.iceclean.feign.SessionClient;

/**
 * @author : Ice'Clean
 * @date : 2022-06-11
 */
public interface SessionService extends SessionClient {

    /**
     * 申请创建一个会话，并返回新会话 ID
     * @param type 会话类型
     * @param targetId 会话的目标 ID
     * @return 新会话实体
     */
    @Override
    Session createSession(SessionType type, int targetId);

    /**
     * 通过会话 ID 获取简单的会话响应对象
     * @param sessionId 会话 ID
     * @return 会话响应对象
     */
    @Override
    SessionVO getSessionVO(int sessionId);

    /**
     * 通过会话 ID 和当前用户 ID 获取详细的会话响应对象
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @return 详细的会话响应对象
     */
    @Override
    SessionVO getSessionVO(int sessionId, int userId);

    /**
     * 通过会话类型和目标 ID 寻找对应会话的 ID
     * @param type 会话类型
     * @param targetId 目标 ID
     * @return 对应会话的 ID
     */
    @Override
    Integer findSessionId(int type, int targetId);

    /**
     * 更新用户在会话中最后一条消息的 ID 为最新的
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     */
    @Override
    void updateLastMsgId(int sessionId, int userId);
}
