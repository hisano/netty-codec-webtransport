package jp.hisano.netty.webtransport;

import io.netty.incubator.codec.http3.Http3ControlStreamFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamFrame;

import javax.annotation.Nullable;

public interface WebTransportStreamFrame extends Http3ControlStreamFrame, Http3RequestStreamFrame, WebTransportFrame {
	@Nullable
	WebTransportStream stream();

	default long streamId() {
		return stream().streamId();
	}
}
