package dev.oneforallsurvival.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Le paquet de file porte les réglages du serveur : l'écran ne lit pas de configuration locale. */
class QueuePacketTest {

    @Test
    void lesReglagesDuServeurFontLAllerRetour() {
        QueuePacket sent = new QueuePacket(List.of(), 12, 1.5, 6.25, 200);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.encode(buf);
        QueuePacket received = QueuePacket.decode(buf);
        assertEquals(12, received.stockItems());
        assertEquals(1.5, received.manualSpeed());
        assertEquals(6.25, received.poweredSpeed());
        assertEquals(200, received.maxQueue());
        assertTrue(received.rows().isEmpty());
        assertEquals(0, buf.readableBytes());
    }
}
