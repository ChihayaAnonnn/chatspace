package top.iceclean.chatspace.realtime.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.iceclean.chatspace.cache.GeoCache;
import top.iceclean.chatspace.cache.SpaceCache;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.cache.ZoneCache;
import top.iceclean.chatspace.infrastructure.constant.RedisKey;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.po.Space;
import top.iceclean.chatspace.infrastructure.po.User;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;
import top.iceclean.chatspace.infrastructure.vo.UserVO;
import top.iceclean.chatspace.realtime.mapper.SiteMapper;
import top.iceclean.chatspace.realtime.service.SiteService;
import top.iceclean.feign.GroupClient;
import top.iceclean.feign.UserClient;

/**
 * @author : Ice'Clean
 * @date : 2022-10-28
 */
@Slf4j
@Service
public class SiteServiceImpl implements SiteService {

    private final SiteMapper siteMapper;
    private final GroupClient groupClient;
    private final GeoCache geoCache;
    private final UserCache userCache;
    private final ZoneCache zoneCache;


    public SiteServiceImpl(SiteMapper siteMapper, GroupClient groupClient,
                           GeoCache geoCache, UserCache userCache, ZoneCache zoneCache) {
        this.siteMapper = siteMapper;
        this.groupClient = groupClient;
        this.geoCache = geoCache;
        this.userCache = userCache;
        this.zoneCache = zoneCache;
    }

    @Override
    public Site getUserSite(int userId) {
        // 优化，先尝试从缓存中获取，且一次请求获取多个状态
        Integer[] userStates = userCache.getUserStates(userId, RedisKey.SPACE_SESSION, RedisKey.VISION, RedisKey.EXIST);
        if (!Arrays.isNullOrContainsNull(userStates)) {
            int zoneId = zoneCache.getUserZoneId(userId);
            // 获取用户的虚拟坐标，并构造 Site
            Site site = geoCache.getUserSite(userStates[0], zoneId, userId);
            site.setExist(userStates[1]);
            site.setVision(userStates[2]);
            return site;
        }
        // 否则从数据库获取
        Site site = siteMapper.selectById(userId);
        // 如果是第一次进入到空间，则为其初始化一个位置信息
        if (site == null) {
            // 初始化位置在 0 号空间的正中央
            Space spaceData = groupClient.getSpaceData(0);
            site = new Site();
            site.setId(userId);
            site.setSpaceId(0);
            site.setX(spaceData.getSizeX() / 2);
            site.setY(spaceData.getSizeY() / 2);
            site.setExist(2);
            site.setVision(4);
            siteMapper.insert(site);
        }
        return site;
    }

    @Override
    public SiteVO getSiteVO(int userId) {
        Site site = getUserSite(userId);
        User user = userCache.getUserById(userId);
        return new SiteVO(site, userCache.wrapUser(user));
    }

    @Override
    public void updateUserSite(SiteVO siteVO) {
        Site site = new Site();
        site.setId(siteVO.getUser().getUserId());
        site.setSpaceId(siteVO.getSpaceId());
        site.setX(siteVO.getX());
        site.setY(siteVO.getY());
        site.setVision(siteVO.getVision());
        site.setExist(siteVO.getExist());
        siteMapper.updateById(site);
    }

    @Override
    public void updateUserSite(Site site) {
        siteMapper.updateById(site);
    }
}
