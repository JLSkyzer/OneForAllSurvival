package dev.oneforallsurvival.bench;

import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Fait avancer, à chaque tick serveur, la file de chaque atelier qui a du travail. */
public final class WorkshopTicker {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        WorkshopSavedData data = WorkshopSavedData.get(server);
        for (PlayerWorkshop workshop : data.activeWorkshops()) {
            // Un atelier actif a une file non vide ; seule sa tête avance.
            workshop.advance(speedOf(server, workshop.queue().get(0)));
        }
    }

    /**
     * Vitesse de la tête de file, selon l'établi où elle a été commandée.
     * <ul>
     *   <li>établi chargé, bloc présent : vitesse alimentée s'il tourne, à la main sinon ; l'état est mémorisé ;</li>
     *   <li>établi chargé, bloc absent (cassé) : à la main ;</li>
     *   <li>établi non chargé : le dernier état connu. Jamais de chargement de chunk pour le savoir.</li>
     * </ul>
     */
    static double speedOf(MinecraftServer server, WorkshopEntry head) {
        ServerLevel level = server.getLevel(head.benchLevel());
        boolean powered;
        if (level == null || !level.hasChunkAt(head.benchPos())) {
            powered = head.lastKnownPowered();
        } else {
            powered = level.getBlockEntity(head.benchPos()) instanceof EngineerBenchBlockEntity bench
                    && bench.state() == MachineState.RUNNING;
            head.setLastKnownPowered(powered);
        }
        return powered ? BenchConfig.poweredSpeed() : BenchConfig.manualSpeed();
    }
}
