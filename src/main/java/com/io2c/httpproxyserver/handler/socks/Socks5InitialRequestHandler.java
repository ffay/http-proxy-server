//package com.io2c.httpproxyserver.handler.socks;
//
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.SimpleChannelInboundHandler;
//import io.netty.handler.codec.socksx.SocksVersion;
//import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
//import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
//import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
//import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
//import org.fengfei.lanproxy.server.ProxyInfoManager;
//import org.fengfei.lanproxy.server.config.web.ProxyInfoVo;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.InetSocketAddress;
//
//public class Socks5InitialRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5InitialRequest> {
//
//    private static final Logger logger = LoggerFactory.getLogger(Socks5InitialRequestHandler.class);
//
//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5InitialRequest msg) {
//        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
//        ProxyInfoVo proxyInfoVo = ProxyInfoManager.getHttpProxyInfoVo(sa.getPort());
//        if (proxyInfoVo == null) {
//            ctx.channel().close();
//            return;
//        }
//        logger.debug("端口 {} 初始化ss5连接 {}", sa.getPort(), msg);
//        if (msg.decoderResult().isFailure()) {
//            logger.debug("不是ss5协议");
//            ctx.fireChannelRead(msg);
//        } else {
//            if (msg.version().equals(SocksVersion.SOCKS5)) {
//                if (proxyInfoVo.getProxyUsername() != null && proxyInfoVo.getProxyUsername().length() > 0) {
//                    Socks5InitialResponse initialResponse = new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD);
//                    ctx.writeAndFlush(initialResponse);
//                } else {
//                    Socks5InitialResponse initialResponse = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
//                    ctx.writeAndFlush(initialResponse);
//                }
//            }
//        }
//    }
//
//}
