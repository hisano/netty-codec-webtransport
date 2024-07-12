package jp.hisano.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.incubator.codec.http3.Http3ControlStreamFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamFrame;

public final class WebTransportStreamFrame extends DefaultByteBufHolder implements Http3ControlStreamFrame, Http3RequestStreamFrame {
	private final long streamId;

	public WebTransportStreamFrame(ByteBuf data) {
		this(-1, data);
	}

	public WebTransportStreamFrame(long streamId, ByteBuf data) {
		super(data);
		this.streamId = streamId;
	}

	public long streamId() {
		return streamId;
	}

	@Override
	public long type() {
		return 0x54;
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
