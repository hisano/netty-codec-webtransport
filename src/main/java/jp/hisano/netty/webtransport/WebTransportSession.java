package jp.hisano.netty.webtransport;

import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;

public final class WebTransportSession {
	private static final AttributeKey<WebTransportSession> SESSION_KEY = AttributeKey.valueOf(WebTransport.class, "SESSION");

	public static WebTransportSession toSession(QuicChannel channel) {
		return channel.attr(SESSION_KEY).get();
	}

	private final QuicChannel parentChannel;
	private final QuicStreamChannel parentStreamChannel;

	WebTransportSession(QuicStreamChannel parentStreamChannel) {
		this.parentChannel = parentStreamChannel.parent();
		this.parentStreamChannel = parentStreamChannel;

		parentChannel.attr(SESSION_KEY).set(this);

		parentStreamChannel.closeFuture().addListener(future -> close());
	}

	public long id() {
		return parentStreamChannel.streamId();
	}

	private void close() {
		parentChannel.attr(SESSION_KEY).set(null);
	}
}
