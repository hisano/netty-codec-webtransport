package jp.hisano.netty.webtransport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.http3.Http3UnknownFrame;
import io.netty.incubator.codec.quic.QuicStreamChannel;

public class WebTransportStreamCodec extends Http3RequestStreamInboundHandler {
	@Override
	protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
		if (!frame.headers().contains(":protocol", "webtransport")) {
			Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
			headersFrame.headers().status("403");
			ctx.writeAndFlush(headersFrame).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
			return;
		}

		Http3HeadersFrame responseFrame = new DefaultHttp3HeadersFrame();
		responseFrame.headers().status("200");
		responseFrame.headers().secWebtransportHttp3Draft("draft02");
		ctx.writeAndFlush(responseFrame).addListener(future -> {
			if (future.isSuccess()) {
				new WebTransportSession((QuicStreamChannel) ctx.channel());
			}
		});
	}

	@Override
	protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
		frame.release();
	}

	@Override
	protected void channelRead(ChannelHandlerContext ctx, Http3UnknownFrame frame) {
		frame.release();
	}

	@Override
	protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
	}
}
