package com.io2c.httpproxyserver.handler.https;

import com.io2c.httpproxyserver.HttpProxyServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.net.URI;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;

public class HttpProxyRequestHandler extends ChannelInboundHandlerAdapter {
    private Bootstrap proxyClientBootstrap;
    private Properties configuration;
    private volatile Channel targetChannel;

    private Queue<Object> receivedLastMsgsWhenConnect = new LinkedList<>();

    public HttpProxyRequestHandler(Bootstrap proxyClientBootstrap, Properties configuration) {
        this.proxyClientBootstrap = proxyClientBootstrap;
        this.configuration = configuration;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            DefaultHttpRequest request = (DefaultHttpRequest) msg;
            //Basic认证
            String enableBasic = configuration.getProperty("auth.enableBasic");
            if (enableBasic != null && HttpProxyServer.TRUE.equals(enableBasic)) {
                if (!HttpProxyServer.basicLogin(request)) {
                    FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED);
                    resp.headers().add("Proxy-Authenticate", "Basic realm=\"Text\"");
                    resp.headers().set("Content-Length", resp.content().readableBytes());
                    ctx.channel().writeAndFlush(resp);
                    return;
                }
            }
            ctx.channel().attr(HttpProxyServer.httpUriAttributeKey).set(request.getUri());
            ctx.channel().attr(HttpProxyServer.httpMethodAttributeKey).set(request.getMethod());
            if (((HttpRequest) msg).getMethod() != HttpMethod.CONNECT) {
                URI uri = new URI(request.getUri());
                String uriQuery = request.getUri().replaceFirst(uri.getScheme() + "://" + uri.getHost(), "");
                ((DefaultHttpRequest) msg).setUri(uriQuery);
                if (((DefaultHttpRequest) msg).headers().get("Proxy-Connection") != null) {
                    ((DefaultHttpRequest) msg).headers().set("Connection", ((DefaultHttpRequest) msg).headers().get("Proxy-Connection"));
                    ((DefaultHttpRequest) msg).headers().remove("Proxy-Connection");
                }
                if (((DefaultHttpRequest) msg).headers().get("Proxy-Authorization") != null) {
                    ((DefaultHttpRequest) msg).headers().remove("Proxy-Authorization");
                }

                if (targetChannel == null) {
                    ctx.channel().config().setOption(ChannelOption.AUTO_READ, false);
                    //禁止访问的端口和代理端口一样，避免死循环
                    if (uri.getPort() == Integer.parseInt(configuration.getProperty(HttpProxyServer.CONFIG_SERVER_PORT_KEY))) {
                        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
                        resp.headers().set("Content-Length", resp.content().readableBytes());
                        ctx.channel().writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
                        return;
                    }
                    proxyClientBootstrap.connect(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()).addListener(new ChannelFutureListener() {

                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            // 连接后端服务器成功
                            if (future.isSuccess()) {
                                future.channel().writeAndFlush(msg);
                                Object msg0;
                                synchronized (receivedLastMsgsWhenConnect) {
                                    while ((msg0 = receivedLastMsgsWhenConnect.poll()) != null) {
                                        future.channel().writeAndFlush(msg0);
                                    }
                                    targetChannel = future.channel();
                                    targetChannel.attr(HttpProxyServer.nextChannelAttributeKey).set(ctx.channel());
                                    ctx.channel().attr(HttpProxyServer.nextChannelAttributeKey).set(targetChannel);
                                }
                                ctx.channel().config().setOption(ChannelOption.AUTO_READ, true);
                            } else {
                                ctx.channel().close();
                            }
                        }

                    });
                } else {
                    targetChannel.writeAndFlush(msg);
                }
            }
        } else {
            if (targetChannel == null) {
                synchronized (receivedLastMsgsWhenConnect) {
                    if (targetChannel == null) {
                        receivedLastMsgsWhenConnect.offer(msg);
                    } else {
                        targetChannel.writeAndFlush(msg);
                    }
                }
            } else {
                targetChannel.writeAndFlush(msg);
            }
        }
    }
}
