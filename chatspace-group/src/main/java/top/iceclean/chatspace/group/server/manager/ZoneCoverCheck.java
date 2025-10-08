package top.iceclean.chatspace.group.server.manager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 区域覆盖的合法性判断
 * 是否覆盖到同级的其他区域
 * @author : Ice'Clean
 * @date : 2022-12-20
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ZoneCoverCheck {
    /**
     * 区域实体所在参数列表的位置
     * @return 区域实体所在参数列表的下标
     */
    int index();
}
