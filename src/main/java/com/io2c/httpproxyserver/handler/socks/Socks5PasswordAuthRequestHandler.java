package com.io2c.httpproxyserver.handler.socks;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Properties;

public class Socks5PasswordAuthRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5PasswordAuthRequest> {

    private static final Logger logger = LoggerFactory.getLogger(Socks5PasswordAuthRequestHandler.class);

    private Properties configuration;

    public Socks5PasswordAuthRequestHandler(Properties configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5PasswordAuthRequest msg) {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
        logger.debug("端口 {}, 用户名 {}, 密码 {}", sa.getPort(), msg.username(), msg.password());
        String password = configuration.getProperty("auth." + msg.username());
        if (password != null && password.equals(msg.password())) {
            Socks5PasswordAuthResponse passwordAuthResponse = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS);
            ctx.writeAndFlush(passwordAuthResponse);
        } else {
            Socks5PasswordAuthResponse passwordAuthResponse = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE);
            //发送鉴权失败消息，完成后关闭channel
            ctx.writeAndFlush(passwordAuthResponse).addListener(ChannelFutureListener.CLOSE);
        }
    }

}
