package jp.hisano.netty.webtransport;

public final class WebTransportStreamCloseFrame implements WebTransportStreamFrame {
	private final WebTransportStream stream;

	public WebTransportStreamCloseFrame(WebTransportStream stream) {
		this.stream = stream;
	}

	@Override
	public WebTransportStream stream() {
		return stream;
	}
}
