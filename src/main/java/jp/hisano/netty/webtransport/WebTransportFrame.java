package jp.hisano.netty.webtransport;

import io.netty.incubator.codec.http3.Http3ControlStreamFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamFrame;

public interface WebTransportFrame extends Http3ControlStreamFrame, Http3RequestStreamFrame {
	@Override
	default long type() {
		return 0x54;
	}
}
