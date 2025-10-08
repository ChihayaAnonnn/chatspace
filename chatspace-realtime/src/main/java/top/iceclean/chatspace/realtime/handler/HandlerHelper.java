package top.iceclean.chatspace.realtime.handler;

import io.netty.channel.ChannelHandlerContext;
import top.iceclean.chatspace.realtime.constant.ChannelKey;

/**
 * 处理器的帮助工具
 * @author : Ice'Clean
 * @date : 2022-11-26
 */
public class HandlerHelper {

    public static String getToken(ChannelHandlerContext ctx) {
        return ctx.channel().attr(ChannelKey.TOKEN).get();
    }

    public static void setToken(ChannelHandlerContext ctx, String token) {
        ctx.channel().attr(ChannelKey.TOKEN).set(token);
    }

    public static int getUserId(ChannelHandlerContext ctx) {
        return ctx.channel().attr(ChannelKey.USER_ID_KEY).get();
    }

    public static void setUserId(ChannelHandlerContext ctx, int userId) {
        ctx.channel().attr(ChannelKey.USER_ID_KEY).set(userId);
    }

    public static int getSpaceId(ChannelHandlerContext ctx) {
        return ctx.channel().attr(ChannelKey.SPACE_ID_KEY).get();
    }

    public static void setSpaceId(ChannelHandlerContext ctx, int spaceId) {
        ctx.channel().attr(ChannelKey.SPACE_ID_KEY).set(spaceId);
    }

    /** 设置当前通道需要强制下线 */
    public static void setForcedOffline(ChannelHandlerContext ctx) {
        ctx.channel().attr(ChannelKey.FORCED_OFFLINE).set(true);
    }

    /** 返回当前通道的强制下线标识 */
    public static boolean isForcedOffline(ChannelHandlerContext ctx) {
        return Boolean.TRUE.equals(ctx.channel().attr(ChannelKey.FORCED_OFFLINE).get());
    }
}
