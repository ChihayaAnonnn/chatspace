package top.iceclean.chatspace.user.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.pojo.Response;

/**
 * 鉴权异常处理器
 * @author : Ice'Clean
 * @date : 2022-10-09
 */
@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    /**
     * SpringSecurity 的剩余认证异常需要额外捕获
     * @param exception 认证异常
     * @return 异常及请求信息
     */
    @ExceptionHandler(value = AuthenticationException.class)
    public Response handleAuthenticationException(AuthenticationException exception) {
        String extraMessage = exception.getMessage();
        if ("Bad credentials".equals(extraMessage)) {
            extraMessage = "用户名或密码错误";
        }
        return new Response(ResponseStatusEnum.AUTHENTICATION_ERROR)
                .setMsg(ResponseStatusEnum.AUTHENTICATION_ERROR.msg() + extraMessage);
    }

    /**
     * SpringSecurity 的剩余权限异常需要额外捕获
     * @param exception 权限异常
     * @return 异常及请求信息
     */
    @ExceptionHandler(value = AccessDeniedException.class)
    public Response handleAccessDeniedException(AccessDeniedException exception) {
        return new Response(ResponseStatusEnum.AUTHORITY_ERROR)
                .setMsg(ResponseStatusEnum.AUTHENTICATION_ERROR.msg() + exception.getMessage());
    }
}
