package jp.hisano.netty.webtransport;

import com.google.common.io.Resources;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.DefaultHttp3DataFrame;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebTransportTest {
	@ParameterizedTest
	@EnumSource(TestType.class)
	public void testPackets(TestType testType) throws Exception {
		BlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
		BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();

		SelfSignedCertificate selfSignedCertificate = CertificateUtils.createSelfSignedCertificateForLocalHost();

		NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
		try {
			startServer(serverMessages, selfSignedCertificate, testType, eventLoopGroup);
			startClient(clientMessages, selfSignedCertificate);

			if (testType == TestType.UNIDIRECTIONAL || testType == TestType.BIDIRECTIONAL) {
				assertEquals("stream opened", serverMessages.poll(10, TimeUnit.SECONDS));
			}
			assertEquals("packet received from client: abc", serverMessages.poll(10, TimeUnit.SECONDS));
			assertEquals("packet received from client: def", serverMessages.poll(10, TimeUnit.SECONDS));
			if (testType != TestType.UNIDIRECTIONAL) {
				assertEquals("packet received from server: abc", clientMessages.poll(10, TimeUnit.SECONDS));
				assertEquals("packet received from server: def", clientMessages.poll(10, TimeUnit.SECONDS));
			}
			if (testType == TestType.UNIDIRECTIONAL || testType == TestType.BIDIRECTIONAL) {
				assertEquals("stream closed", serverMessages.poll(10, TimeUnit.SECONDS));
			}
			assertEquals("session closed: errorCode = 9999, errorMessage = unknown", serverMessages.poll(10, TimeUnit.SECONDS));
		} finally {
			eventLoopGroup.shutdownGracefully();
		}
	}

	private static void startClient(BlockingQueue<String> messages, SelfSignedCertificate selfSignedCertificate) throws InterruptedException {
		CountDownLatch waiter = new CountDownLatch(1);
		try (Playwright playwright = Playwright.create()) {
			BrowserType browserType = playwright.chromium();
			Browser browser = browserType.launch(new BrowserType.LaunchOptions()
					.setArgs(Arrays.asList(
							"--test-type",
							"--enable-quic",
							"--quic-version=h3",
							"--origin-to-force-quic-on=localhost:4433",
							"--ignore-certificate-errors-spki-list=" + CertificateUtils.toPublicKeyHashAsBase64(selfSignedCertificate)
					)));

			Page page = browser.newPage();
			page.onConsoleMessage(message -> {
				String text = message.text();
				System.out.println("[DevTools Console] " + text);

				if (text.startsWith("Data received: ")) {
					String payload = text.substring("Data received: ".length());
					messages.add("packet received from server: " + payload);
				}
				if ("Transport closed.".equals(message.text())) {
					waiter.countDown();
				}
			});
			page.navigate("https://localhost:4433/");
			page.textContent("*").contains("LOADED");
			waiter.await(10, TimeUnit.SECONDS);
		}
	}

	private static void startServer(BlockingQueue<String> messages, SelfSignedCertificate selfSignedCertificate, TestType testType, EventLoopGroup eventLoopGroup) throws InterruptedException {
		QuicSslContext sslContext = QuicSslContextBuilder
				.forServer(selfSignedCertificate.key(), null, selfSignedCertificate.cert())
				.applicationProtocols(Http3.supportedApplicationProtocols()).build();

		ChannelHandler codec = WebTransport.newQuicServerCodecBuilder()
				.sslContext(sslContext)
				.tokenHandler(InsecureQuicTokenHandler.INSTANCE)
				.handler(new ChannelInitializer<QuicChannel>() {
					@Override
					protected void initChannel(QuicChannel ch) {
						System.out.println("Connection created: id = " + ch.id());

						// For datagrams
						ch.pipeline().addLast(new WebTransportDatagramCodec());
						ch.pipeline().addLast(createEchoHandler(messages, testType));

						// For streams
						ChannelHandler streamChannelInitializer = new ChannelInitializer<QuicStreamChannel>() {
							@Override
							protected void initChannel(QuicStreamChannel ch) {
								ch.pipeline().addLast(new WebTransportStreamCodec() {
									@Override
									protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
										if ("/webtransport".equals(frame.headers().path().toString())) {
											super.channelRead(ctx, frame);
										} else {
											HttpUtils.sendHtmlContent(ctx, "index.html", selfSignedCertificate, fileContent -> fileContent.replace("$TEST_TYPE", "" + testType));
										}
									}
								});
								ch.pipeline().addLast(createEchoHandler(messages, testType));
								ch.pipeline().addLast(createCloseHandler(messages));
							}
						};
						ch.pipeline().addLast(new Http3ServerConnectionHandler(streamChannelInitializer, null, null, WebTransport.createSettingsFrame(), true));
					}
				}).build();

		Bootstrap bs = new Bootstrap();
		bs.group(eventLoopGroup);
		bs.channel(NioDatagramChannel.class);
		bs.handler(codec);
		bs.bind(new InetSocketAddress(4433)).sync();
	}

	private static SimpleChannelInboundHandler<WebTransportFrame> createEchoHandler(BlockingQueue<String> messages, TestType testType) {
		return new SimpleChannelInboundHandler<WebTransportFrame>() {
			@Override
			protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebTransportFrame frame) throws Exception {
				if (frame instanceof WebTransportDatagramFrame) {
					WebTransportDatagramFrame datagramFrame = (WebTransportDatagramFrame) frame;
					byte[] payload = ByteBufUtil.getBytes(datagramFrame.content());

					System.out.println("WebTransport packet received: payload = " + Arrays.toString(payload));

					messages.add("packet received from client: " + new String(payload, StandardCharsets.UTF_8));

					channelHandlerContext.writeAndFlush(new WebTransportDatagramFrame(datagramFrame.session(), Unpooled.wrappedBuffer(payload))).addListener(futue -> {
						if (futue.isSuccess()) {
							System.out.println("WebTransport stream packet sent: payload = " + Arrays.toString(payload));
						} else {
							System.out.println("Sending WebTransport stream packet failed: payload = " + Arrays.toString(payload));
							futue.cause().printStackTrace();
						}
					});
				} else if (frame instanceof WebTransportStreamOpenFrame) {
					System.out.println("WebTransport stream opened");
					messages.add("stream opened");
				} else if (frame instanceof WebTransportStreamDataFrame) {
					WebTransportStreamDataFrame streamDataFrame = (WebTransportStreamDataFrame) frame;
					byte[] payload = ByteBufUtil.getBytes(streamDataFrame.content());

					System.out.println("WebTransport packet received: payload = " + Arrays.toString(payload));

					messages.add("packet received from client: " + new String(payload, StandardCharsets.UTF_8));

					if (testType == TestType.UNIDIRECTIONAL) {
						return;
					}

					channelHandlerContext.writeAndFlush(new WebTransportStreamDataFrame(streamDataFrame.stream(), Unpooled.wrappedBuffer(payload))).addListener(futue -> {
						if (futue.isSuccess()) {
							System.out.println("WebTransport stream packet sent: payload = " + Arrays.toString(payload));
						} else {
							System.out.println("Sending WebTransport stream packet failed: payload = " + Arrays.toString(payload));
							futue.cause().printStackTrace();
						}
					});
				} else if (frame instanceof WebTransportStreamCloseFrame) {
					System.out.println("WebTransport stream closed");
					messages.add("stream closed");
				}
			}
		};
	}

	private static SimpleChannelInboundHandler<WebTransportSessionCloseFrame> createCloseHandler(BlockingQueue<String> messages) {
		return new SimpleChannelInboundHandler<WebTransportSessionCloseFrame>() {
			@Override
			protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebTransportSessionCloseFrame frame) throws Exception {
				System.out.println("WebTransport session closed: errorCode = " + frame.errorCode() + ", errorMessage = " + frame.errorMessage());

				messages.add("session closed: errorCode = " + frame.errorCode() + ", errorMessage = " + frame.errorMessage());
			}
		};
	}

	private enum TestType {
		DATAGRAM,
		UNIDIRECTIONAL,
		BIDIRECTIONAL,
	}
}
