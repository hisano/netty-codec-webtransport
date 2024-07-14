package jp.hisano.netty.webtransport;

import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;

public final class WebTransportStream {
	private static final AttributeKey<WebTransportStream> STREAM_KEY = AttributeKey.valueOf(WebTransport.class, "STREAM");

	public static WebTransportStream toStream(QuicStreamChannel streamChannel) {
		return streamChannel.attr(STREAM_KEY).get();
	}

	private final WebTransportSession session;
	private final QuicStreamChannel streamChannel;

	public WebTransportStream(WebTransportSession session, QuicStreamChannel streamChannel) {
		this.session = session;
		this.streamChannel = streamChannel;

		streamChannel().attr(STREAM_KEY).set(this);
		streamChannel().closeFuture().addListener(future -> streamChannel().attr(STREAM_KEY).set(null));
	}

	public WebTransportSession session() {
		return session;
	}

	public QuicStreamChannel streamChannel() {
		return streamChannel;
	}

	public long streamId() {
		return streamChannel.streamId();
	}

	void close() {
		streamChannel().shutdownOutput().addListener(future -> streamChannel().close());
	}
}
