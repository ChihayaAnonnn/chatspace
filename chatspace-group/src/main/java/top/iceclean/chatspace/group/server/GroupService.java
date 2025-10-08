package top.iceclean.chatspace.group.server;


import org.springframework.web.bind.annotation.GetMapping;
import top.iceclean.chatspace.infrastructure.po.Group;
import top.iceclean.chatspace.infrastructure.po.UserGroup;
import top.iceclean.chatspace.infrastructure.pojo.Response;

import java.util.List;

/**
 * @author : Ice'Clean
 * @date : 2022-05-29
 */
public interface GroupService {

    /**
     * 新建群聊
     * @param groupName 群名
     * @return 群聊响应对象
     */
    Response createGroup(String groupName);

    /**
     * 将用户添加到群聊中
     * @param userId 用户 ID
     * @param groupId 群聊 ID
     * @return 是否添加成功
     */
    boolean joinGroup(int userId, int groupId);

    /**
     * 将用户移出群聊
     * @param userId 用户 ID
     * @param groupId 群聊 ID
     * @return 是否移出成功
     */
    boolean exitGroup(int userId, int groupId);

    /**
     * 通过群聊 ID 获取群聊实体
     * @param groupId 群聊 ID
     * @return 群聊实体
     */
    Group getGroupById(int groupId);

    /**
     * 获取用户某条群聊记录
     * @param userId 用户 ID
     * @param groupId 群聊 ID
     * @return 用户群聊记录
     */
    UserGroup getUserGroup(int userId, int groupId);

    /**
     * 获取用户加入的所有群组的 ID
     * @param userId 用户 ID
     * @return 群组 ID 列表
     */
    List<Integer> getGroupIdList(int userId);

    /**
     * 获取用户所有群聊
     * @param userId 用户 ID
     * @return 群聊列表
     */
    Response getGroupList(int userId);

    /**
     * 获取用户创建的所有群聊
     * @param userId 用户 ID
     * @return 由用户创建的所有群组
     */
    Response getUserCreateGroups(int userId);

    /**
     * 获取群聊中的所有用户
     * @param groupId 群聊 ID
     * @return 所有用户集合
     */
    Response getUserList(int groupId);

    /**
     * 获取群聊中所有用户 ID
     * @param groupId 群聊 ID
     * @return 所有用户 ID 集合
     */
    List<Integer> getUserIdList(int groupId);

    /**
     * 获取所有用户管理的群聊
     * @param userId 用户 ID
     * @return 群聊列表
     */
    List<Group> getUserManageGroups(int userId);

    /**
     * 判断用户是否为指定群组的创建者
     * @param userId 用户 ID
     * @param groupId 群组 ID
     * @return 是则返回 true
     */
    boolean isUserCreator(int userId, int groupId);

    /**
     * 更新用户在群聊会话中最后一条消息的 ID 为最新的
     * @param groupId 群聊 ID
     * @param userId 用户 ID
     * @param latestMsgId 最新消息 ID
     */
    void updateLastMsgId(int groupId, int userId, int latestMsgId);
}
