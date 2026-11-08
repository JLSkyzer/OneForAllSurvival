package dev.oneforallsurvival.client.title;

import dev.oneforallsurvival.OneForAllSurvival;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Libère la scène de l'écran titre en entrant dans un monde (solo ou serveur) : ses textures n'ont pas à rester
 * en mémoire vidéo pendant le jeu. Le retour au titre la recrée ({@link TitleScene#acquire}).
 *
 * <p>Classe à part de {@link TitleScene} : le bus d'événements lit par réflexion toutes les méthodes publiques de
 * la classe qu'il inscrit ; celles de {@code TitleScene} nomment la scène du jar de service, absente sans lui.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class TitleSceneEvents {
    private TitleSceneEvents() {}

    /** Connexion du joueur à un niveau, sur le thread du jeu (paquet de connexion traité par lui). */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        TitleScene.releaseForWorld();
    }
}
