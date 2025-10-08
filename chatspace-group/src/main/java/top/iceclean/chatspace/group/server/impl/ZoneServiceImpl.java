package top.iceclean.chatspace.group.server.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.iceclean.chatspace.cache.SpaceCache;
import top.iceclean.chatspace.group.mapper.ZoneMapper;
import top.iceclean.chatspace.group.mq.ZoneMessageProcessor;
import top.iceclean.chatspace.group.server.ZoneService;
import top.iceclean.chatspace.cache.ZoneCache;
import top.iceclean.chatspace.group.server.manager.CreatorCheck;
import top.iceclean.chatspace.group.server.manager.ZoneCoverCheck;
import top.iceclean.chatspace.infrastructure.auth.UserAuth;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.constant.ZoneType;
import top.iceclean.chatspace.infrastructure.dto.ZoneDTO;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.po.Zone;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.infrastructure.vo.ZoneVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author : Ice'Clean
 * @date : 2022-11-28
 */
@Service
public class ZoneServiceImpl implements ZoneService {

    /** 区域映射 */
    private final ZoneMapper zoneMapper;
    /** 区域缓存 */
    private final ZoneCache zoneCache;
    /** 空间缓存 */
    private final SpaceCache spaceCache;
    /** 区域消息队列生产者 */
    private final ZoneMessageProcessor zoneMessageProcessor;

    public ZoneServiceImpl(ZoneMapper zoneMapper, ZoneCache zoneCache, SpaceCache spaceCache, ZoneMessageProcessor zoneMessageProcessor) {
        this.zoneMapper = zoneMapper;
        this.zoneCache = zoneCache;
        this.spaceCache = spaceCache;
        this.zoneMessageProcessor = zoneMessageProcessor;
    }

    @Override
    public Response checkedCreateZone(ZoneDTO zoneDTO) {
        // 获取用户位置以及当前所在的区域（作为要创建的区域的父级区域）
        int userId = UserAuth.getUserId();
        Site userSite = spaceCache.getUserSite(userId);
        int zonePid = zoneCache.getUserZoneId(userId);
        int zoneRid;
        // 获取区域的根区域（没有父区域则为 0，有父区域则取嵌套区域的第一层）
        if (zonePid == 0) {
            zoneRid = 0;
        } else {
            Zone pZone = zoneCache.getZone(zonePid);
            zoneRid = pZone.getZoneRid() == 0 ?
                    pZone.getId() : pZone.getZoneRid();
        }
        // 构造出的目标区域实体
        Zone zone = new Zone(zoneDTO, ZoneType.PRIVATE, userSite, zonePid, zoneRid);
        // 执行区域创建（使用代理，带检查机制）
        return getProxy().createZone(zone);
    }


    @Override
    @CreatorCheck(type = Integer.class, index = 0)
    public Response getGroupZoneList(int groupId) {
        return new Response(ResponseStatusEnum.OK).addData("zoneList",
                zoneMapper.selectList(new LambdaQueryWrapper<Zone>()
                        .isNull(Zone::getDeleteTime)
                        .eq(Zone::getGroupId, groupId)));
    }

    @Override
    @ZoneCoverCheck(index = 0)
    @CreatorCheck(type = Zone.class, index = 0)
    @Transactional
    public Response createZone(Zone zone) {
        zoneMapper.insert(zone);
        // 将区域缓存起来
        ZoneVO zoneVO = zoneCache.cachedZone(zone);
        // 发送一条创建区域的消息
        zoneMessageProcessor.newZone(zoneVO);
        return new Response(ResponseStatusEnum.OK)
                .setMsg("区域创建成功！")
                .setData(zoneVO);
    }

    @Override
    @CreatorCheck(type = Zone.class, index = 0)
    public Response deleteZone(Zone zone) {
        //
        return null;
    }

    @Override
    public Zone getZoneById(int zoneId) {
        return zoneMapper.selectOne(new LambdaQueryWrapper<Zone>()
                .isNull(Zone::getDeleteTime)
                .eq(Zone::getId, zoneId));
    }

    @Override
    public List<ZoneVO> getZoneList(int spaceId, int zonePid) {
        return zoneMapper.selectList(new LambdaQueryWrapper<Zone>()
                .select(Zone::getId)
                .isNull(Zone::getDeleteTime)
                .eq(Zone::getSpaceId, spaceId)
                .eq(Zone::getZonePid, zonePid))
                .stream().map(zone -> zoneCache.getZoneVO(zone.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Integer> getChildZoneIds(int spaceId, int zonePid) {
        return zoneMapper.selectList(new LambdaQueryWrapper<Zone>()
                .select(Zone::getId)
                .isNull(Zone::getDeleteTime)
                .eq(Zone::getSpaceId, spaceId)
                .eq(Zone::getZonePid, zonePid))
                .stream().map(Zone::getId).collect(Collectors.toList());
    }

    @Override
    public List<Zone> getZones(int spaceId) {
        return zoneMapper.selectList(new LambdaQueryWrapper<Zone>()
                .isNull(Zone::getDeleteTime)
                .eq(Zone::getSpaceId, spaceId));
    }

    @Override
    public List<Zone> getGroupZones(int groupId) {
        return zoneMapper.selectList(new LambdaQueryWrapper<Zone>()
                .isNull(Zone::getDeleteTime)
                .eq(Zone::getGroupId, groupId));
    }

    @Override
    public List<Zone> getGroupZones(int groupId, int spaceId) {
        return zoneMapper.selectList(new LambdaQueryWrapper<Zone>()
                .isNull(Zone::getDeleteTime)
                .eq(Zone::getGroupId, groupId)
                .eq(Zone::getSpaceId, spaceId));
    }

    /** 获取该类的代理类，使嵌套的 aop 能生效 */
    private ZoneServiceImpl getProxy() {
        return (ZoneServiceImpl) AopContext.currentProxy();
    }
}
