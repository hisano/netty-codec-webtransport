package jp.hisano.netty.webtransport;

import com.google.common.io.Resources;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.DefaultHttp3DataFrame;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;

final class HttpUtils {
	static void sendHtmlContent(ChannelHandlerContext ctx, String fileName, SelfSignedCertificate selfSignedCertificate) {
		sendHtmlContent(ctx, fileName, selfSignedCertificate, UnaryOperator.identity());
	}

	static void sendHtmlContent(ChannelHandlerContext ctx, String fileName, SelfSignedCertificate selfSignedCertificate, UnaryOperator<String> fileContentConverter) {
		String fileContent;
		try {
			fileContent = new String(Resources.toByteArray(Resources.getResource(HttpUtils.class, fileName)), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		String replacedContent = fileContentConverter.apply(fileContent)
				.replace("$CERTIFICATE_HASH", CertificateUtils.toPublicKeyHashAsBase64(selfSignedCertificate));
		byte[] replacedContentBytes = replacedContent.getBytes(StandardCharsets.UTF_8);

		Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
		headersFrame.headers().status("200");
		headersFrame.headers().add("server", "netty");
		headersFrame.headers().add("content-type", "text/html");
		headersFrame.headers().addInt("content-length", replacedContentBytes.length);
		ctx.write(headersFrame);

		ctx.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(replacedContentBytes))).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
	}

	private HttpUtils() {
	}
}
