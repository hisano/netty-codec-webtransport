package jp.hisano.netty.webtransport;

import io.netty.incubator.codec.http3.Http3RequestStreamFrame;

public final class WebTransportSessionCloseFrame implements Http3RequestStreamFrame {
	private static final int HTTP3_DATA_FRAME_TYPE = 0x0;

	private final int errorCode;
	private final String errorMessage;

	WebTransportSessionCloseFrame(int errorCode, String errorMessage) {
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}

	@Override
	public long type() {
		return HTTP3_DATA_FRAME_TYPE;
	}

	public int errorCode() {
		return errorCode;
	}

	public String errorMessage() {
		return errorMessage;
	}
}
