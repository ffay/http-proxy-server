package com.io2c.httpproxyserver.handler.socks;

import com.io2c.httpproxyserver.HttpProxyServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 处理服务端 channel.
 */
public class HttpsSocksProxyChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static Logger logger = LoggerFactory.getLogger(HttpsSocksProxyChannelHandler.class);

    private Bootstrap proxyClientBootstrap;

    public HttpsSocksProxyChannelHandler(Bootstrap proxyClientBootstrap) {
        this.proxyClientBootstrap = proxyClientBootstrap;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
       // logger.error("exceptionCaught", cause);
        // 当出现异常就关闭连接
        try {
          //  ctx.close();
        } catch (Exception e) {
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        Channel nextChannel = ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).get();
        nextChannel.writeAndFlush(buf.copy());
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        // 用户连接到代理服务器时，设置用户连接不可读，等待代理后端服务器连接成功后再改变为可读状态
        channel.config().setOption(ChannelOption.AUTO_READ, false);
        String connectInfo = channel.attr(HttpProxyServer.connectInfoAttributeKey).get();
        String[] connectInfoArr = connectInfo.split(":");
        if (connectInfoArr.length != 2) {
            channel.close();
        }
        proxyClientBootstrap.connect(connectInfoArr[0], Integer.parseInt(connectInfoArr[1])).addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                // 连接后端服务器成功
                if (future.isSuccess()) {
                    future.channel().attr(HttpProxyServer.nextChannelAttributeKey).set(ctx.channel());
                    ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).set(future.channel());
                    ctx.channel().config().setOption(ChannelOption.AUTO_READ, true);
                } else {
                    future.channel().close();
                }
            }
        });

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

}