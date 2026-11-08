package dev.oneforallsurvival.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class IslandDebugInfoTest {

    private static IslandDebugResponse allerRetour(IslandDebugResponse reponse, int[] taille) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        reponse.encode(buf);
        taille[0] = buf.readableBytes();
        IslandDebugResponse lue = IslandDebugResponse.decode(buf);
        assertEquals(0, buf.readableBytes(), "tout doit être relu");
        return lue;
    }

    @Test
    void toutRevientTelQuel() {
        IslandDebugResponse complete = new IslandDebugResponse(new BlockPos(-12, 64, 3000),
                new IslandDebugInfo(true,
                        new IslandDebugInfo.Machine(MachineState.FAULT, true, 37.5f, FaultType.FOULING),
                        new IslandDebugInfo.Meter("Mme Essai", 120, 1_234_567_890_123L),
                        new IslandDebugInfo.Breaker(false, true)), 17.25f);
        assertEquals(complete, allerRetour(complete, new int[1]));
    }

    @Test
    void ceQuiEstAbsentResteAbsent() {
        IslandDebugResponse citoyen = new IslandDebugResponse(null, new IslandDebugInfo(false,
                new IslandDebugInfo.Machine(MachineState.RUNNING, false, null, null),
                new IslandDebugInfo.Meter("", 0, null), null), 20f);
        assertEquals(citoyen, allerRetour(citoyen, new int[1]));
    }

    @Test
    void lesTpsSuiventLeTempsDeTickSansDepasser20() {
        assertEquals(20f, IslandDebugResponse.tps(0f), 1e-3f, "serveur qui démarre");
        assertEquals(20f, IslandDebugResponse.tps(12.5f), 1e-3f, "en avance : le jeu reste à 20");
        assertEquals(20f, IslandDebugResponse.tps(50f), 1e-3f);
        assertEquals(10f, IslandDebugResponse.tps(100f), 1e-3f, "en retard");
        assertEquals(4f, IslandDebugResponse.tps(250f), 1e-3f);
    }

    @Test
    void laReponseResteCourte() {
        int[] taille = new int[1];
        allerRetour(new IslandDebugResponse(new BlockPos(1, 2, 3), new IslandDebugInfo(true,
                new IslandDebugInfo.Machine(MachineState.FAULT, true, 100f, FaultType.BEARING),
                new IslandDebugInfo.Meter("0123456789abcdef", Integer.MAX_VALUE, Long.MAX_VALUE),
                new IslandDebugInfo.Breaker(true, true)), 20f), taille);
        assertTrue(taille[0] <= 64, "octets : " + taille[0]);
    }
}
