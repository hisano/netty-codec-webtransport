package jp.hisano.netty.webtransport;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebTransportTest {
	private static final byte[] CONTENT = "Hello World!\r\n".getBytes(StandardCharsets.UTF_8);
	private static final int PORT = 843;

	@Test
	public void testBasic() throws Exception {
		CountDownLatch waiter = new CountDownLatch(1);

		NioEventLoopGroup group = new NioEventLoopGroup(1);
		SelfSignedCertificate cert = new SelfSignedCertificate();
		QuicSslContext sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
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
														.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
														.addListener(result -> {
															waiter.countDown();
														});
											}
										});
									}
								}));
					}
				}).build();
		try {
			Bootstrap bs = new Bootstrap();
			Channel channel = bs.group(group)
					.channel(NioDatagramChannel.class)
					.handler(codec)
					.bind(new InetSocketAddress(PORT)).sync().channel();

			// Wait for 'docker run -t --rm badouralix/curl-http3 --insecure --verbose https://host.docker.internal:843/'

			waiter.await(10, TimeUnit.SECONDS);
		} finally {
			group.shutdownGracefully();
		}
	}
}
