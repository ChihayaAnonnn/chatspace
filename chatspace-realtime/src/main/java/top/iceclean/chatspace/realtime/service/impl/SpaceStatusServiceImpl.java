package top.iceclean.chatspace.realtime.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.iceclean.chatspace.realtime.common.SessionRepository;
import top.iceclean.chatspace.realtime.mapper.SpaceStatusMapper;
import top.iceclean.chatspace.realtime.service.SpaceStatusService;
import top.iceclean.chatspace.realtime.share.SpacePoint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : Ice'Clean
 * @date : 2022-10-27
 */
@Service
@Transactional
public class SpaceStatusServiceImpl implements SpaceStatusService {

    @Autowired
    private SpaceStatusMapper spaceStatusMapper;

    @Override
    public Set<Integer> addUser(int userId, int spaceId, SpacePoint point) {
        spaceStatusMapper.addStatus("t_site" + spaceId, point.toValue(userId));
        return spaceStatusMapper.getStatus("t_vision" + spaceId, point.toPoint())
                .stream().filter(uId -> canFeel(spaceId, uId, userId)).collect(Collectors.toSet());
    }

    @Override
    public Set<Integer> removeUser(int userId, int spaceId, SpacePoint point) {
        spaceStatusMapper.removeStatus("t_site" + spaceId, point.toValue(userId));
        return spaceStatusMapper.getStatus("t_vision" + spaceId, point.toPoint())
                .stream().filter(uId -> canFeel(spaceId, uId, userId)).collect(Collectors.toSet());
    }

    @Override
    public Set<Integer> addVision(int userId, int spaceId, List<SpacePoint> visionList) {
        if (visionList.isEmpty()) {
            return new HashSet<>(0);
        }
        spaceStatusMapper.addStatus("t_vision" + spaceId, batchValues(visionList, userId));
        return spaceStatusMapper.getStatus("t_site" + spaceId, batchPoints(visionList))
                .stream().filter(uId -> canFeel(spaceId, userId, uId)).collect(Collectors.toSet());
    }

    @Override
    public Set<Integer> removeVision(int userId, int spaceId, List<SpacePoint> visionList) {
        if (visionList.isEmpty()) {
            return new HashSet<>(0);
        }
        spaceStatusMapper.removeStatus("t_vision" + spaceId, batchValues(visionList, userId));
        return spaceStatusMapper.getStatus("t_site" + spaceId, batchPoints(visionList))
                .stream().filter(uId -> canFeel(spaceId, userId, uId)).collect(Collectors.toSet());
    }

    @Override
    public void addExist(int userId, int spaceId, List<SpacePoint> existList) {
        if (!existList.isEmpty()) {
            spaceStatusMapper.addStatus("t_exist" + spaceId, batchValues(existList, userId));
        }
    }

    @Override
    public void removeExist(int userId, int spaceId, List<SpacePoint> existList) {
        if (!existList.isEmpty()) {
            spaceStatusMapper.removeStatus("t_exist" + spaceId, batchValues(existList, userId));
        }
    }

    @Override
    public boolean canFeel(int spaceId, int fromUser, int toUser) {
        // 由于过滤需要，目标为本身是返回否
        if (fromUser == toUser) {
            return false;
        }
        // 否则，拿到用户的坐标，并查询该坐标上是否有另一个用户的存在感
        SpacePoint point = SpacePoint.get(SessionRepository.getSite(fromUser));
        return spaceStatusMapper.isExist(spaceId, point.toValue(toUser)) != null;
    }

    @Override
    public void destroyStatus(int userId, int spaceId) {
        // 分别销毁位置、视野和存在感状态
        spaceStatusMapper.destroyStatus("t_site" + spaceId, userId);
        spaceStatusMapper.destroyStatus("t_vision" + spaceId, userId);
        spaceStatusMapper.destroyStatus("t_exist" + spaceId, userId);
    }

    @Override
    public SpacePoint getPoint(int userId, int spaceId) {
        return spaceStatusMapper.getSite(spaceId, userId);
    }

    @Override
    public void initZone(int zoneId, int spaceId, List<SpacePoint> zoneList) {
        spaceStatusMapper.addZone(spaceId, batchValues(zoneList, zoneId));
    }

    @Override
    public Integer getZone(int spaceId, SpacePoint point) {
        return spaceStatusMapper.getZone(spaceId, point.toPoint());
    }

    private String batchValues(List<SpacePoint> pointList, int userId) {
        StringBuilder vBuilder = new StringBuilder();
        pointList.forEach(point -> vBuilder.append(point.toValue(userId)).append(","));
        return vBuilder.deleteCharAt(vBuilder.length()-1).toString();
    }

    private String batchPoints(List<SpacePoint> pointList) {
        StringBuilder pBuilder = new StringBuilder();
        pointList.forEach(point -> pBuilder.append(point.toPoint()).append(","));
        return pBuilder.deleteCharAt(pBuilder.length()-1).toString();
    }
}
