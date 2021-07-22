package com.io2c.httpproxyserver.handler.socks;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {
    EventLoopGroup bossGroup;

    private static final Logger logger = LoggerFactory.getLogger(Socks5CommandRequestHandler.class);

    public Socks5CommandRequestHandler(EventLoopGroup bossGroup) {
        this.bossGroup = bossGroup;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext clientChannelContext, DefaultSocks5CommandRequest msg) {

        InetSocketAddress sa = (InetSocketAddress) clientChannelContext.channel().localAddress();
        logger.debug("端口 {}, 消息类型 {}, 目标服务器 {}, 端口 {}", sa.getPort(), msg.type(), msg.dstAddr(), msg.dstPort());

        if (msg.type().equals(Socks5CommandType.CONNECT)) {
            logger.trace("准备连接目标服务器");
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(bossGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            //将目标服务器信息转发给客户端
                            ch.pipeline().addLast(new Dest2ClientHandler(clientChannelContext));
                        }
                    });

            ChannelFuture future = bootstrap.connect(msg.dstAddr(), msg.dstPort());
            future.addListener(new ChannelFutureListener() {

                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        if (!clientChannelContext.channel().isActive()) {
                            future.channel().close();
                            return;
                        }
                        logger.trace("成功连接目标服务器");
                        clientChannelContext.pipeline().addLast(new Client2DestHandler(future));
                        Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4);
                        clientChannelContext.writeAndFlush(commandResponse);
                    } else {
                        Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4);
                        clientChannelContext.writeAndFlush(commandResponse);
                    }
                }

            });
        } else {
            clientChannelContext.fireChannelRead(msg);
        }
    }

    /**
     * 将目标服务器信息转发给客户端
     */
    private static class Dest2ClientHandler extends ChannelInboundHandlerAdapter {

        private ChannelHandlerContext clientChannelContext;

        public Dest2ClientHandler(ChannelHandlerContext clientChannelContext) {
            this.clientChannelContext = clientChannelContext;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx2, Object destMsg) {
            logger.trace("将目标服务器信息转发给客户端");
            clientChannelContext.writeAndFlush(destMsg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx2) throws Exception {
            logger.trace("目标服务器断开连接");
            clientChannelContext.channel().close();
            super.channelInactive(ctx2);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            clientChannelContext.channel().config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
            super.channelWritabilityChanged(ctx);
        }
    }

    /**
     * 将客户端的消息转发给目标服务器端
     */
    private static class Client2DestHandler extends ChannelInboundHandlerAdapter {

        private ChannelFuture destChannelFuture;

        public Client2DestHandler(ChannelFuture destChannelFuture) {
            this.destChannelFuture = destChannelFuture;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            logger.trace("将客户端的消息转发给目标服务器端");
            destChannelFuture.channel().writeAndFlush(msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            logger.trace("客户端断开连接");
            destChannelFuture.channel().close();
            super.channelInactive(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            destChannelFuture.channel().config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
            super.channelWritabilityChanged(ctx);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}
