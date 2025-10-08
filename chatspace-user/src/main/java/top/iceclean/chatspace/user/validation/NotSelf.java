package top.iceclean.chatspace.user.validation;


import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * 表示不是当前用户自己
 * @author : Ice'Clean
 * @date : 2022-08-23
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD, PARAMETER, TYPE})
@Constraint(validatedBy = {UserValidator.NotSelfValidator.class})
public @interface NotSelf {
    String message() default "目标对象非法：为用户本身";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
