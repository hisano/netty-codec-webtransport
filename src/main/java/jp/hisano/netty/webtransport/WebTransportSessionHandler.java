package jp.hisano.netty.webtransport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;

public final class WebTransportSessionHandler extends ChannelInboundHandlerAdapter {
	public static Http3SettingsFrame createSettingsFrameFromServer() {
		DefaultHttp3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
		settingsFrame.put(0x08L, 1L);
		settingsFrame.put(0x33L, 1L);
		settingsFrame.put(0x2b60_3742L, 1L);
		settingsFrame.put(0xc671_706aL, 256L);
		return settingsFrame;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (!(msg instanceof Http3HeadersFrame)) {
			super.channelRead(ctx, msg);
			return;
		}

		Http3HeadersFrame requestFrame = (Http3HeadersFrame) msg;
		if (!requestFrame.headers().contains(":protocol", "webtransport")) {
			super.channelRead(ctx, requestFrame);
			return;
		}

		System.out.println("WebTransport stream created: streamId = " + ((QuicStreamChannel) ctx.channel()).streamId());

		Http3HeadersFrame responseFrame = new DefaultHttp3HeadersFrame();
		responseFrame.headers().secWebtransportHttp3Draft("draft02");
		responseFrame.headers().status("200");
		ctx.writeAndFlush(responseFrame);

		ReferenceCountUtil.release(msg);
	}
}
