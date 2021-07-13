//package com.io2c.httpproxyserver.handler.socks;
//
//import io.netty.channel.ChannelFutureListener;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.SimpleChannelInboundHandler;
//import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
//import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
//import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse;
//import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
//import org.fengfei.lanproxy.server.ProxyInfoManager;
//import org.fengfei.lanproxy.server.config.web.ProxyInfoVo;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.InetSocketAddress;
//
//public class Socks5PasswordAuthRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5PasswordAuthRequest> {
//
//    private static final Logger logger = LoggerFactory.getLogger(Socks5PasswordAuthRequestHandler.class);
//
//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5PasswordAuthRequest msg) {
//        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
//        ProxyInfoVo proxyInfoVo = ProxyInfoManager.getHttpProxyInfoVo(sa.getPort());
//        if (proxyInfoVo == null) {
//            ctx.channel().close();
//            return;
//        }
//        logger.debug("端口 {}, 用户名 {}, 密码 {}", sa.getPort(), msg.username(), msg.password());
//        if (proxyInfoVo.getProxyUsername().equals(msg.username()) && proxyInfoVo.getProxyPassword().equals(msg.password())) {
//            Socks5PasswordAuthResponse passwordAuthResponse = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS);
//            ctx.writeAndFlush(passwordAuthResponse);
//        } else {
//            Socks5PasswordAuthResponse passwordAuthResponse = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE);
//            //发送鉴权失败消息，完成后关闭channel
//            ctx.writeAndFlush(passwordAuthResponse).addListener(ChannelFutureListener.CLOSE);
//        }
//    }
//
//}
