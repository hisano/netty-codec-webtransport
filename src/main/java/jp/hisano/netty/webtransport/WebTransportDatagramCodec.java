package jp.hisano.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.util.ReferenceCountUtil;

import static io.netty.incubator.codec.http3.Http3CodecUtils.numBytesForVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.readVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.writeVariableLengthInteger;

public final class WebTransportDatagramCodec extends ChannelDuplexHandler {
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof ByteBuf) {
			ByteBuf in = (ByteBuf) msg;

			int sessionIdLength = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
			long streamId = readVariableLengthInteger(in, sessionIdLength);

			try {
				ctx.fireChannelRead(new WebTransportStreamFrame(streamId, in.readRetainedSlice(in.readableBytes())));
			} finally {
				ReferenceCountUtil.release(msg);
			}
		} else {
			super.channelRead(ctx, msg);
		}
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof WebTransportStreamFrame) {
			WebTransportStreamFrame out = (WebTransportStreamFrame) msg;

			ByteBuf encoded = ctx.alloc().buffer(out.content().readableBytes() + 8);
			writeVariableLengthInteger(encoded, out.streamId());
			encoded.writeBytes(out.content());

			try {
				ctx.write(encoded, promise);
			} finally {
				ReferenceCountUtil.release(out);
			}
		} else {
			super.write(ctx, msg, promise);
		}
	}
}