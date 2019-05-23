package com.io2c.httpproxyserver;

import com.io2c.httpproxyserver.container.Container;
import com.io2c.httpproxyserver.container.ContainerHelper;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;

/**
 * @author fei.feng
 */
public class HttpProxyServer implements Container {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProxyServer.class);

    private static final String TRUE = "true";

    private static final String CONFIG_SERVER_PORT_KEY = "server.port";

    private static AttributeKey<Channel> nextChannelAttributeKey = AttributeKey.newInstance("nextChannelAttributeKey");

    private static AttributeKey<HttpMethod> httpMethodAttributeKey = AttributeKey.newInstance("httpMethodAttributeKey");

    private static AttributeKey<String> httpUriAttributeKey = AttributeKey.newInstance("httpUriAttributeKey");

    private NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);

    private NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);

    private static Properties configuration = new Properties();

    static {
        InputStream is = HttpProxyServer.class.getClassLoader().getResourceAsStream("config.properties");
        try {
            configuration.load(is);
            is.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) {
        ContainerHelper.start(Arrays.asList((Container) new HttpProxyServer()));
    }

    @Override
    public void start() {
        ServerBootstrap httpServerBootstrap = new ServerBootstrap();
        Bootstrap proxyClientBootstrap = new Bootstrap();
        initProxyClient(proxyClientBootstrap, workerGroup);
        initProxyServer(httpServerBootstrap, proxyClientBootstrap, bossGroup, workerGroup);
        try {
            httpServerBootstrap.bind(configuration.getProperty("server.bind"), Integer.parseInt(configuration.getProperty(CONFIG_SERVER_PORT_KEY))).get();
            LOG.info("server started on port {}, bind {}", configuration.getProperty(CONFIG_SERVER_PORT_KEY), configuration.getProperty("server.bind"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void stop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    /**
     * 初始化代理服务客户端
     *
     * @param proxyClientBootstrap
     * @param workerGroup
     */
    private void initProxyClient(Bootstrap proxyClientBootstrap, NioEventLoopGroup workerGroup) {
        proxyClientBootstrap.channel(NioSocketChannel.class);
        proxyClientBootstrap.group(workerGroup).handler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {

                ch.pipeline().addLast("httpClientCodec", new HttpClientCodec());
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ctx.channel().attr(nextChannelAttributeKey).get().writeAndFlush(msg);
                    }

                    @Override
                    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                        Channel clientChannel = ctx.channel().attr(nextChannelAttributeKey).get();
                        if (clientChannel != null) {
                            clientChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
                        }
                        super.channelWritabilityChanged(ctx);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        Channel clientChannel = ctx.channel().attr(nextChannelAttributeKey).get();
                        if (clientChannel != null) {
                            clientChannel.close();
                        }
                        super.channelInactive(ctx);
                    }
                });
            }
        });
    }


    /**
     * 初始化代理服务器
     *
     * @param httpServerBootstrap
     * @param proxyClientBootstrap
     * @param bossGroup
     * @param workerGroup
     */
    private void initProxyServer(ServerBootstrap httpServerBootstrap, final Bootstrap proxyClientBootstrap, NioEventLoopGroup bossGroup, NioEventLoopGroup workerGroup) {
        httpServerBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                LOG.error("exceptionCaught", cause);
                super.exceptionCaught(ctx, cause);
            }

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new ChannelInboundHandlerAdapter() {

                    private volatile Channel targetChannel;

                    @Override
                    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (targetChannel == null) {
                            super.channelRead(ctx, msg);
                            // HTTPS代理
                            if (ctx.channel().attr(httpMethodAttributeKey).get() == HttpMethod.CONNECT) {
                                URI uri = new URI("https://" + ctx.channel().attr(httpUriAttributeKey).get());
                                proxyClientBootstrap.connect(uri.getHost(), uri.getPort()).addListener(new ChannelFutureListener() {

                                    @Override
                                    public void operationComplete(final ChannelFuture future) throws Exception {

                                        // 连接后端服务器成功
                                        if (future.isSuccess()) {
                                            targetChannel = future.channel();
                                            targetChannel.attr(nextChannelAttributeKey).set(ctx.channel());
                                            ctx.channel().attr(nextChannelAttributeKey).set(targetChannel);
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
                        Channel targetChannel = ctx.channel().attr(nextChannelAttributeKey).get();
                        if (targetChannel != null) {
                            targetChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
                        }
                        super.channelWritabilityChanged(ctx);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        Channel targetChannel = ctx.channel().attr(nextChannelAttributeKey).get();
                        if (targetChannel != null) {
                            targetChannel.close();
                        }
                        super.channelInactive(ctx);
                    }
                });
                pipeline.addLast("httpServerCodec", new HttpServerCodec()).addLast("httpRequestHandler", new ChannelInboundHandlerAdapter() {

                    private volatile Channel targetChannel;

                    private Queue<Object> receivedLastMsgsWhenConnect = new LinkedList<>();

                    @Override
                    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
                        if (msg instanceof HttpRequest) {
                            DefaultHttpRequest request = (DefaultHttpRequest) msg;
                            //Basic认证
                            String enableBasic = configuration.getProperty("auth.enableBasic");
                            if (enableBasic != null && TRUE.equals(enableBasic)) {
                                if (!basicLogin(request)) {
                                    FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED);
                                    resp.headers().add("Proxy-Authenticate", "Basic realm=\"Text\"");
                                    resp.headers().set("Content-Length", resp.content().readableBytes());
                                    ctx.channel().writeAndFlush(resp);
                                    return;
                                }
                            }
                            ctx.channel().attr(httpUriAttributeKey).set(request.getUri());
                            ctx.channel().attr(httpMethodAttributeKey).set(request.getMethod());
                            if (((HttpRequest) msg).getMethod() != HttpMethod.CONNECT) {
                                if (targetChannel == null) {
                                    URI uri = new URI(ctx.channel().attr(httpUriAttributeKey).get());
                                    ctx.channel().config().setOption(ChannelOption.AUTO_READ, false);
                                    //禁止访问的端口和代理端口一样，避免死循环
                                    if (uri.getPort() == Integer.parseInt(configuration.getProperty(CONFIG_SERVER_PORT_KEY))) {
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
                                                    targetChannel.attr(nextChannelAttributeKey).set(ctx.channel());
                                                    ctx.channel().attr(nextChannelAttributeKey).set(targetChannel);
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
                });
            }
        });
    }

    /**
     * basic方式登录
     *
     * @param req
     * @return
     */
    public static boolean basicLogin(HttpRequest req) {
        //获取请求头中的 Proxy-Authorization
        String s = req.headers().get("Proxy-Authorization");
        if (s == null) {
            return false;
        }
        try {
            String[] split = s.split(" ");
            ByteBuf byteBuf = io.netty.handler.codec.base64.Base64.decode(Unpooled.copiedBuffer(split[1].getBytes()));
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            byteBuf.release();
            String userNamePassWord = new String(bytes);
            String[] split1 = userNamePassWord.split(":", 2);
            String password = configuration.getProperty("auth." + split1[0]);
            if (password == null || !password.equals(split1[1])) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

}
