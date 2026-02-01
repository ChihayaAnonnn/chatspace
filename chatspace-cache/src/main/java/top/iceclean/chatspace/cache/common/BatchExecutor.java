package top.iceclean.chatspace.cache.common;

import org.redisson.Redisson;

/**
 * 自定义批量请求执行器
 * @author : Ice'Clean
 * @date : 2023-04-07
 */
public class BatchExecutor {
    /** 批量请求的类型 */
    public enum Type {
        /** GEO 视野覆盖 */
        GEO_VISION_COVER,
        /** GEO 存在感覆盖 */
        GEO_EXIST_COVER,
        /** GEO 位置更新 */
        GEO_SITE_UPDATE
    }
}
