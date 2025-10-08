package top.iceclean.chatspace.group.server.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.iceclean.chatspace.cache.*;
import top.iceclean.chatspace.group.mapper.box.BoxItemMapper;
import top.iceclean.chatspace.group.mapper.box.BoxMapper;
import top.iceclean.chatspace.group.mq.ZoneMessageProcessor;
import top.iceclean.chatspace.group.server.BoxService;
import top.iceclean.chatspace.group.server.ZoneService;
import top.iceclean.chatspace.group.server.manager.*;
import top.iceclean.chatspace.infrastructure.auth.UserAuth;
import top.iceclean.chatspace.infrastructure.constant.ItemType;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.constant.ZoneType;
import top.iceclean.chatspace.infrastructure.po.Box;
import top.iceclean.chatspace.infrastructure.po.BoxItem;
import top.iceclean.chatspace.infrastructure.po.Site;
import top.iceclean.chatspace.infrastructure.po.Zone;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;
import top.iceclean.chatspace.infrastructure.utils.IdWorker;
import top.iceclean.chatspace.infrastructure.vo.BoxVO;
import top.iceclean.chatspace.infrastructure.vo.ItemVO;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * @author : Ice'Clean
 * @date : 2022-12-13
 */
@Slf4j
@Service
public class BoxServiceImpl implements BoxService {

    private final BoxMapper boxMapper;
    private final BoxItemMapper boxItemMapper;

    private final ZoneService zoneService;
    private final IdWorker idWorker;

    /** 箱子锁消息队列处理器 */
    @Lazy
    private ZoneMessageProcessor zoneMessageProcessor;

    /** 缓存服务 */
    private final ZoneCache zoneCache;
    private final UserCache userCache;
    private final SpaceCache spaceCache;

    public BoxServiceImpl(BoxMapper boxMapper, BoxItemMapper boxItemMapper,
                          ZoneService zoneService, IdWorker idWorker,
                          ZoneCache zoneCache, UserCache userCache, SpaceCache spaceCache) {
        this.boxMapper = boxMapper;
        this.boxItemMapper = boxItemMapper;
        this.zoneService = zoneService;
        this.idWorker = idWorker;
        this.zoneCache = zoneCache;
        this.userCache = userCache;
        this.spaceCache = spaceCache;
    }

    @Autowired
    public void setBoxMessageProcessor(ZoneMessageProcessor zoneMessageProcessor) {
        this.zoneMessageProcessor = zoneMessageProcessor;
    }

    @Override
    public Box getBoxByZoneId(int zoneId) {
        return boxMapper.selectOne(new LambdaQueryWrapper<Box>()
                .eq(Box::getZoneId, zoneId));
    }

    @Override
    public BoxItem getBoxItemById(int boxItemId) {
        return boxItemMapper.selectById(boxItemId);
    }

    @Override
    public Response getItemDetail(int boxItemId) {
        return new Response(ResponseStatusEnum.OK)
                .setData(new ItemVO(boxItemMapper.getItemDetail(boxItemId)));
    }

    @Override
    public List<BoxItem> getItemFromUserBox(int userId, ItemType type) {
        // 获取用户空间箱 ID
        Integer userBoxId = userCache.getUserBoxId(userId);
        if (userBoxId == null) {
            return Collections.emptyList();
        }
        // 获取用户的随身空间箱 ID，并查询出指定物品的列表（同一个物品可以存放在多个位置）
        return boxItemMapper.selectList(new LambdaQueryWrapper<BoxItem>()
                .eq(BoxItem::getBoxId, userBoxId)
                .eq(BoxItem::getItemId, type.value())
                .gt(BoxItem::getAmount, 0));
    }

    @Override
    public boolean updateItemAmount(BoxItem boxItem, int changeNum) {
        boxItem.setAmount(boxItem.getAmount() + changeNum);
        return boxItemMapper.updateById(boxItem) == 1;
    }

    @Override
    @ItemCheck(index = 0)
    @CreatorCheck(type = Integer.class, index = 1)
    @Transactional
    public Response checkedCreateBoxZone(int boxItemId, int groupId, String boxName) {
        // 获取当前用户 ID 与当前所处区域
        int userId = UserAuth.getUserId();
        int nowZoneIn = zoneCache.getUserZoneId(userId);
        // 获取指定的物品项
        BoxItem item = getBoxItemById(boxItemId);
        ItemType boxType = ItemType.getCheckedBoxType(item.getItemId());

        // 执行创建箱子区域，成功的话，使该物品项数量 -1，最后返回成功
        Site userSite = spaceCache.getUserSite(userId);
        // 构造一个箱子区域实体
        Zone zone = new Zone(groupId, nowZoneIn, boxName, userSite, boxType);
        // 获取用户当前所在区域，如果有，则作为该区域的父级区域
        zone.setZonePid(zoneCache.getUserZoneId(UserAuth.getUserId()));
        // 进行箱子区域的创建（调用了区域的创建，自带检查机制）
        Response createResp = createBoxZone(zone);
        updateItemAmount(item, -1);
        return createResp.setMsg("创建箱子区域成功");
    }

    @Override
    @Transactional
    public Response createBoxZone(Zone zone) {
        // 尝试新建一个箱子区域
        Response resp = zoneService.createZone(zone);
        // 然后创建一个箱子实体
        Box box = new Box(zone.getId());
        boxMapper.insert(box);
        return resp.addData("box", box);
    }

    @Override
    @ZoneInCheck(ack = false, type = ZoneType.BOX)
    public Response checkedGetBoxItemList() {
        // 通过检查之后，获取箱子实体
        int zoneId = zoneCache.getTempZoneIn(UserAuth.getUserId());
        int boxId = getBoxByZoneId(zoneId).getId();
        return new Response(ResponseStatusEnum.OK)
                .setData(getBoxItemList(boxId));
    }

    @Override
    public Response getUserBoxItemList() {
        // 获取用户随身空间箱
        Integer userBoxId = userCache.getUserBoxId(UserAuth.getUserId());
        if (userBoxId == null) {
            return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                    .setMsg("当前身上没有携带的空间箱哦");
        }
        return new Response(ResponseStatusEnum.OK)
                .setData(getBoxItemList(userBoxId));
    }

    @Override
    @ZoneInCheck(ack = false, type = ZoneType.BOX)
    @Transactional
    public Response moveItem(int boxItemId, int num) {
        // 判断是否有随身空间箱
        Integer userBoxId = userCache.getUserBoxId(UserAuth.getUserId());
        if (userBoxId == null) {
            return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                    .setMsg("当前身上没有携带的空间箱哦");
        }
        // 获取物品及其所在的箱子
        BoxItem boxItem = boxItemMapper.selectById(boxItemId);
        if (boxItem == null) {
            return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                    .setMsg("没有可以移动的");
        }
        Integer boxId = boxItem.getBoxId();
        // 获取当前所处区域的箱子 ID
        Integer zoneBoxId = getBoxByZoneId(zoneCache.getTempZoneIn(UserAuth.getUserId())).getId();
        if (userBoxId.equals(boxId)) {
            // 是随身空间箱上的，则移动到目标箱子
            return moveItem(boxItem, zoneBoxId, num);
        } else if (zoneBoxId.equals(boxId)) {
            // 是区域箱子上的，则移动到空间箱
            return moveItem(boxItem, userBoxId, num);
        }
        return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                .setMsg("要操作的物品不在任何关联箱子中！");
    }

    /**
     * 将指定物品从一个箱子移动到另一个箱子
     * @param boxItem 箱子物品项
     * @param toBox 目标箱子
     * @param num 移动的数量（-1 表示全部）
     */
    private Response moveItem(BoxItem boxItem, int toBox, int num) {
        // 数量不足
        int amount = boxItem.getAmount();
        if (amount < num) {
            return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                    .setMsg("源箱子物品数量为 " + boxItem.getAmount() + "，不足 " + num);
        }

        if (num == -1 || amount == num) {
            // 如果是移动全部的话，需要删除物品项
            num = amount;
            boxItemMapper.deleteById(boxItem);
        } else {
            // 否则进行扣除
            boxItem.setAmount(amount - num);
            // 更新这个物品项
            boxItemMapper.updateById(boxItem);
        }

        // 然后放到另一个箱子
        return addItem(toBox, boxItem.getItemId(), num);
    }

    /**
     * 项指定箱子中添加指定物品指定数量
     * @param boxId 指定箱子
     * @param itemId 指定物品
     * @param num 指定数量
     * @return 添加结果
     */
    private Response addItem(int boxId, int itemId, int num) {
        // 获取箱子所有物品项
        List<BoxItem> boxItemList = boxItemMapper.selectList(new LambdaQueryWrapper<BoxItem>()
                .select(BoxItem::getId, BoxItem::getBoxIdx, BoxItem::getItemId, BoxItem::getAmount)
                .eq(BoxItem::getBoxId, boxId));
        // 移动逻辑
        boxItemList.sort(Comparator.comparingInt(BoxItem::getBoxIdx));
        int allowIdx = 1;
        for (BoxItem item : boxItemList) {
            // 找到一个可以插入的空位
            if (item.getBoxIdx() == allowIdx) {
                allowIdx++;
            }
            // 如果有相同的物品，则直接增加数量
            if (item.getItemId().equals(itemId)) {
                item.setAmount(item.getAmount() + num);
                boxItemMapper.updateById(item);
                return new Response(ResponseStatusEnum.OK);
            }
        }
        // 没有相同的，则插入一个到允许的位置
        boxItemMapper.insert(new BoxItem(boxId, allowIdx, itemId, num));
        return new Response(ResponseStatusEnum.OK);
    }

    @Override
    public BoxVO getBoxItemList(int boxId) {
        return new BoxVO(boxId, boxItemMapper.getBoxItemList(boxId));
    }

    @Override
    @ZoneInCheck(ack = false, type = ZoneType.BOX)
    public Response newHunt() {
        // 通过检查之后，获取箱子实体
        int zoneId = zoneCache.getTempZoneIn(UserAuth.getUserId());
        Box box = getBoxByZoneId(zoneId);
        // 获取新的夺宝序列号
        box.setHuntSeq(idWorker.nextId());
        // 发送一次
        return null;
    }

//    @Override
//    @BoxZoneInCheck
//    @ZoneInCheck(false)
//    public Response openBoxRandom() {
//        // 获取该区域对应的箱子实体
//        int zoneId = zoneCache.getUserZoneId(UserAuth.getUserId());
//        Box box = getBoxByZoneId(zoneId);
//        // 获取箱子内的物品列表实体
//        List<BoxItem> boxItems = boxItemMapper.selectList(new LambdaQueryWrapper<BoxItem>()
//                .eq(BoxItem::getBoxId, box.getId()));
//
//        // 随机选择一个返回
//        int idx = new Random(DateUtils.getDateTime().getTime()).nextInt(boxItems.size());
//        Integer boxIdx = boxItems.get(idx).getBoxIdx();
//        return new Response(ResponseStatusEnum.OK)
//                .setMsg("打开宝箱成功")
//                .setData(boxItemMapper.getBoxItem(box.getId(), boxIdx));
//    }

    @Override
    public Response openBoxRandom() throws InterruptedException {
        // 将夺宝消息入队
        int userId = new Random().nextInt(100);
        int huntId = new Random().nextInt(100);
//        zoneMessageProcessor.newUserHunt(userId, huntId);
        Thread.sleep(500);
        log.info("处理成功");
        return new Response(ResponseStatusEnum.OK)
                .setMsg("请求成功：" + userId + " - " + huntId);
    }

    @Override
    @ZoneInCheck(ack = false, type = ZoneType.BOX)
    @TimeCheck(after = true, index = 0)
    @Transactional
    public Response setBoxLock(String time, boolean lock) {
        // 获取该区域对应的箱子实体
        int zoneId = zoneCache.getUserZoneId(UserAuth.getUserId());
        Box box = getBoxByZoneId(zoneId);
        if (time == null) {
            // 如果时间为 null，则立即执行
            handleBoxLock(box.getId(), lock);
        } else {
            // 否则向数据库中添加加锁字段
            box.setBoxLock(time, lock);
            boxMapper.updateById(box);
            // 否则将锁任务添加到延迟队列
            zoneMessageProcessor.newLockMsg(time, box.getId(), lock);
        }

        return new Response(ResponseStatusEnum.OK)
                .setMsg("设置");
    }

    @Override
    public void handleBoxLock(int boxId, boolean lock) {
        // 获取当前时间
        LocalDateTime nowTime = DateUtils.getNowTime();
        // 获取箱子
        Box box = boxMapper.selectById(boxId);
        // 判断加解锁时间是否一致（不一致说明是旧消息）
        if (lock && nowTime.isEqual(box.getLockTime())) {
            box.setLockStat(1);
            boxMapper.updateById(box);
        } else if (!lock && nowTime.isEqual(box.getUnlockTime())) {
            box.setLockStat(0);
            boxMapper.updateById(box);
        }
    }

    /** 获取该类的代理类，使嵌套的 aop 能生效 */
    private BoxServiceImpl getProxy() {
        return (BoxServiceImpl) AopContext.currentProxy();
    }
}
