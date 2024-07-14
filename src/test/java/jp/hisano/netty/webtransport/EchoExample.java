package jp.hisano.netty.webtransport;

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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class EchoExample {
	public static void main(String[] args) throws Exception {
		SelfSignedCertificate selfSignedCertificate = CertificateUtils.createSelfSignedCertificateForLocalHost();

		startServer(selfSignedCertificate);
		startClient(selfSignedCertificate);

		System.out.println("Wait for enter key to exit.");
		System.in.read();
		System.exit(0);
	}

	private static void startServer(SelfSignedCertificate selfSignedCertificate) {
		QuicSslContext sslContext = QuicSslContextBuilder
				.forServer(selfSignedCertificate.key(), null, selfSignedCertificate.cert())
				.applicationProtocols(Http3.supportedApplicationProtocols()).build();

		ChannelHandler codec = WebTransport.newQuicServerCodecBuilder()
				.sslContext(sslContext)
				.tokenHandler(InsecureQuicTokenHandler.INSTANCE)
				.handler(new ChannelInitializer<QuicChannel>() {
					@Override
					protected void initChannel(QuicChannel ch) {
						ChannelHandler streamChannelInitializer = new ChannelInitializer<QuicStreamChannel>() {
							@Override
							protected void initChannel(QuicStreamChannel ch) {
								ch.pipeline().addLast(new WebTransportStreamCodec() {
									@Override
									protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
										if ("/echo".equals(frame.headers().path().toString())) {
											super.channelRead(ctx, frame);
										} else {
											HttpUtils.sendHtmlContent(ctx, "echo.html", selfSignedCertificate);
										}
									}
								});
								ch.pipeline().addLast(new SimpleChannelInboundHandler<WebTransportFrame>() {
									@Override
									protected void channelRead0(ChannelHandlerContext channelHandlerContext, WebTransportFrame frame) throws Exception {
										if (frame instanceof WebTransportStreamOpenFrame) {
											System.out.println("Stream opened.");
										} else if (frame instanceof WebTransportStreamDataFrame) {
											WebTransportStreamDataFrame streamDataFrame = (WebTransportStreamDataFrame) frame;

											byte[] payload = ByteBufUtil.getBytes(streamDataFrame.content());
											System.out.println("Data received: payload = " + new String(payload, StandardCharsets.UTF_8));

											channelHandlerContext.writeAndFlush(new WebTransportStreamDataFrame(streamDataFrame.stream(), Unpooled.wrappedBuffer(payload))).addListener(futue -> {
												if (futue.isSuccess()) {
													System.out.println("Data sent.");
												} else {
													System.err.println("Sending data failed.");
												}
											});
										} else if (frame instanceof WebTransportStreamCloseFrame) {
											System.out.println("Stream closed.");
										}
									}
								});
							}
						};
						ch.pipeline().addLast(new Http3ServerConnectionHandler(streamChannelInitializer, null, null, WebTransport.createSettingsFrame(), true));
					}
				}).build();

		Bootstrap bs = new Bootstrap();
		bs.group(new NioEventLoopGroup());
		bs.channel(NioDatagramChannel.class);
		bs.handler(codec);
		bs.bind(new InetSocketAddress(4433)).syncUninterruptibly();
	}

	private static void startClient(SelfSignedCertificate selfSignedCertificate) throws Exception {
		Playwright playwright = Playwright.create();
		BrowserType browserType = playwright.chromium();
		Browser browser = browserType.launch(new BrowserType.LaunchOptions()
				.setHeadless(false)
				.setArgs(Arrays.asList(
						"--test-type",
						"--enable-quic",
						"--quic-version=h3",
						"--origin-to-force-quic-on=localhost:4433",
						"--ignore-certificate-errors-spki-list=" + CertificateUtils.toPublicKeyHashAsBase64(selfSignedCertificate)
				)));

		Page page = browser.newPage();
		page.navigate("https://localhost:4433/");
		page.textContent("*").contains("LOADED");
	}
}
