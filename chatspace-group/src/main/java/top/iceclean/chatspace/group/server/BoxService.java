package top.iceclean.chatspace.group.server;

import top.iceclean.chatspace.infrastructure.constant.ItemType;
import top.iceclean.chatspace.infrastructure.po.Box;
import top.iceclean.chatspace.infrastructure.po.BoxItem;
import top.iceclean.chatspace.infrastructure.po.Zone;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.infrastructure.vo.BoxVO;
import top.iceclean.chatspace.infrastructure.vo.ItemVO;

import java.util.List;

/**
 * 箱子操作服务
 * @author : Ice'Clean
 * @date : 2022-12-13
 */
public interface BoxService {

    /**
     * 获取区域对应的箱子实体
     * @param zoneId 区域 ID
     * @return 箱子实体
     */
    Box getBoxByZoneId(int zoneId);

    /**
     * 根据物品项 ID 获取物品项
     * @param boxItemId 物品项 ID
     * @return 物品项实体
     */
    BoxItem getBoxItemById(int boxItemId);

    /**
     * 查询出箱子物品项对应的物品详情
     * @param boxItemId 箱子物品项 ID
     * @return 物品详情
     */
    Response getItemDetail(int boxItemId);

    /**
     * 获取用户随生空间箱中指定的物品
     * @param userId 用户 ID
     * @param type 指定物品的类型
     * @return 物品项列表
     */
    List<BoxItem> getItemFromUserBox(int userId, ItemType type);

    /**
     * 更新物品项的数量
     * @param boxItem 目标物品项
     * @param changeNum 变化的数量（正值增加，负值减少）
     * @return 是否更新成功
     */
    boolean updateItemAmount(BoxItem boxItem, int changeNum);

    /**
     * 将一个箱子资源放在空间中，创建箱子区域
     * @param boxItemId 箱子资源的物品项 ID
     * @param groupId 要把箱子放在哪个箱子组
     * @param boxName 箱子的名称
     * @return 创建反馈
     */
    Response checkedCreateBoxZone(int boxItemId, int groupId, String boxName);

    /**
     * 内部用的创建一片区域
     * @param zone 箱子所在的区域
     * @return 是否创建成功
     */
    Response createBoxZone(Zone zone);

    /**
     * 获取用户当前所在箱子区域的物品列表
     * @return 箱子物品列表
     */
    Response checkedGetBoxItemList();

    /**
     * 获取用户随生箱子中的物品列表
     * @return 随身箱子物品列表
     */
    Response getUserBoxItemList();

    /**
     * 在箱子之间移动物品
     * @param boxItemId 要操作的物品项 ID
     * @param num 要移动的数量
     * @return 操作结果
     */
    Response moveItem(int boxItemId, int num);

    /**
     * 内部用的获取箱子物品列表
     * @param boxId 指定箱子 ID
     * @return 箱子物品列表
     */
    BoxVO getBoxItemList(int boxId);

    /**
     * 开启一次新的夺宝
     * @return 新夺宝详情
     */
    Response newHunt();

    /**
     * 用户打开d当前位置的宝箱，随机拿出一件物品
     * @return 结果响应
     */
    Response openBoxRandom() throws InterruptedException;

    /**
     * 设置箱子锁
     * @param time 箱子锁定时任务
     * @param lock true 为加锁，false 为解锁
     * @return 是否设置成功
     */
    Response setBoxLock(String time, boolean lock);

    /**
     * 处理箱子锁操作
     * @param boxId 箱子 ID
     * @param lock true 为加锁，false 为解锁
     */
    void handleBoxLock(int boxId, boolean lock);
}
