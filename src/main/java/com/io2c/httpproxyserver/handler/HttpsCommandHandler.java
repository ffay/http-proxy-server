package com.io2c.httpproxyserver.handler;

import com.io2c.httpproxyserver.HttpProxyServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.net.URI;

public class HttpsCommandHandler extends ChannelInboundHandlerAdapter {

    private Bootstrap proxyClientBootstrap;

    private volatile Channel targetChannel;

    public HttpsCommandHandler(Bootstrap proxyClientBootstrap) {
        this.proxyClientBootstrap = proxyClientBootstrap;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        if (targetChannel == null) {
            super.channelRead(ctx, msg);
            // HTTPS代理
            if (ctx.channel().attr(HttpProxyServer.httpMethodAttributeKey).get() == HttpMethod.CONNECT) {
                URI uri = new URI("https://" + ctx.channel().attr(HttpProxyServer.httpUriAttributeKey).get());
                proxyClientBootstrap.connect(uri.getHost(), uri.getPort()).addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(final ChannelFuture future) throws Exception {

                        // 连接后端服务器成功
                        if (future.isSuccess()) {
                            targetChannel = future.channel();
                            targetChannel.attr(HttpProxyServer.nextChannelAttributeKey).set(ctx.channel());
                            ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).set(targetChannel);
                            future.channel().pipeline().remove("httpClientCodec");
                            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "Connection Established"));
                            ctx.channel().writeAndFlush(resp).addListener(new ChannelFutureListener() {

                                @Override
                                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                                    ctx.pipeline().remove("httpServerCodec");
                                    ctx.pipeline().remove("httpRequestHandler");
                                }
                            });
                        } else {
                            ctx.channel().close();
                        }
                    }
                });
            }
        } else {
            targetChannel.writeAndFlush(msg);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel targetChannel = ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).get();
        if (targetChannel != null) {
            targetChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel targetChannel = ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).get();
        if (targetChannel != null) {
            targetChannel.close();
        }
        super.channelInactive(ctx);
    }
}
