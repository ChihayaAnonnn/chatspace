package top.iceclean.chatspace.group.server.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.sf.jsqlparser.util.validation.metadata.DatabaseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import top.iceclean.chatspace.group.mapper.GroupMapper;
import top.iceclean.chatspace.group.mapper.UserGroupMapper;
import top.iceclean.chatspace.group.server.GroupService;
import top.iceclean.chatspace.cache.GroupCache;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.auth.UserAuth;
import top.iceclean.chatspace.infrastructure.constant.FileConst;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.constant.SessionType;
import top.iceclean.chatspace.infrastructure.po.Group;
import top.iceclean.chatspace.infrastructure.po.UserGroup;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.infrastructure.vo.GroupVO;
import top.iceclean.chatspace.infrastructure.vo.UserVO;
import top.iceclean.feign.MessageClient;
import top.iceclean.feign.SessionClient;
import top.iceclean.feign.UserClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author : Ice'Clean
 * @date : 2022-05-29
 */
@Service
public class GroupServiceImpl implements GroupService {

    @Autowired
    private GroupMapper groupMapper;
    @Autowired
    private UserGroupMapper userGroupMapper;
    @Autowired
    private UserClient userClient;
    @Autowired
    private MessageClient messageClient;
    @Autowired
    private SessionClient sessionClient;

    /** 群组缓存 */
    private final GroupCache groupCache;
    /** 用户缓存 */
    private final UserCache userCache;

    public GroupServiceImpl(GroupCache groupCache, UserCache userCache) {
        this.groupCache = groupCache;
        this.userCache = userCache;
    }

    @Override
    @Transactional
    public Response createGroup(String groupName) {
        // 获取当前用户 ID
        int creatorId = UserAuth.getUserId();
        // 构造群对象并设置默认头像和初始化群人数
        Group group = new Group(creatorId, groupName);
        group.setAvatar(FileConst.DEFAULT_AVATAR);
        group.setNumber(1);

        // 创建群组
        groupMapper.insert(group);
        // 成功则为创建者建立映射
        UserGroup userGroup = new UserGroup(creatorId, group.getId(), 0);
        userGroupMapper.insert(userGroup);
        // 映射成功，封装群聊响应对象并返回
        GroupVO groupVO = new GroupVO(group, 1);
        groupVO.setUserGroup(userGroup);
        // 为群聊创建一个会话，成功则直接返回
        if (sessionClient.createSession(SessionType.GROUP, group.getId()) != null) {
            return new Response(ResponseStatusEnum.OK)
                    .setMsg("创建群组成功")
                    .setData(groupVO);
        }
        // 出错则回滚
        throw new DatabaseException(ResponseStatusEnum.DATABASE_ERROR.msg());
    }

    @Override
    @Transactional
    public boolean joinGroup(int userId, int groupId) {
        // 获取群聊中最新的消息 ID，作为新用户的已阅 ID
        return userGroupMapper.insert(new UserGroup(userId, groupId, messageClient.getLastMsgId(groupId))) == 1;
    }

    @Override
    @Transactional
    public boolean exitGroup(int userId, int groupId) {
        return userGroupMapper.delete(new LambdaQueryWrapper<UserGroup>()
                .eq(UserGroup::getUserId, userId).eq(UserGroup::getGroupId, groupId)) == 1;
    }

    @Override
    public Group getGroupById(int groupId) {
        return groupMapper.selectById(groupId);
    }


    @Override
    public UserGroup getUserGroup(int userId, int groupId) {
        return userGroupMapper.selectOne(new LambdaQueryWrapper<UserGroup>()
                .isNull(UserGroup::getDeleteTime)
                .eq(UserGroup::getUserId, userId)
                .eq(UserGroup::getGroupId, groupId));
    }

    @Override
    public List<Integer> getGroupIdList(int userId) {
        return userGroupMapper.selectList(new LambdaQueryWrapper<UserGroup>()
                .select(UserGroup::getGroupId)
                .isNull(UserGroup::getDeleteTime)
                .eq(UserGroup::getUserId, userId))
                .stream().map(UserGroup::getGroupId).collect(Collectors.toList());
    }

    @Override
    public Response getGroupList(int userId) {
        // 先获取所有群组 ID
        List<Integer> groupIdList = getGroupIdList(userId);
        // 然后转化成响应对象
        List<GroupVO> groupList = groupIdList.stream().map(groupId ->
                groupCache.getUserGroupVO(groupId, userId)).collect(Collectors.toList());
        return new Response(ResponseStatusEnum.OK).addData("groupList", groupList);
    }

    @Override
    public Response getUserCreateGroups(int userId) {
        // 先获取所有用户创建的群组 ID
        List<Integer> groupIdList = groupMapper.selectList(new LambdaQueryWrapper<Group>()
                .select(Group::getId)
                .isNull(Group::getDeleteTime)
                .eq(Group::getCreatorId, userId))
                .stream().map(Group::getId).collect(Collectors.toList());
        // 然后转化成响应对象
        List<GroupVO> groupList = groupIdList.stream().map(groupCache::getGroupVO).collect(Collectors.toList());
        return new Response(ResponseStatusEnum.OK).addData("groupList", groupList);
    }

    @Override
    public Response getUserList(int groupId) {
        // 获取群聊中所有用户的 ID
        List<Integer> userIdList = userGroupMapper.selectList(new LambdaQueryWrapper<UserGroup>()
                .select(UserGroup::getUserId)
                .isNull(UserGroup::getDeleteTime)
                .eq(UserGroup::getGroupId, groupId))
                .stream().map(UserGroup::getUserId).collect(Collectors.toList());
        // 根据 ID 集合获取所有用户并转化为响应对象
        List<UserVO> userList = userClient.getUserList(userIdList)
                .stream().map(userCache::wrapUser).collect(Collectors.toList());
        // 最后响应群聊用户列表
        return new Response(ResponseStatusEnum.OK).addData("groupUserList", userList);
    }

    @Override
    public List<Integer> getUserIdList(int groupId) {
        // 将所有的群聊映射记录查询出来，再过滤出用户 ID 集合
        return userGroupMapper.selectList(new LambdaQueryWrapper<UserGroup>()
                .select(UserGroup::getUserId)
                .isNull(UserGroup::getDeleteTime)
                .eq(UserGroup::getGroupId, groupId))
                .stream().map(UserGroup::getUserId).collect(Collectors.toList());
    }

    @Override
    public List<Group> getUserManageGroups(int userId) {
        return groupMapper.selectList(new LambdaQueryWrapper<Group>()
                .isNull(Group::getDeleteTime)
                .eq(Group::getCreatorId, userId));
    }

    @Override
    public boolean isUserCreator(int userId, int groupId) {
        return groupMapper.selectOne(new LambdaQueryWrapper<Group>()
                .isNull(Group::getDeleteTime)
                .eq(Group::getId, groupId)
                .eq(Group::getCreatorId, userId)) != null;
    }

    @Override
    public void updateLastMsgId(int groupId, int userId, int latestMsgId) {
        // 先获得好友映射，更新完最新消息 ID 再持久化
        UserGroup userGroup = getUserGroup(userId, groupId);
        userGroup.setLastMsgId(latestMsgId);
        userGroupMapper.updateById(userGroup);
    }
}
