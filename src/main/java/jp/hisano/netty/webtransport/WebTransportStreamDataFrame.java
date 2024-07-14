package jp.hisano.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

import javax.annotation.Nullable;

public final class WebTransportStreamDataFrame extends DefaultByteBufHolder implements WebTransportStreamFrame {
	@Nullable
	private final WebTransportStream stream;

	public WebTransportStreamDataFrame(ByteBuf data) {
		this(null, data);
	}

	public WebTransportStreamDataFrame(WebTransportStream stream, ByteBuf data) {
		super(data);
		this.stream = stream;
	}

	@Override
	@Nullable
	public WebTransportStream stream() {
		return stream;
	}

	@Override
	public WebTransportStreamDataFrame copy() {
		return (WebTransportStreamDataFrame) super.copy();
	}

	@Override
	public WebTransportStreamDataFrame duplicate() {
		return (WebTransportStreamDataFrame) super.duplicate();
	}

	@Override
	public WebTransportStreamDataFrame retainedDuplicate() {
		return (WebTransportStreamDataFrame) super.retainedDuplicate();
	}

	@Override
	public WebTransportStreamDataFrame replace(ByteBuf content) {
		return new WebTransportStreamDataFrame(content);
	}

	@Override
	public WebTransportStreamDataFrame retain() {
		super.retain();
		return this;
	}

	@Override
	public WebTransportStreamDataFrame retain(int increment) {
		super.retain(increment);
		return this;
	}

	@Override
	public WebTransportStreamDataFrame touch() {
		super.touch();
		return this;
	}

	@Override
	public WebTransportStreamDataFrame touch(Object hint) {
		super.touch(hint);
		return this;
	}
}
