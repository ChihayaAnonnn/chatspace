package top.iceclean.chatspace.infrastructure.constant;

/**
 * 消息队列常量
 * @author : Ice'Clean
 * @date : 2022-12-17
 */
public class QueueConst {
    /** 箱子消息交换机 */
    public static final String BOX_EXCHANGE = "box-exchange";
    /** 延时队列交换机类型，固定值 x-delayed-message */
    public static final String DELAY_EXCHANGE_TYPE = "x-delayed-message";
    /** 箱子锁队列名称 */
    public static final String BOX_LOCK_QUEUE = "box-lock";
    /** 夺宝消息队列名称 */
    public static final String BOX_HUNT_QUEUE = "box-hunt";
    /** 区域更新队列名称 */
    public static final String ZONE_CREATED_QUEUE = "zone_created";
    /** 节点消费队列 */
    public static final String NODE_CONSUMER_QUEUE = "node-consumer";

    /** 区域更新常量 */

    private QueueConst() {

    }
}
