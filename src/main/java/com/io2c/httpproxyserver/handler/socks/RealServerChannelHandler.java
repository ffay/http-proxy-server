package com.io2c.httpproxyserver.handler.socks;

import com.io2c.httpproxyserver.HttpProxyServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理服务端 channel.
 */
public class RealServerChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static Logger logger = LoggerFactory.getLogger(RealServerChannelHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        Channel nextChannel = ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).get();
        nextChannel.writeAndFlush(buf.copy());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).get() != null) {
            try {
                ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).get().close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.channelInactive(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).get();
        if (channel != null) {
            channel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exception caught", cause);
        super.exceptionCaught(ctx, cause);
    }
}