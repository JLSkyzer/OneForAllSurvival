package dev.oneforallsurvival.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Rejoue avec Netty seul la suite d'appels de {@code Connection} qui coupe la lecture du client, voir
 * {@code docs/verifications/lot-2.md}. L'ordre y est imposé : ce test prouve ce que la relance fait dans
 * cet état, pas que le vrai client y passe de cette façon.
 */
class ReadRescueTest {

    private static final byte[] REPONSE = {1, 2, 3, 4};

    private NioEventLoopGroup clients;
    private NioEventLoopGroup serveurs;
    private Channel serveur;
    private Channel client;
    private final AtomicInteger recus = new AtomicInteger();

    @BeforeEach
    void ouvrir() throws Exception {
        clients = new NioEventLoopGroup(1);
        serveurs = new NioEventLoopGroup(1);
        serveur = new ServerBootstrap().group(serveurs).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ((ByteBuf) msg).release();
                        ctx.writeAndFlush(Unpooled.wrappedBuffer(REPONSE));
                    }
                }).bind("127.0.0.1", 0).sync().channel();
    }

    @AfterEach
    void fermer() throws Exception {
        if (client != null) {
            client.close().sync();
        }
        serveur.close().sync();
        clients.shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.SECONDS).sync();
        serveurs.shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.SECONDS).sync();
    }

    /** channelActive diffère setAutoRead(true), et le Hello du fil connecteur passe pendant ce temps. */
    private void couperLaLecture() throws Exception {
        CountDownLatch actif = new CountDownLatch(1);
        client = new Bootstrap().group(clients).channel(NioSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        super.channelActive(ctx);
                        Channel ch = ctx.channel();
                        ch.eventLoop().execute(() -> ch.config().setAutoRead(true));
                        actif.countDown();
                        Thread.sleep(300);
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        recus.addAndGet(((ByteBuf) msg).readableBytes());
                        ((ByteBuf) msg).release();
                    }
                }).connect("127.0.0.1", ((InetSocketAddress) serveur.localAddress()).getPort()).sync().channel();
        actif.await();
        Channel ch = client;
        ch.config().setAutoRead(false);
        ch.eventLoop().execute(() -> {
            ch.eventLoop().execute(() -> ch.config().setAutoRead(true));
            ch.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {9}));
        });
        Thread.sleep(1000);
    }

    private boolean recoitTout(long delaiMs) throws InterruptedException {
        long fin = System.currentTimeMillis() + delaiMs;
        while (System.currentTimeMillis() < fin) {
            if (recus.get() == REPONSE.length) {
                return true;
            }
            Thread.sleep(10);
        }
        return recus.get() == REPONSE.length;
    }

    @Test
    void laRelanceDebloqueUneLectureCoupee() throws Exception {
        couperLaLecture();
        assertEquals(0, recus.get(), "le témoin : sans relance, la réponse du serveur n'arrive pas");
        assertTrue(client.config().isAutoRead(), "l'état du blocage a autoRead vrai");

        ReadRescue.rescue(client);

        assertTrue(recoitTout(2000), "après la relance, octets reçus : " + recus.get());
    }

    @Test
    void laRelanceNeLitPasPendantUnChangementDeProtocole() throws Exception {
        couperLaLecture();
        client.config().setAutoRead(false);

        ReadRescue.rescue(client);

        assertFalse(recoitTout(500), "autoRead faux : un paquet aurait pu passer par l'ancien décodeur");
    }

    @Test
    void laRelanceSurUnCanalFermeNeLancePasDErreur() throws Exception {
        couperLaLecture();
        client.close().sync();

        ReadRescue.rescue(client);

        assertEquals(0, recus.get());
    }

    @Test
    void leRythme() {
        ReadRescue rythme = new ReadRescue();
        Object connexion = new Object();
        int premiere = -1;
        int seconde = -1;
        for (int tick = 1; tick <= ReadRescue.FIRST_TICKS + ReadRescue.EVERY_TICKS; tick++) {
            if (rythme.isDue(connexion)) {
                if (premiere < 0) {
                    premiere = tick;
                } else {
                    seconde = tick;
                }
            }
        }
        assertEquals(ReadRescue.FIRST_TICKS, premiere, "première relance");
        assertEquals(ReadRescue.FIRST_TICKS + ReadRescue.EVERY_TICKS, seconde, "relance suivante");
    }

    @Test
    void uneNouvelleConnexionRepartDeZero() {
        ReadRescue rythme = new ReadRescue();
        for (int tick = 1; tick < ReadRescue.FIRST_TICKS; tick++) {
            assertFalse(rythme.isDue("ancienne"));
        }
        for (int tick = 1; tick < ReadRescue.FIRST_TICKS; tick++) {
            assertFalse(rythme.isDue("nouvelle"), "le compte aurait dû repartir, tick " + tick);
        }
        assertTrue(rythme.isDue("nouvelle"));
    }
}
