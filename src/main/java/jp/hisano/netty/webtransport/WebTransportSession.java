package jp.hisano.netty.webtransport;

import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WebTransportSession {
	private static final AttributeKey<WebTransportSession> SESSION_KEY = AttributeKey.valueOf(WebTransport.class, "SESSION");

	public static WebTransportSession toSession(QuicChannel channel) {
		return channel.attr(SESSION_KEY).get();
	}

	public static WebTransportSession toSession(QuicStreamChannel channel) {
		return channel.parent().attr(SESSION_KEY).get();
	}

	public static WebTransportStream createAndAddStream(long sessionId, QuicStreamChannel streamChannel) {
		WebTransportSession session = WebTransportSession.toSession(streamChannel);
		if (session == null || session.sessionId() != sessionId) {
			throw new IllegalStateException();
		}

		WebTransportStream stream = new WebTransportStream(session, streamChannel);
		session.addStream(stream);
		return stream;
	}

	private final QuicStreamChannel connectStreamChannel;

	private final List<WebTransportStream> streams = new CopyOnWriteArrayList<>();

	WebTransportSession(QuicStreamChannel connectStreamChannel) {
		this.connectStreamChannel = connectStreamChannel;

		channel().attr(SESSION_KEY).set(this);
		channel().closeFuture().addListener(future -> channel().attr(SESSION_KEY).set(null));
	}

	private QuicChannel channel() {
		return connectStreamChannel.parent();
	}

	public long sessionId() {
		return connectStreamChannel.streamId();
	}

	public void addStream(WebTransportStream stream) {
		streams.add(stream);
	}

	void close() {
		channel().close();
	}
}
