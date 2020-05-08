import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

import javax.net.ssl.KeyManagerFactory;
import java.security.KeyStore;
import java.security.Security;

public class ClientStarter {
    static final String HOST = System.getProperty("host", "192.168.8.94");
    static final int PORT = Integer.parseInt(System.getProperty("port", "1024"));
    static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));

    @Slf4j
    static class ClientHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("loaded");
            super.channelActive(ctx);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            log.info("calldone");
            super.channelReadComplete(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.info("call on exception", cause);
            //super.exceptionCaught(ctx, cause);
        }
    }

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }
        if (Security.getProvider(BouncyCastleJsseProvider.PROVIDER_NAME) == null) {
            Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
        }
        Security.setProperty("ssl.KeyManagerFactory.algorithm", "PKIX ");
        Security.setProperty("ssl.TrustManagerFactory.algorithm", "PKIX ");
    }

    public static void main(String[] args) {
        val group = new NioEventLoopGroup();
        try {
            val tks = KeyStore.getInstance("BKS");
            char[] pwd = {'1', '2', '3', '4', '5', '6'};
            tks.load(ClientStarter.class.getResourceAsStream("/trustStore_cloud.bks"), pwd);
            val tm = InsecureTrustManagerFactory.INSTANCE;
            tm.init(tks);
            val pks = KeyStore.getInstance("BKS");
            pks.load(ClientStarter.class.getResourceAsStream("/cloud.bks"), pwd);
            val km = KeyManagerFactory.getInstance("PKIX");
            km.init(pks, pwd);
            val sslCtx = SslContextBuilder.forClient()
                .keyManager(km)
                .trustManager(tm).build();
            // Configure the client.
            val b = new Bootstrap();
            b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(sslCtx.newHandler(ch.alloc(), HOST, PORT));
                        p.addLast(new LoggingHandler(LogLevel.INFO));
                        p.addLast(new ClientHandler());
                    }
                });

            // Start the client.
            val f = b.connect(HOST, PORT).sync();

            // Wait until the connection is closed.
            f.channel().closeFuture().sync();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            // Shut down the event loop to terminate all threads.
            group.shutdownGracefully();
        }
    }

}
