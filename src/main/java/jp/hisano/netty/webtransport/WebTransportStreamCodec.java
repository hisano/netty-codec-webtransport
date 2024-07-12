package jp.hisano.netty.webtransport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.http3.Http3UnknownFrame;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;

public class WebTransportStreamCodec extends Http3RequestStreamInboundHandler {
	private static final long SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x01L;
	private static final long SETTINGS_QPACK_BLOCKED_STREAMS = 0x07L;
	private static final long SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x08L;
	private static final long SETTINGS_H3_DATAGRAM = 0x33L;
	private static final long SETTINGS_ENABLE_WEBTRANSPORT = 0x2b60_3742L;
	private static final long SETTINGS_WEBTRANSPORT_MAX_SESSIONS = 0xc671_706aL;

	public static void prepare(QuicServerCodecBuilder quicServerCodecBuilder) {
		quicServerCodecBuilder.initialMaxData(10000000);

		quicServerCodecBuilder.initialMaxStreamDataBidirectionalLocal(1000000);
		quicServerCodecBuilder.initialMaxStreamDataBidirectionalRemote(1000000);
		quicServerCodecBuilder.initialMaxStreamDataUnidirectional(1000000);
		quicServerCodecBuilder.initialMaxStreamsBidirectional(100);
		quicServerCodecBuilder.initialMaxStreamsUnidirectional(100);
	}

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
		ctx.writeAndFlush(responseFrame);
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
