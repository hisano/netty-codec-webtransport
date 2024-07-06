package jp.hisano.netty.webtransport;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.DefaultHttp3DataFrame;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebTransportTest {
	private static final String MESSAGE = "Hello World!";
	private static final byte[] CONTENT = (MESSAGE + "\r\n").getBytes(StandardCharsets.UTF_8);
	private static final int PORT = 4433;

	@Test
	public void testHttp3() throws Exception {
		SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
		NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
		try {
			startHttp3Server(selfSignedCertificate, eventLoopGroup);

			try (Playwright playwright = Playwright.create()) {
				BrowserType browserType = playwright.chromium(); // Chromiumを使用
				Browser browser = browserType.launch(new BrowserType.LaunchOptions()
						.setArgs(Arrays.asList(
								"--test-type",
								"--enable-quic",
								"--quic-version=h3",
								"--origin-to-force-quic-on=127.0.0.1:4433",
								"--ignore-certificate-errors-spki-list=" + toPublicKeyHashAsBase64(selfSignedCertificate.cert())
						)));

				Page page = browser.newPage();
				page.navigate("https://127.0.0.1:4433/");
				assertTrue(page.textContent("*").contains(MESSAGE));
			}
		} finally {
			eventLoopGroup.shutdownGracefully();
		}
	}

	private static void startHttp3Server(SelfSignedCertificate selfSignedCertificate, EventLoopGroup eventLoopGroup) throws InterruptedException {
		QuicSslContext sslContext = QuicSslContextBuilder.forServer(selfSignedCertificate.key(), null, selfSignedCertificate.cert())
				.applicationProtocols(Http3.supportedApplicationProtocols()).build();
		ChannelHandler codec = Http3.newQuicServerCodecBuilder()
				.sslContext(sslContext)
				.maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
				.initialMaxData(10000000)
				.initialMaxStreamDataBidirectionalLocal(1000000)
				.initialMaxStreamDataBidirectionalRemote(1000000)
				.initialMaxStreamsBidirectional(100)
				.tokenHandler(InsecureQuicTokenHandler.INSTANCE)
				.handler(new ChannelInitializer<QuicChannel>() {
					@Override
					protected void initChannel(QuicChannel ch) {
						// Called for each connection
						ch.pipeline().addLast(new Http3ServerConnectionHandler(
								new ChannelInitializer<QuicStreamChannel>() {
									// Called for each request-stream,
									@Override
									protected void initChannel(QuicStreamChannel ch) {
										ch.pipeline().addLast(new Http3RequestStreamInboundHandler() {

											@Override
											protected void channelRead(
													ChannelHandlerContext ctx, Http3HeadersFrame frame) {
												ReferenceCountUtil.release(frame);
											}

											@Override
											protected void channelRead(
													ChannelHandlerContext ctx, Http3DataFrame frame) {
												ReferenceCountUtil.release(frame);
											}

											@Override
											protected void channelInputClosed(ChannelHandlerContext ctx) {
												Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
												headersFrame.headers().status("200");
												headersFrame.headers().add("server", "netty");
												headersFrame.headers().add("content-type", "text/html");
												headersFrame.headers().addInt("content-length", CONTENT.length);
												ctx.write(headersFrame);
												ctx.writeAndFlush(new DefaultHttp3DataFrame(
																Unpooled.wrappedBuffer(CONTENT)))
														.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
											}
										});
									}
								}));
					}
				}).build();
		Bootstrap bs = new Bootstrap();
		Channel channel = bs.group(eventLoopGroup)
				.channel(NioDatagramChannel.class)
				.handler(codec)
				.bind(new InetSocketAddress(PORT)).sync().channel();
	}

	private static String toPublicKeyHashAsBase64(X509Certificate certificate) {
		PublicKey publicKey = certificate.getPublicKey();

		byte[] publicKeyAsDer = publicKey.getEncoded();

		byte[] publicKeyHash;
		try {
			publicKeyHash = MessageDigest.getInstance("SHA-256").digest(publicKeyAsDer);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}

		return Base64.getEncoder().encodeToString(publicKeyHash);
	}
}
