package jp.hisano.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

public final class WebTransportStreamFrame extends DefaultByteBufHolder {
	public WebTransportStreamFrame(ByteBuf data) {
		super(data);
	}

	@Override
	public WebTransportStreamFrame copy() {
		return (WebTransportStreamFrame) super.copy();
	}

	@Override
	public WebTransportStreamFrame duplicate() {
		return (WebTransportStreamFrame) super.duplicate();
	}

	@Override
	public WebTransportStreamFrame retainedDuplicate() {
		return (WebTransportStreamFrame) super.retainedDuplicate();
	}

	@Override
	public WebTransportStreamFrame replace(ByteBuf content) {
		return new WebTransportStreamFrame(content);
	}

	@Override
	public WebTransportStreamFrame retain() {
		super.retain();
		return this;
	}

	@Override
	public WebTransportStreamFrame retain(int increment) {
		super.retain(increment);
		return this;
	}

	@Override
	public WebTransportStreamFrame touch() {
		super.touch();
		return this;
	}

	@Override
	public WebTransportStreamFrame touch(Object hint) {
		super.touch(hint);
		return this;
	}
}
