package com.io2c.httpproxyserver.handler.socks;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Properties;

public class Socks5InitialRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5InitialRequest> {

    private static final Logger logger = LoggerFactory.getLogger(Socks5InitialRequestHandler.class);

    private Properties configuration;

    public Socks5InitialRequestHandler(Properties configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5InitialRequest msg) {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
        logger.debug("端口 {} 初始化ss5连接 {}", sa.getPort(), msg);
        if (msg.decoderResult().isFailure()) {
            logger.debug("不是ss5协议");
            ctx.fireChannelRead(msg);
        } else {
            if (msg.version().equals(SocksVersion.SOCKS5)) {
                if ("true".equals(configuration.getProperty("auth.socks5"))) {
                    Socks5InitialResponse initialResponse = new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD);
                    ctx.writeAndFlush(initialResponse);
                } else {
                    Socks5InitialResponse initialResponse = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
                    ctx.writeAndFlush(initialResponse);
                }
            }
        }
    }

}
