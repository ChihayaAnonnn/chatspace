package top.iceclean.chatspace.gateway.config;

import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;

/**
 * @author : Ice'Clean
 * @date : 2023-03-06
 */
public class FastCodec extends BaseCodec {
    FastJsonRedisSerializer<Object> serializer = new FastJsonRedisSerializer<>(Object.class);

    private final Encoder encoder = in -> {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
        return byteBuf.writeBytes(serializer.serialize(in));
    };

    private final Decoder<Object> decoder = (buf, state) -> {
        int len = buf.readableBytes();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return serializer.deserialize(bytes) ;
    };

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }
}
