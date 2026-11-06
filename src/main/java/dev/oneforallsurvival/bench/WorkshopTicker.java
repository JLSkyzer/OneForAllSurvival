package dev.oneforallsurvival.bench;

import net.minecraft.server.MinecraftServer;
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
        // La vitesse selon l'établi arrive avec le bloc (Task 4) ; ici, la vitesse manuelle.
        double speed = BenchConfig.manualSpeed();
        for (PlayerWorkshop workshop : data.activeWorkshops()) {
            workshop.advance(speed);
        }
    }
}
