package jp.hisano.netty.webtransport;

import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;

public final class WebTransport {
	private static final long SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x01L;
	private static final long SETTINGS_QPACK_BLOCKED_STREAMS = 0x07L;
	private static final long SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x08L;
	private static final long SETTINGS_H3_DATAGRAM = 0x33L;
	private static final long SETTINGS_ENABLE_WEBTRANSPORT = 0x2b60_3742L;
	private static final long SETTINGS_WEBTRANSPORT_MAX_SESSIONS = 0xc671_706aL;

	public static QuicServerCodecBuilder newQuicServerCodecBuilder() {
		QuicServerCodecBuilder quicServerCodecBuilder = Http3.newQuicServerCodecBuilder();

		quicServerCodecBuilder.initialMaxData(10000000);

		quicServerCodecBuilder.initialMaxStreamDataBidirectionalLocal(1000000);
		quicServerCodecBuilder.initialMaxStreamDataBidirectionalRemote(1000000);
		quicServerCodecBuilder.initialMaxStreamDataUnidirectional(1000000);
		quicServerCodecBuilder.initialMaxStreamsBidirectional(100);
		quicServerCodecBuilder.initialMaxStreamsUnidirectional(100);

		quicServerCodecBuilder.datagram(1000, 1000);

		return quicServerCodecBuilder;
	}

	public static Http3SettingsFrame createSettingsFrame() {
		DefaultHttp3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
		settingsFrame.put(SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0L);
		settingsFrame.put(SETTINGS_QPACK_BLOCKED_STREAMS, 0L);
		settingsFrame.put(SETTINGS_ENABLE_CONNECT_PROTOCOL, 1L);
		settingsFrame.put(SETTINGS_H3_DATAGRAM, 1L);
		settingsFrame.put(SETTINGS_ENABLE_WEBTRANSPORT, 1L);
		settingsFrame.put(SETTINGS_WEBTRANSPORT_MAX_SESSIONS, 256L);
		return settingsFrame;
	}

	private WebTransport() {
	}
}
