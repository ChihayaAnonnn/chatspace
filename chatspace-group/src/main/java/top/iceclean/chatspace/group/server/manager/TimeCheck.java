package top.iceclean.chatspace.group.server.manager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author : Ice'Clean
 * @date : 2022-12-17
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeCheck {
    /**
     * true 表示检查时间是否在当前时间之后
     * false 表示检查时间是否在当前时间之前
     * @return 检查的规则
     */
    boolean after();

    /**
     * 时间所在参数列表的位置
     * @return 参数列表的下标
     */
    int index();
}
