package top.iceclean.chatspace.group.server.manager;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 箱子物品检查
 * @author : Ice'Clean
 * @date : 2022-12-16
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ItemCheck {
    /**
     * 物品项 ID 位于参数的第几个
     * @return 物品项 ID 所在参数列表的位置
     */
    int index();
}
