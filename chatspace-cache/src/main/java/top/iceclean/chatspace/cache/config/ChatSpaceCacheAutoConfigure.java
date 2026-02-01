package top.iceclean.chatspace.cache.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import top.iceclean.chatspace.cache.*;
import top.iceclean.chatspace.cache.common.UserState;

/**
 * 缓存的自动装载
 * @author : Ice'Clean
 * @date : 2022-12-23
 */
@Configuration
@Import({GeoCache.class, GroupCache.class, SessionCache.class,
        SpaceCache.class, UserCache.class, ZoneCache.class, UserState.class})
public class ChatSpaceCacheAutoConfigure {
}
