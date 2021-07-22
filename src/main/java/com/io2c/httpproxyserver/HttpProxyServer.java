package com.io2c.httpproxyserver;

import com.io2c.httpproxyserver.container.Container;
import com.io2c.httpproxyserver.container.ContainerHelper;
import com.io2c.httpproxyserver.handler.https.HttpProxyRequestHandler;
import com.io2c.httpproxyserver.handler.https.HttpsCommandHandler;
import com.io2c.httpproxyserver.handler.https.HttpsTunnelProxyChannelHandler;
import com.io2c.httpproxyserver.handler.https.HttpsTunnelProxyRealServerChannelHandler;
import com.io2c.httpproxyserver.handler.socks.Socks5CommandRequestHandler;
import com.io2c.httpproxyserver.handler.socks.Socks5InitialRequestHandler;
import com.io2c.httpproxyserver.handler.socks.Socks5PasswordAuthRequestHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * @author fei.feng
 */
public class HttpProxyServer implements Container {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProxyServer.class);

    public static final String TRUE = "true";

    public static final String CONFIG_SERVER_PORT_KEY = "server.port";

    public static AttributeKey<Channel> nextChannelAttributeKey = AttributeKey.newInstance("nextChannelAttributeKey");

    public static AttributeKey<HttpMethod> httpMethodAttributeKey = AttributeKey.newInstance("httpMethodAttributeKey");

    public static AttributeKey<String> httpUriAttributeKey = AttributeKey.newInstance("httpUriAttributeKey");

    public static AttributeKey<String> connectInfoAttributeKey = AttributeKey.newInstance("connectInfoAttributeKey");

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
        ServerBootstrap httpsServerBootstrap = new ServerBootstrap();
        Bootstrap proxyClientBootstrap = new Bootstrap();
        initProxyClient(proxyClientBootstrap, workerGroup);
        initHttpProxyServer(httpServerBootstrap, proxyClientBootstrap, bossGroup, workerGroup);
        initHttpsProxyServer(httpsServerBootstrap, proxyClientBootstrap, bossGroup, workerGroup);
        initHttpsTunnelProxyServer();
        initSocks5ProxyServer();
        try {
            httpServerBootstrap.bind(configuration.getProperty("server.bind"), Integer.parseInt(configuration.getProperty(CONFIG_SERVER_PORT_KEY))).get();
            LOG.info("http proxy server started on port {}, bind {}", configuration.getProperty(CONFIG_SERVER_PORT_KEY), configuration.getProperty("server.bind"));
            httpsServerBootstrap.bind(configuration.getProperty("server.https.bind"), Integer.parseInt(configuration.getProperty("server.https.port"))).get();
            LOG.info("https proxy server started on port {}, bind {}", configuration.getProperty("server.https.port"), configuration.getProperty("server.https.bind"));
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

    private void initHttpsProxyServer(ServerBootstrap httpsServerBootstrap, final Bootstrap proxyClientBootstrap, NioEventLoopGroup bossGroup, NioEventLoopGroup workerGroup) {
        final SSLContext sslContext = new SslContextCreator().initSSLContext(configuration.getProperty("server.https.jksPath"),
                configuration.getProperty("server.https.keyStorePassword"), configuration.getProperty("server.https.keyManagerPassword"));
        httpsServerBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                LOG.error("exceptionCaught", cause);
                super.exceptionCaught(ctx, cause);
            }

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("ssl", createSslHandler(sslContext));
                pipeline.addLast(new HttpsCommandHandler(proxyClientBootstrap));
                pipeline.addLast("httpServerCodec", new HttpServerCodec()).addLast("httpRequestHandler", new HttpProxyRequestHandler(proxyClientBootstrap, configuration));
            }
        });
    }

    /**
     * https隧道代理其他协议端口
     */
    private void initHttpsTunnelProxyServer() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        final Bootstrap proxyClientBootstrap = new Bootstrap();
        proxyClientBootstrap.channel(NioSocketChannel.class);
        proxyClientBootstrap.group(workerGroup).handler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new HttpsTunnelProxyRealServerChannelHandler());
            }
        });

        String configStr = configuration.getProperty("https.tunnel.config");//port->ip:port,port->ip:port
        final Map<Integer, String> portMap = new HashMap<>();
        final SSLContext sslContext = new SslContextCreator().initSSLContext(configuration.getProperty("server.https.jksPath"),
                configuration.getProperty("server.https.keyStorePassword"), configuration.getProperty("server.https.keyManagerPassword"));
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                LOG.error("exceptionCaught", cause);
                super.exceptionCaught(ctx, cause);
            }

            @Override
            public void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                InetSocketAddress sa = ch.localAddress();
                String ipPort = portMap.get(sa.getPort());
                if (ipPort == null) {
                    ch.close();
                    return;
                }
                ch.attr(connectInfoAttributeKey).set(ipPort);
                pipeline.addLast("ssl", createSslHandler(sslContext));
                pipeline.addLast(new HttpsTunnelProxyChannelHandler(proxyClientBootstrap));
            }
        });

        if (configStr == null) {
            return;
        }
        String[] configArr = configStr.split(",");
        for (String item : configArr) {
            String[] itemArr = item.split("->");
            if (itemArr.length != 2) {
                continue;
            }
            portMap.put(Integer.parseInt(itemArr[0]), itemArr[1]);
            try {
                serverBootstrap.bind("0.0.0.0", Integer.parseInt(itemArr[0])).get();
                LOG.info("HTTPS通道绑定 {}->{}", itemArr[0], itemArr[1]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * socks5协议
     */
    private void initSocks5ProxyServer() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                LOG.error("exceptionCaught", cause);
                super.exceptionCaught(ctx, cause);
            }

            @Override
            public void initChannel(SocketChannel ch) {
                //Socks5MessagByteBuf
                ch.pipeline().addLast(Socks5ServerEncoder.DEFAULT);
                //sock5 init
                ch.pipeline().addLast(new Socks5InitialRequestDecoder());
                //sock5 init
                ch.pipeline().addLast(new Socks5InitialRequestHandler(configuration));
                if ("true".equals(configuration.getProperty("auth.socks5"))) {
                    ch.pipeline().addLast(new Socks5PasswordAuthRequestDecoder());
                    ch.pipeline().addLast(new Socks5PasswordAuthRequestHandler(configuration));
                }
                //socks connection
                ch.pipeline().addLast(new Socks5CommandRequestDecoder());
                //Socks connection
                ch.pipeline().addLast(new Socks5CommandRequestHandler(bossGroup));
            }
        });
        String bind = configuration.getProperty("server.socks5.bind");
        String port = configuration.getProperty("server.socks5.port");
        try {
            serverBootstrap.bind(bind, Integer.parseInt(port)).get();
            LOG.info("绑定socks5端口 {}:{}", bind, port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ChannelHandler createSslHandler(SSLContext sslContext) {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        return new SslHandler(sslEngine);
    }

    /**
     * 初始化代理服务器
     *
     * @param httpServerBootstrap
     * @param proxyClientBootstrap
     * @param bossGroup
     * @param workerGroup
     */
    private void initHttpProxyServer(ServerBootstrap httpServerBootstrap, final Bootstrap proxyClientBootstrap, NioEventLoopGroup bossGroup, NioEventLoopGroup workerGroup) {
        httpServerBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                LOG.error("exceptionCaught", cause);
                super.exceptionCaught(ctx, cause);
            }

            @Override
            public void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new HttpsCommandHandler(proxyClientBootstrap));
                pipeline.addLast("httpServerCodec", new HttpServerCodec()).addLast("httpRequestHandler", new HttpProxyRequestHandler(proxyClientBootstrap, configuration));
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
