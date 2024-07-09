package jp.hisano.netty.webtransport;

import io.netty.incubator.codec.quic.QuicStreamChannel;

public final class WebTransportStream {
	private final QuicStreamChannel channel;
	private final long sessionId;
	private final long streamId;

	public WebTransportStream(QuicStreamChannel channel, long sessionId, long streamId) {
		this.channel = channel;
		this.sessionId = sessionId;
		this.streamId = streamId;
	}

	public QuicStreamChannel getChannel() {
		return channel;
	}

	public long getSessionId() {
		return sessionId;
	}

	public long getStreamId() {
		return streamId;
	}
}
