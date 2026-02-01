package top.iceclean.chatspace.realtime.handler;

import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Netty 的全局异常处理器
 * @author : Ice'Clean
 * @date : 2022-10-17
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class ExceptionHandler extends ChannelDuplexHandler {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //log.error("[ExceptionHandler] 捕获入站异常：{}", cause.toString());
        //cause.getCause().printStackTrace();
    }
}
