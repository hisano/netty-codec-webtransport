package jp.hisano.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.incubator.codec.http3.Http3ControlStreamFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamFrame;

public final class WebTransportDatagramFrame extends DefaultByteBufHolder implements Http3ControlStreamFrame, Http3RequestStreamFrame, WebTransportFrame {
	private final WebTransportSession session;

	public WebTransportDatagramFrame(WebTransportSession session, ByteBuf data) {
		super(data);
		this.session = session;
	}

	public WebTransportSession session() {
		return session;
	}

	@Override
	public WebTransportDatagramFrame copy() {
		return (WebTransportDatagramFrame) super.copy();
	}

	@Override
	public WebTransportDatagramFrame duplicate() {
		return (WebTransportDatagramFrame) super.duplicate();
	}

	@Override
	public WebTransportDatagramFrame retainedDuplicate() {
		return (WebTransportDatagramFrame) super.retainedDuplicate();
	}

	@Override
	public WebTransportDatagramFrame replace(ByteBuf content) {
		return new WebTransportDatagramFrame(session, content);
	}

	@Override
	public WebTransportDatagramFrame retain() {
		super.retain();
		return this;
	}

	@Override
	public WebTransportDatagramFrame retain(int increment) {
		super.retain(increment);
		return this;
	}

	@Override
	public WebTransportDatagramFrame touch() {
		super.touch();
		return this;
	}

	@Override
	public WebTransportDatagramFrame touch(Object hint) {
		super.touch(hint);
		return this;
	}
}
