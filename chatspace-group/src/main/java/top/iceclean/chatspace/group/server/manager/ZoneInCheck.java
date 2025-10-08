package top.iceclean.chatspace.group.server.manager;

import top.iceclean.chatspace.infrastructure.constant.ZoneType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 区域操作合法性检查（是否有权限、是否在区域内）
 * @author : Ice'Clean
 * @date : 2022-12-13
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ZoneInCheck {
    /**
     * 区域检查的标准
     * true 表示需要确定加入区域
     * false 表示只要处在区域里边即可
     */
    boolean ack();

    /**
     * 检查是否为指定的区域类型
     * 默认为 null 表示不需要检查
     * @return 区域类型
     */
    ZoneType type() default ZoneType.NON;
}
