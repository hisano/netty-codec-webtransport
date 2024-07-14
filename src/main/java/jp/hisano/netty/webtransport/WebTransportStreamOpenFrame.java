package jp.hisano.netty.webtransport;

public final class WebTransportStreamOpenFrame implements WebTransportStreamFrame {
	private final WebTransportStream stream;

	public WebTransportStreamOpenFrame(WebTransportStream stream) {
		this.stream = stream;
	}

	@Override
	public WebTransportStream stream() {
		return stream;
	}
}
