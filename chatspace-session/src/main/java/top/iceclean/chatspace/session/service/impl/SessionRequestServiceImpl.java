package top.iceclean.chatspace.session.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import top.iceclean.chatspace.cache.GroupCache;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.constant.SessionType;
import top.iceclean.chatspace.infrastructure.dto.RequestDTO;
import top.iceclean.chatspace.infrastructure.po.Group;
import top.iceclean.chatspace.infrastructure.po.SessionRequest;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;
import top.iceclean.chatspace.infrastructure.vo.GroupVO;
import top.iceclean.chatspace.infrastructure.vo.RequestBadgeVO;
import top.iceclean.chatspace.infrastructure.vo.SessionRequestListVO;
import top.iceclean.chatspace.infrastructure.vo.SessionRequestVO;
import top.iceclean.chatspace.session.mapper.SessionRequestMapper;
import top.iceclean.chatspace.session.mq.MessageProducer;
import top.iceclean.chatspace.session.service.SessionRequestService;
import top.iceclean.feign.FriendClient;
import top.iceclean.feign.GroupClient;
import top.iceclean.feign.UserClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : Ice'Clean
 * @date : 2022-06-25
 */
@Service
public class SessionRequestServiceImpl implements SessionRequestService {
    @Autowired
    private SessionRequestMapper requestMapper;
    @Autowired
    private MessageProducer messageProducer;
    @Autowired
    private UserClient userClient;
    @Autowired
    private GroupClient groupClient;
    @Autowired
    private FriendClient friendClient;

    private final UserCache userCache;
    private final GroupCache groupCache;

    public SessionRequestServiceImpl(UserCache userCache, GroupCache groupCache) {
        this.userCache = userCache;
        this.groupCache = groupCache;
    }

    @Override
    public Response sendRequest(RequestDTO requestDTO) {
        // 构造请求消息
        SessionRequest request = new SessionRequest(requestDTO);
        try {
            // 直接添加请求记录
            requestMapper.insert(request);
            request.setCreateTime(DateUtils.getDateTime());
            // 将添加消息实时推送
            messageProducer.sessionRequest(request);
        } catch (DuplicateKeyException e) {
            return new Response(ResponseStatusEnum.REQUEST_PARAM_ILLEGAL)
                    .setMsg("已经成为好友");
        }

        return new Response(ResponseStatusEnum.OK);
    }

    @Override
    public Response getRequestList(int userId) {
        // 查询出用户所有管理的群聊的 ID
        List<Integer> manageGroupIds = groupClient.getUserManageGroups(userId)
                .stream().map(Group::getId).collect(Collectors.toList());
        // 查询用户发送的以及接收的所有申请
        List<SessionRequestVO> requestList = requestMapper.selectList(new LambdaQueryWrapper<SessionRequest>()
                // 用户发送的好友请求和群聊请求
                .eq(SessionRequest::getSenderId, userId)
                // 或者用户接收的好友请求
                .or(orQuery -> orQuery
                        .eq(SessionRequest::getType, 0)
                        .eq(SessionRequest::getTargetId, userId))
                // 或者用户接收的群聊请求（用户为群主或管理员）
                .or(!manageGroupIds.isEmpty(), orQuery -> orQuery
                        .eq(SessionRequest::getType, 1)
                        .in(SessionRequest::getTargetId, manageGroupIds)))
                .stream().map(request -> toRequestVO(request, userId)).collect(Collectors.toList());
        // 按会话类型和是否为用户发起，分成四类
        Map<Integer, Map<Integer, List<SessionRequestVO>>> collect = requestList.stream()
                .collect(Collectors.groupingBy(SessionRequestVO::getType, Collectors.groupingBy(SessionRequestVO::getKind)));
        // 转化为响应对象返回
        return new Response(ResponseStatusEnum.OK).setData(new SessionRequestListVO(collect));
    }

    @Override
    public Response getRequestBadge(int userId) {
        // 查询出用户所有管理的群聊的 ID
        List<Integer> manageGroupIds = groupClient.getUserManageGroups(userId)
                .stream().map(Group::getId).collect(Collectors.toList());
        // 查询域用户相关的所有未读的申请
        Map<Integer, Long> collect = requestMapper.selectList(new LambdaQueryWrapper<SessionRequest>()
                // 未读的记录
                .eq(SessionRequest::getReqStatus, 0)
                .and(query -> query
                        // 好友申请
                        .eq(SessionRequest::getType, 0)
                        .eq(SessionRequest::getTargetId, userId)
                        // 或者群聊申请
                        .or(!manageGroupIds.isEmpty(), orQuery -> orQuery
                                .eq(SessionRequest::getType, 1)
                                .in(SessionRequest::getTargetId, manageGroupIds))))
                .stream().collect(Collectors.groupingBy(SessionRequest::getType, Collectors.counting()));
        return new Response(ResponseStatusEnum.OK).setData(new RequestBadgeVO(collect));
    }

    @Override
    public Response handleRequest(int reqId, boolean access) {
        // 将目标请求记录的状态置为接受/拒绝
        SessionRequest request = requestMapper.selectById(reqId);
        request.setReqStatus(access ? 2 : 3);
        requestMapper.updateById(request);

        // 同意申请后，需要建立相应的映射关系
        boolean success = true;
        if (access) {
            if (request.getType() == SessionType.FRIEND.value()) {
                success = friendClient.becomeFriends(request.getSenderId(), request.getTargetId());
            } else {
                success = groupClient.joinGroup(request.getSenderId(), request.getTargetId());
            }
        }

        if (success) {
            return new Response(ResponseStatusEnum.OK);
        }
        return new Response(ResponseStatusEnum.DATABASE_ERROR);
    }

    @Override
    public SessionRequestVO toRequestVO(SessionRequest request, int userId) {
        // 准备返回的响应对象
        SessionRequestVO requestVO = new SessionRequestVO(request, userId);

        // 存储会话类型（好友/群聊）
        String type = "" + request.getType();
        // 存储请求类型（该请求是否为用户发送）
        String kind = "" + (request.getSenderId() == userId ? 1 : 0);

        // 组合成不同种类的响应对象（type 作为十位数，kind 作为个位数的组合）
        switch (type + kind) {
            case "00":
                requestVO.setUser(userCache.wrapUser(userCache.getUserById(request.getSenderId())));
                break;
            case "10":
                requestVO.setUser(userCache.wrapUser(userCache.getUserById(request.getSenderId())));
                Group group = groupClient.getGroupById(request.getTargetId());
                System.out.println(group);
                requestVO.setGroup(new GroupVO(group, groupCache.onlineUserNum(group.getId())));
                break;
            case "01":
                requestVO.setUser(userCache.wrapUser(userCache.getUserById(request.getTargetId())));
                break;
            case "11":
                requestVO.setGroup(groupCache.getUserGroupVO(request.getTargetId(), request.getSenderId()));
                break;
            default:
        }

        // 若 status 为 0 且该用户为接收者，则置为 1 表示已读
        if (request.getSenderId() != userId && request.getReqStatus() == 0) {
            request.setReqStatus(1);
            requestMapper.updateById(request);
        }

        // 返回最后的响应对象
        return requestVO;
    }
}
