package jp.hisano.netty.webtransport;

import com.google.common.io.Resources;
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
import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebTransportTest {
	private static final String MESSAGE = "Hello World!";
	private static final byte[] CONTENT = (MESSAGE + "\r\n").getBytes(StandardCharsets.UTF_8);
	private static final int PORT = 4433;

	@Test
	public void testWebTransport() throws Exception {
		Date now = new Date();
		Date oneDayLater = new Date(now.getTime() + 24 * 60 * 60 * 1000);
		SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate(now, oneDayLater, "EC", 256);

		NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
		try {
			startHttp3Server(selfSignedCertificate, eventLoopGroup);

			try (Playwright playwright = Playwright.create()) {
				BrowserType browserType = playwright.chromium();
				Browser browser = browserType.launch(new BrowserType.LaunchOptions()
						.setHeadless(false)
						.setArgs(Arrays.asList(
								"--test-type",
								"--enable-quic",
								"--quic-version=h3",
								"--origin-to-force-quic-on=localhost:4433",
								"--ignore-certificate-errors-spki-list=" + toPublicKeyHashAsBase64(selfSignedCertificate.cert())
						)));

				CountDownLatch waiter = new CountDownLatch(1);

				Page page = browser.newPage();
				page.onConsoleMessage(message -> {
					System.out.println(">> " + message.text());
					if ("Stream closed.".equals(message.text())) {
						waiter.countDown();
					}
				});
				page.navigate("https://localhost:4433/");
				assertTrue(page.textContent("*").contains(MESSAGE));

				waiter.await();
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
				.datagram(2048, 2048)
				.maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
				.initialMaxData(10000000)
				.initialMaxStreamDataBidirectionalLocal(1000000)
				.initialMaxStreamDataBidirectionalRemote(1000000)
				.initialMaxStreamsBidirectional(100)
				.tokenHandler(InsecureQuicTokenHandler.INSTANCE)
				.handler(new ChannelInitializer<QuicChannel>() {
					@Override
					protected void initChannel(QuicChannel ch) {
						System.out.println("Received connection " + ch);

						DefaultHttp3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
						settingsFrame.put(0x08L, 1L);
						settingsFrame.put(0x33L, 1L);
						settingsFrame.put(0x2b60_3742L, 1L);
						settingsFrame.put(0xc671_706aL, 256L);

						// Called for each connection
						ch.pipeline().addLast(new Http3ServerConnectionHandler(
								new ChannelInitializer<QuicStreamChannel>() {
									// Called for each request-stream,
									@Override
									protected void initChannel(QuicStreamChannel ch) {
										System.out.println("Received stream " + ch);
										ch.pipeline().addLast(new Http3RequestStreamInboundHandler() {

											@Override
											protected void channelRead(
													ChannelHandlerContext ctx, Http3HeadersFrame frame) {
												System.out.println("Received headers " + frame.headers());
												if (frame.headers().contains(":protocol", "webtransport")) {
													System.out.println("WebTransport accepted");
													Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
													headersFrame.headers().status("200");
													ctx.writeAndFlush(headersFrame);
												} else {
													ReferenceCountUtil.release(frame);
												}
											}

											@Override
											protected void channelRead(
													ChannelHandlerContext ctx, Http3DataFrame frame) {
												ReferenceCountUtil.release(frame);
											}

											@Override
											protected void channelInputClosed(ChannelHandlerContext ctx) {
												String content;
												try {
													content = new String(Resources.toByteArray(Resources.getResource(WebTransportTest.class, "index.html")), StandardCharsets.UTF_8);
												} catch (IOException e) {
													throw new IllegalStateException(e);
												}
												String replacedContent = content.replace("$CERTIFICATE_HASH", toPublicKeyHashAsBase64(selfSignedCertificate.cert()));
												byte[] replacedContentBytes = replacedContent.getBytes(StandardCharsets.UTF_8);

												Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
												headersFrame.headers().status("200");
												headersFrame.headers().add("server", "netty");
												headersFrame.headers().add("content-type", "text/html");
												headersFrame.headers().addInt("content-length", replacedContentBytes.length);
												ctx.write(headersFrame);
												ctx.writeAndFlush(new DefaultHttp3DataFrame(
																Unpooled.wrappedBuffer(replacedContentBytes)))
														.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
											}
										});
									}
								}, null, null, settingsFrame, true));
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
