package top.iceclean.chatspace.infrastructure.common;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Prometheus 监控
 * @author : Ice'Clean
 * @date : 2023-03-28
 */
@Slf4j
@Component
public class Monitor implements ApplicationContextAware {
    /** 监控指标枚举 */
    public enum Type {
        /** 用户连接数 */
        USER_CONNECTION,
        /** 移动事件积压数 */
        USER_MOVE_EVENT,
        /** 移动事件处理延迟 */
        USER_MOVE_DELAY
    }

    /** 单例 */
    private static Monitor instance;
    /** 监控用户连接数 */
    private Gauge userConnectionGauge;
    /** 监控用户事件积压数 */
    private Gauge userMoveEventGauge;
    /** 监控用户事件处理延迟（时间点、汇总） */
    private Gauge userMoveDelayGauge;
    private Histogram userMoveDelayHistogram;
    /** 用户事件响应时间 */
    private Histogram userMoveRespTime;
    /** 监听位置信息获取命中 */
    private Counter siteGetTotal;
    private Counter siteGetLocal;
    /** 监听视野和存在感二级缓存命中率 */
    private Counter veGetTotal;
    private Counter veGetLocal;
    /** 用户事件数量 */
    private Counter userEvent;
    /** 处理用户移动事件的速度 */
    private Counter userEventHandle;
    /** 区域处理耗时 */
    private Histogram zoneHandleTime;
    /** RabbitMQ 消息推送耗时 */
    private Histogram mqSendTime;
    /** 发送消息耗时 */
    private Histogram sendMessageTime;
    /** Geo 计算耗时 */
    private Histogram geoTime;
    /** 批量获取视野和存在感耗时 */
    private Histogram batchVisionTime;

    private Monitor() {

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        instance = this;
        CollectorRegistry collectorRegistry = applicationContext.getBean(CollectorRegistry.class);
        this.userConnectionGauge = Gauge.build().name("user_connection").help("用户的连接数").register(collectorRegistry);
        this.userMoveEventGauge = Gauge.build().name("user_move_event").help("用户事件积压数").register(collectorRegistry);
        this.userMoveDelayGauge = Gauge.build().name("user_move_delay_now").help("用户事件处理延迟").register(collectorRegistry);
        this.userMoveDelayHistogram = Histogram.build().buckets(0, 50, 100, 200, 400, 800, 1600).name("user_move_delay_summary").help("用户事件处理延迟统计").register(collectorRegistry);
        this.userMoveRespTime = Histogram.build().name("user_move_resp_time").buckets(0, 100, 200, 400, 800, 1600, 3200).help("用户事件响应时间").register(collectorRegistry);
        this.siteGetTotal = Counter.build().name("site_get_total").help("位置信息总获取数").register(collectorRegistry);
        this.siteGetLocal = Counter.build().name("site_get_local").help("位置信息本地获取数").register(collectorRegistry);
        this.veGetTotal = Counter.build().name("ve_get_total").help("视野和存在感总获取数").register(collectorRegistry);
        this.veGetLocal = Counter.build().name("ve_get_local").help("视野和存在感本地获取数").register(collectorRegistry);
        this.userEvent = Counter.build().name("user_event").help("用户事件总数").register(collectorRegistry);
        this.userEventHandle = Counter.build().name("user_event_handle").help("用户事件处理总数").register(collectorRegistry);
        this.zoneHandleTime = Histogram.build().name("zone_handle_time").buckets(0, 5, 10, 20, 40, 80, 160).help("区域处理耗时").register(collectorRegistry);
        this.mqSendTime = Histogram.build().name("mq_send_time").buckets(0, 2, 4, 8, 16, 32, 64).help("RabbitMQ 消息推送耗时").register(collectorRegistry);
        this.sendMessageTime = Histogram.build().name("send_message_time").buckets(0, 5, 10, 20, 40, 80, 160).help("发送消息耗时").register(collectorRegistry);
        this.geoTime = Histogram.build().name("geo_time").help("GEO 计算耗时").buckets(0, 10, 20, 40, 80, 160, 320).register(collectorRegistry);
        this.batchVisionTime = Histogram.build().name("batch_vision_exist").help("批量获取视野和存在感时间").buckets(0, 5, 10, 20, 40, 80, 160).register(collectorRegistry);
        log.info("【Prometheus】自定义监控初始化成功");
    }

    public static void change(Type type, double num) {
        switch (type) {
            case USER_CONNECTION:
                instance.userConnectionGauge.inc(num);
                break;
            case USER_MOVE_EVENT:
                instance.userMoveEventGauge.set(num);
                break;
            case USER_MOVE_DELAY:
                instance.userMoveDelayGauge.set(num);
                instance.userMoveDelayHistogram.observe(num);
                break;
            default:
        }
    }

    public static void siteGet(boolean local) {
        instance.siteGetTotal.inc();
        if (local) {
            instance.siteGetLocal.inc();
        }
    }

    public static void siteGet(boolean local, int num) {
        instance.siteGetTotal.inc(num);
        if (local) {
            instance.siteGetLocal.inc(num);
        }
    }

    public static void veGet(boolean local, int num) {
        instance.veGetTotal.inc(num);
        if (local) {
            instance.veGetLocal.inc(num);
        }
    }

    public static void newUserEvent() {
        instance.userEvent.inc();
    }
    public static void finishUserEvent() {
        instance.userEventHandle.inc();
    }

    public static void addZoneHandleTime(long time) {
        instance.zoneHandleTime.observe(time);
    }

    public static void addUserMoveRespTime(long time) {
        instance.userMoveRespTime.observe(time);
    }

    public static void addMqSendTime(long time) {
        instance.mqSendTime.observe(time);
    }

    public static void addSendMessageTime(long time) {
        instance.sendMessageTime.observe(time);
    }

    public static void addGeoTime(long time) {
        instance.geoTime.observe(time);
    }

    public static void addBatchVisionTime(long time) {
        instance.batchVisionTime.observe(time);
    }
}
