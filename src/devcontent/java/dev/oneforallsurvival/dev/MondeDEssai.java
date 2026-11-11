package dev.oneforallsurvival.dev;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le monde du serveur de GameTest est isolé du hasard : pas d'apparition de monstres ou d'animaux, pas de
 * météo qui change, pas de feu qui se propage, pas de jour qui passe.
 *
 * <p>Le terrain plat, sans structure ni apparition, vient de {@code server.properties}, réécrit par la tâche
 * {@code runGameTestServer} de {@code build.gradle} (le serveur de GameTest de Forge construit son monde à partir
 * de ce fichier). Les règles de jeu, elles, ne s'y trouvent pas : on les pose ici, une fois le serveur lancé.
 * Ce code vit dans {@code devcontent} et ne sert que sur un {@link GameTestServer}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MondeDEssai {

    private MondeDEssai() {}

    @SubscribeEvent
    public static void auDemarrage(ServerStartingEvent event) {
        if (!(event.getServer() instanceof GameTestServer serveur)) {
            return;
        }
        GameRules regles = serveur.getGameRules();
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, serveur);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, serveur);
        regles.getRule(GameRules.RULE_DOFIRETICK).set(false, serveur);
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, serveur);
    }
}
