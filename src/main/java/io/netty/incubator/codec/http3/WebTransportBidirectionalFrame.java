package io.netty.incubator.codec.http3;

import io.netty.incubator.codec.quic.QuicStreamChannel;

public final class WebTransportBidirectionalFrame {
	private final QuicStreamChannel channel;
	private final long sessionId;
	private final long streamId;
	private final byte[] payload;

	WebTransportBidirectionalFrame(QuicStreamChannel channel, long sessionId, long streamId, byte[] payload) {
		this.channel = channel;
		this.sessionId = sessionId;
		this.streamId = streamId;
		this.payload = payload;
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

	public byte[] getPayload() {
		return payload;
	}
}
