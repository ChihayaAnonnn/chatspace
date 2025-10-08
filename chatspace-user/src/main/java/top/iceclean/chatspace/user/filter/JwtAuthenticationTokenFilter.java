package top.iceclean.chatspace.user.filter;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import top.iceclean.chatspace.cache.UserCache;
import top.iceclean.chatspace.infrastructure.constant.RedisKey;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.pojo.GlobalException;
import top.iceclean.chatspace.infrastructure.utils.RedissonUtils;
import top.iceclean.chatspace.user.pojo.UserAuthority;
import top.iceclean.chatspace.infrastructure.utils.JwtUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * @author : Ice'Clean
 * @date : 2022-06-25
 */
@Slf4j
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    @Autowired
    private UserCache userCache;

    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver resolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 提取 token，如果为空需要放行，因为在访问security的匿名接口时过滤器同样起作用，而此时 token 值为空
        String token = request.getHeader("token");
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 解析 token
        String userId;
        try {
            userId = JwtUtils.parseJWT(token).getSubject();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("JWT 解析失败");
            resolver.resolveException(request,response,
                    null,new GlobalException(ResponseStatusEnum.AUTHENTICATION_ERROR, "Token 非法"));
            return;
        }

        // 校验用户信息
        UserAuthority userAuthority = JSON.parseObject(userCache.getAuthorityString(userId), UserAuthority.class);
        if (Objects.isNull(userAuthority)) {
            log.error("JWT 对应的用户不存在");
            resolver.resolveException(request,response,
                    null, new GlobalException(ResponseStatusEnum.AUTHENTICATION_ERROR, "Token 已过期"));
            return;
        }

        // 获取权限信息封装到 Authentication 中
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userAuthority, null, userAuthority.getAuthorities());
        // 存入 SecurityContextHolder
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        log.info("封装用户 {} 权限：{}", userAuthority.getUser().getUserName(), userAuthority.getAuthorities().toString());

        // 放行
        filterChain.doFilter(request, response);
    }
}
