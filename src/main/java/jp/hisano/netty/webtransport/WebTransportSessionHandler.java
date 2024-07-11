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
	private static final long SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x01L;
	private static final long SETTINGS_QPACK_BLOCKED_STREAMS = 0x07L;
	private static final long SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x08L;
	private static final long SETTINGS_H3_DATAGRAM = 0x33L;
	private static final long SETTINGS_ENABLE_WEBTRANSPORT = 0x2b60_3742L;
	private static final long SETTINGS_WEBTRANSPORT_MAX_SESSIONS = 0xc671_706aL;

	public static Http3SettingsFrame createSettingsFrameFromServer() {
		DefaultHttp3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
		settingsFrame.put(SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0L);
		settingsFrame.put(SETTINGS_QPACK_BLOCKED_STREAMS, 0L);
		settingsFrame.put(SETTINGS_ENABLE_CONNECT_PROTOCOL, 1L);
		settingsFrame.put(SETTINGS_H3_DATAGRAM, 1L);
		settingsFrame.put(SETTINGS_ENABLE_WEBTRANSPORT, 1L);
		settingsFrame.put(SETTINGS_WEBTRANSPORT_MAX_SESSIONS, 256L);
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
		responseFrame.headers().status("200");
		responseFrame.headers().secWebtransportHttp3Draft("draft02");
		ctx.writeAndFlush(responseFrame);

		ReferenceCountUtil.release(msg);
	}
}
