package jp.hisano.netty.webtransport;

import com.google.common.io.Resources;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
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
import io.netty.incubator.codec.http3.Http3UnknownFrame;
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
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.netty.incubator.codec.http3.Http3CodecUtils.numBytesForVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.readVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.writeVariableLengthInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebTransportTest {
	@Test
	public void testDatagram() throws Exception {
		BlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
		BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();

		SelfSignedCertificate selfSignedCertificate = createSelfSignedCertificateForLocalHost();

		NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
		try {
			startServer(serverMessages, selfSignedCertificate, TestType.DATAGRAM, eventLoopGroup);
			startClient(clientMessages, selfSignedCertificate);

			assertEquals("packet received from client: abc", serverMessages.poll());
			assertEquals("packet received from server: abc", clientMessages.poll());
			assertEquals("packet received from client: def", serverMessages.poll());
			assertEquals("packet received from server: def", clientMessages.poll());
			assertEquals("stream closed", clientMessages.poll());
		} finally {
			eventLoopGroup.shutdownGracefully();
		}
	}

	@Test
	public void testUnidirectionalStream() throws Exception {
		BlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
		BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();

		SelfSignedCertificate selfSignedCertificate = createSelfSignedCertificateForLocalHost();

		NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
		try {
			startServer(serverMessages, selfSignedCertificate, TestType.UNIDIRECTIONAL, eventLoopGroup);
			startClient(clientMessages, selfSignedCertificate);

			assertEquals("packet received from client: abc", serverMessages.poll());
			assertEquals("packet received from client: def", serverMessages.poll());
			assertEquals("stream closed", clientMessages.poll());
		} finally {
			eventLoopGroup.shutdownGracefully();
		}
	}

	@Test
	public void testBidirectionalStream() throws Exception {
		BlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
		BlockingQueue<String> clientMessages = new LinkedBlockingQueue<>();

		SelfSignedCertificate selfSignedCertificate = createSelfSignedCertificateForLocalHost();

		NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
		try {
			startServer(serverMessages, selfSignedCertificate, TestType.BIDIRECTIONAL, eventLoopGroup);
			startClient(clientMessages, selfSignedCertificate);

			assertEquals("packet received from client: abc", serverMessages.poll());
			assertEquals("packet received from server: abc", clientMessages.poll());
			assertEquals("packet received from client: def", serverMessages.poll());
			assertEquals("packet received from server: def", clientMessages.poll());
			assertEquals("stream closed", clientMessages.poll());
		} finally {
			eventLoopGroup.shutdownGracefully();
		}
	}

	private static void startClient(BlockingQueue<String> messages, SelfSignedCertificate selfSignedCertificate) throws InterruptedException {
		CountDownLatch waiter = new CountDownLatch(1);
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

			Page page = browser.newPage();
			page.onConsoleMessage(message -> {
				String text = message.text();
				System.out.println("[DevTools Console] " + text);

				if (text.startsWith("Data received: ")) {
					String payload = text.substring("Data received: ".length());
					messages.add("packet received from server: " + payload);
				}
				if ("Stream closed.".equals(message.text())) {
					messages.add("stream closed");
					waiter.countDown();
				}
			});
			page.navigate("https://localhost:4433/");
			page.textContent("*").contains("Hello World!");
			waiter.await(10, TimeUnit.MINUTES);
		}
	}

	private static void startServer(BlockingQueue<String> messages, SelfSignedCertificate selfSignedCertificate, TestType testType, EventLoopGroup eventLoopGroup) throws InterruptedException {
		QuicSslContext sslContext = QuicSslContextBuilder.forServer(selfSignedCertificate.key(), null, selfSignedCertificate.cert())
				.applicationProtocols(Http3.supportedApplicationProtocols()).build();
		ChannelHandler codec = Http3.newQuicServerCodecBuilder()
				.sslContext(sslContext)
				.datagram(2048, 2048)
				.maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
				.initialMaxData(10000000)
				.initialMaxStreamDataBidirectionalLocal(1000000)
				.initialMaxStreamDataBidirectionalRemote(1000000)
				.initialMaxStreamDataUnidirectional(1000000)
				.initialMaxStreamsBidirectional(100)
				.initialMaxStreamsUnidirectional(100)
				.tokenHandler(InsecureQuicTokenHandler.INSTANCE)
				.handler(new ChannelInitializer<QuicChannel>() {
					@Override
					protected void initChannel(QuicChannel ch) {
						System.out.println("Connection created: channelId = " + ch.id());

						ch.pipeline().addLast(new ChannelDuplexHandler() {
							@Override
							public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
								if (msg instanceof ByteBuf) {
									ByteBuf in = (ByteBuf) msg;
									int sessionIdLength = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
									if (in.readableBytes() < sessionIdLength) {
										return;
									}
									long streamId = readVariableLengthInteger(in, sessionIdLength);
									ctx.fireChannelRead(new WebTransportStreamFrame(streamId, in.readRetainedSlice(in.readableBytes())));
									ReferenceCountUtil.release(msg);
								} else {
									super.channelRead(ctx, msg);
								}
							}

							@Override
							public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
								if (msg instanceof WebTransportStreamFrame) {
									WebTransportStreamFrame frame = (WebTransportStreamFrame) msg;
									ByteBuf out = Unpooled.buffer(frame.content().readableBytes() + 8);
									writeVariableLengthInteger(out, frame.streamId());
									out.writeBytes(frame.content());
									ReferenceCountUtil.release(frame);
									ctx.write(out, promise);
								} else {
									super.write(ctx, msg, promise);
								}
							}
						});
						ch.pipeline().addLast(createWebTransportFrameHandler(messages, testType));

						ch.pipeline().addLast(new Http3ServerConnectionHandler(
								new ChannelInitializer<QuicStreamChannel>() {
									// Called for each request-stream,
									@Override
									protected void initChannel(QuicStreamChannel ch) {
										System.out.println("Stream created: streamId = " + ch.streamId());

										ch.pipeline().addLast(new Http3RequestStreamInboundHandler() {
											boolean isHttpRequest;

											@Override
											protected void channelRead(ChannelHandlerContext ctx, Http3UnknownFrame frame) {
												System.out.println("Unknown frame received: content = " + new String(ByteBufUtil.getBytes(frame.content())));

												super.channelRead(ctx, frame);
											}

											@Override
											protected void channelRead(
													ChannelHandlerContext ctx, Http3HeadersFrame frame) {
												System.out.println("Headers frame received: headers = " + frame.headers());

												if ("/webtransport".equals(frame.headers().path().toString())) {
													ctx.fireChannelRead(frame);
													return;
												}

												isHttpRequest = true;
											}

											@Override
											protected void channelRead(
													ChannelHandlerContext ctx, Http3DataFrame frame) {
												System.out.println("Data frame received: " + Arrays.toString(ByteBufUtil.getBytes(frame.content())));

												frame.release();
											}

											@Override
											protected void channelInputClosed(ChannelHandlerContext ctx) {
												if (!isHttpRequest) {
													((QuicStreamChannel) ctx.channel()).shutdownOutput();
													return;
												}

												sendHtmlContent(selfSignedCertificate, testType, ctx);
											}
										});
										ch.pipeline().addLast(new WebTransportSessionHandler());
										ch.pipeline().addLast(createWebTransportFrameHandler(messages, testType));
									}
								}, null, null, WebTransportSessionHandler.createSettingsFrameFromServer(), true));
					}
				}).build();
		Bootstrap bs = new Bootstrap();
		Channel channel = bs.group(eventLoopGroup)
				.channel(NioDatagramChannel.class)
				.handler(codec)
				.bind(new InetSocketAddress(4433)).sync().channel();
	}

	private static SimpleChannelInboundHandler<WebTransportStreamFrame> createWebTransportFrameHandler(BlockingQueue<String> messages, TestType testType) {
		return new SimpleChannelInboundHandler<WebTransportStreamFrame>() {
			@Override
			protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebTransportStreamFrame frame) throws Exception {
				byte[] payload = ByteBufUtil.getBytes(frame.content());

				System.out.println("WebTransport packet received: payload = " + Arrays.toString(payload));

				messages.add("packet received from client: " + new String(payload, StandardCharsets.UTF_8));

				if (testType == TestType.UNIDIRECTIONAL) {
					return;
				}

				channelHandlerContext.writeAndFlush(new WebTransportStreamFrame(frame.streamId(), Unpooled.wrappedBuffer(payload))).addListener(futue -> {
					if (futue.isSuccess()) {
						System.out.println("WebTransport stream packet sent: payload = " + Arrays.toString(payload));
					} else {
						System.out.println("Sending WebTransport stream packet failed: payload = " + Arrays.toString(payload));
						futue.cause().printStackTrace();
					}
				});
			}
		};
	}

	private static void sendHtmlContent(SelfSignedCertificate selfSignedCertificate, TestType testType, ChannelHandlerContext ctx) {
		String content;
		try {
			content = new String(Resources.toByteArray(Resources.getResource(WebTransportTest.class, "index.html")), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		String replacedContent = content.replace("$CERTIFICATE_HASH", toPublicKeyHashAsBase64(selfSignedCertificate.cert()));
		replacedContent = replacedContent.replace("$TEST_TYPE", "" + testType);
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

	private static SelfSignedCertificate createSelfSignedCertificateForLocalHost() throws CertificateException {
		Date now = new Date();
		Date oneDayLater = new Date(now.getTime() + 24 * 60 * 60 * 1000);
		SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate(now, oneDayLater, "EC", 256);
		return selfSignedCertificate;
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

	private enum TestType {
		DATAGRAM, UNIDIRECTIONAL, BIDIRECTIONAL
	}
}
