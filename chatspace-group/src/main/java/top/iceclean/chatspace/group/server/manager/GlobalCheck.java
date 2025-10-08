package top.iceclean.chatspace.group.server.manager;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.cache.ZoneCache;
import top.iceclean.chatspace.group.server.BoxService;
import top.iceclean.chatspace.group.server.GroupService;
import top.iceclean.chatspace.group.server.SpaceService;
import top.iceclean.chatspace.infrastructure.auth.UserAuth;
import top.iceclean.chatspace.infrastructure.constant.ItemType;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.constant.ZoneType;
import top.iceclean.chatspace.infrastructure.po.*;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.infrastructure.utils.DateUtils;

import java.time.temporal.ChronoUnit;
import java.util.List;


/**
 * @author : Ice'Clean
 * @date : 2022-12-13
 */
@Slf4j
@Component
public class GlobalCheck {

    private final GroupService groupService;
    private final BoxService boxService;
    private final SpaceService spaceService;

    private final ZoneCache zoneCache;
    private final UserCache userCache;

    public GlobalCheck(GroupService groupService, BoxService boxService, SpaceService spaceService,
                       ZoneCache zoneCache, UserCache userCache) {
        this.groupService = groupService;
        this.boxService = boxService;
        this.spaceService = spaceService;
        this.zoneCache = zoneCache;
        this.userCache = userCache;
    }

    @Aspect
    @Order(1)
    @Component
    public final class ZoneCheckAspect {

        @Pointcut("@annotation(top.iceclean.chatspace.group.server.manager.ZoneInCheck)")
        public void zoneCheckPointCut() {
            // Pointcut methods should have empty body
        }

        /**
         * 检查当前用户对当前所在区域的操作是否合法
         * @return 合法则返回 null，否则可能为：不在区域中、无权限使用
         */
        @Around("zoneCheckPointCut() && @annotation(zoneInCheck)")
        public Object zoneInCheck(ProceedingJoinPoint joinPoint, ZoneInCheck zoneInCheck) throws Throwable {
            int userId = UserAuth.getUserId();
            Integer zoneId = 0;
            if (zoneInCheck.ack()) {
                // 如果需要确定进入，则获取用户当前所在的区域进行判断
                zoneId = zoneCache.getUserZoneId(userId);
                if (zoneId == 0) {
                    // 不在任何区域内
                    return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                            .setMsg("非法操作，当前位置不存在任何区域！");
                }
            } else {
                // 不需要确定进入，则检查临时区域
                zoneId = zoneCache.getTempZoneIn(userId);
                if (zoneId == null) {
                    // 不在任何区域内
                    return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                            .setMsg("非法操作，当前位置不存在任何区域！");
                } else if (zoneId < 0) {
                    // 如果 ID 小于零，则说明该用户没权限使用该区域，返回权限不足
                    return new Response(ResponseStatusEnum.AUTHORITY_ERROR)
                            .setMsg("没有使用该区域的权限！");
                }
            }
//            // 钥匙检查
//            Zone zone = zoneCache.getZone(zoneId);
//            if (zone.getAccessKey() != null) {
//                // 获取用户背包中的所有钥匙
//                List<BoxItem> keyList = boxService.getItemFromUserBox(userId, ItemType.KEY);
//                keyList.stream().filter(key -> boxService.getItemDetail())
//            }
            // 类型检查
            if (zoneInCheck.type() == ZoneType.BOX) {
                // 箱子类型检查
                if (boxService.getBoxByZoneId(zoneId) == null) {
                    return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                            .setMsg("非法操作，该区域不是箱子区域");
                }
            }

            // 检查通过
            return joinPoint.proceed();
        }
    }

    @Aspect
    @Order(3)
    @Component
    public final class ItemCheckAspect {
        @Pointcut("@annotation(top.iceclean.chatspace.group.server.manager.ItemCheck)")
        public void itemCheckPointCut() {
            // Pointcut methods should have empty body
        }

        @Around(value = "itemCheckPointCut() && @annotation(itemCheck)")
        private Object itemCheck(ProceedingJoinPoint joinPoint, ItemCheck itemCheck) throws Throwable {
            // 获取物品项 ID
            int boxItemId = (int) joinPoint.getArgs()[itemCheck.index()];
            // 获取当前用户 ID
            int userId = UserAuth.getUserId();
            // 获取指定的物品项
            BoxItem item = boxService.getBoxItemById(boxItemId);

            // 合法性判断
            if (!item.getBoxId().equals(userCache.getUserBoxId(userId))) {
                // 不在用户空间箱中
                return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                        .setMsg("非法操作，随身空间箱中不存在指定物品");
            } else if (ItemType.getBoxType(item.getItemId()) == null) {
                // 物品非箱子资源
                return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                        .setMsg("非法操作，使用的物品不是箱子资源");
            } else if (item.getAmount() <= 0) {
                // 物品数量不足
                return new Response(ResponseStatusEnum.ITEM_AMOUNT_LACK)
                        .setMsg("箱子资源数量不足");
            }

            // 检查通过
            return joinPoint.proceed();
        }
    }

    @Aspect
    @Order(4)
    @Component
    public final class CreatorCheckAspect {
        @Pointcut("@annotation(top.iceclean.chatspace.group.server.manager.CreatorCheck)")
        public void creatorCheckPointCut() {
            // Pointcut methods should have empty body
        }

        @Around(value = "creatorCheckPointCut() && @annotation(creatorCheck)")
        private Object creatorCheck(ProceedingJoinPoint joinPoint, CreatorCheck creatorCheck) throws Throwable {
            // 获取群组 ID
            int groupId;
            Object dataObject = joinPoint.getArgs()[creatorCheck.index()];
            if (creatorCheck.type() == Integer.class) {
                groupId = (int) dataObject;
            } else if (creatorCheck.type() == Zone.class) {
                groupId = ((Zone) dataObject).getGroupId();
            } else if (creatorCheck.type() == Group.class) {
                groupId = ((Group) dataObject).getId();
            } else {
                throw new ClassNotFoundException("不支持的群组 ID 所属类型：" + creatorCheck.type());
            }

            // 判断用户是否为当前区域的创建者
            if (!groupService.isUserCreator(UserAuth.getUserId(), groupId)) {
                return new Response(ResponseStatusEnum.AUTHORITY_ERROR)
                        .setMsg("当前用户不是目标群组的创建者！");
            }
            // 检查通过
            return joinPoint.proceed();
        }
    }

    @Aspect
    @Order(3)
    @Component
    public static final class TimeCheckAspect {
        @Pointcut("@annotation(top.iceclean.chatspace.group.server.manager.TimeCheck)")
        public void timeCheckPointCut() {
            // Pointcut methods should have empty body
        }

        @Around(value = "timeCheckPointCut() && @annotation(timeCheck)")
        private Object timeCheck(ProceedingJoinPoint joinPoint, TimeCheck timeCheck) throws Throwable {
            // 获取目标时间
            String targetTime = joinPoint.getArgs()[timeCheck.index()].toString();
            // 目标时间不为 null 时检查
            if (targetTime != null) {
                // 获取当前时间和目标时间的距离
                long interval = DateUtils.timeInterval(targetTime, ChronoUnit.MILLIS);
                if (timeCheck.after() && interval >= 0) {
                    return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                            .setMsg("设置的时间必须在当前时间之后！");
                } else if (!timeCheck.after() && interval < 0) {
                    return new Response(ResponseStatusEnum.ILLEGAL_OPERATION)
                            .setMsg("设置的时间必须在当前时间之前！");
                }
            }
            // 检查通过
            return joinPoint.proceed();
        }
    }

    @Aspect
    @Order(1)
    @Component
    public final class ZoneCoverCheckAspect {
        @Pointcut("@annotation(top.iceclean.chatspace.group.server.manager.ZoneCoverCheck)")
        public void zoneCoverCheckPointCut() {
            // Pointcut methods should have empty body
        }

        @Around(value = "zoneCoverCheckPointCut() && @annotation(zoneCoverCheck)")
        private Object zoneCoverCheck(ProceedingJoinPoint joinPoint, ZoneCoverCheck zoneCoverCheck) throws Throwable {
            // 获取区域实体，并在重叠的时候返回警告
            Zone zone = (Zone) joinPoint.getArgs()[zoneCoverCheck.index()];
            // 检查在同一个嵌套区域内，该区域的范围是否有其他区域
            if (zoneCache.isZoneCover(zone)) {
                return new Response(ResponseStatusEnum.ILLEGAL_ZONE_COVER);
            }
            // 检查是否超过根级区域的范围
            if (zone.getZoneRid() == 0) {
                // 为 0 的话，直接检查所在空间的大小
                Space space = spaceService.getSpaceData(zone.getSpaceId());
                if (zone.getStartX() < 0 || zone.getStartY() < 0 ||
                        zone.getEndX() > space.getSizeX() || zone.getEndY() > space.getSizeY()) {
                    return new Response(ResponseStatusEnum.OVER_ZONE_COVER);
                }
            } else {
                // 否则检查其根区域的大小
                Zone rootZone = zoneCache.getZone(zone.getZoneRid());
                if (zone.getStartX() < rootZone.getStartX() || zone.getStartY() < rootZone.getStartY() ||
                        zone.getEndX() > rootZone.getEndX() || zone.getEndY() > rootZone.getEndY()) {
                    return new Response(ResponseStatusEnum.OVER_ZONE_COVER);
                }
            }

            // 检查通过
            return joinPoint.proceed();
        }
    }
}
