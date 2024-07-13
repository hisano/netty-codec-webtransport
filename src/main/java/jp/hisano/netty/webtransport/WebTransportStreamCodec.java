package jp.hisano.netty.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3CodecUtils;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.http3.Http3UnknownFrame;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import java.nio.charset.StandardCharsets;

import static io.netty.incubator.codec.http3.Http3CodecUtils.numBytesForVariableLengthInteger;

public class WebTransportStreamCodec extends Http3RequestStreamInboundHandler {
	private static final int CLOSE_WEBTRANSPORT_SESSION_TYPE = 0x6843;

	@Override
	protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
		if (!frame.headers().contains(":protocol", "webtransport")) {
			Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
			headersFrame.headers().status("403");
			ctx.writeAndFlush(headersFrame).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
			return;
		}

		Http3HeadersFrame responseFrame = new DefaultHttp3HeadersFrame();
		responseFrame.headers().status("200");
		responseFrame.headers().secWebtransportHttp3Draft("draft02");
		ctx.writeAndFlush(responseFrame).addListener(future -> {
			if (future.isSuccess()) {
				new WebTransportSession((QuicStreamChannel) ctx.channel());
			}
		});
	}

	@Override
	protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
		try {
			WebTransportSession session = WebTransportSession.toSession((QuicStreamChannel) ctx.channel());
			if (session == null) {
				return;
			}

			ByteBuf in = frame.content();
			int type = in.readUnsignedShort();
			if (type != CLOSE_WEBTRANSPORT_SESSION_TYPE) {
				return;
			}
			int lengthLength = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
			long length = Http3CodecUtils.readVariableLengthInteger(in, lengthLength);
			int errorCode = 0;
			String errorMessage = "";
			switch (lengthLength) {
				case 0:
					break;
				case 4:
					errorCode = in.readInt();
					break;
				default:
					errorCode = in.readInt();
					errorMessage = new String(ByteBufUtil.getBytes(in.readBytes(in.readableBytes())), StandardCharsets.UTF_8);
					break;
			}

			session.close();

			ctx.fireChannelRead(new WebTransportSessionCloseFrame(errorCode, errorMessage));
		} finally {
			frame.release();
		}
	}

	@Override
	protected void channelRead(ChannelHandlerContext ctx, Http3UnknownFrame frame) {
		frame.release();
	}

	@Override
	protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
	}
}
