package dev.oneforallsurvival.client.title;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.OneForAllSurvival;
import java.lang.reflect.Field;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

/**
 * Branche l'écran titre de Lost Island par les événements de Forge (aucun mixin).
 *
 * <ul>
 *   <li>{@link #onOpening} : ce que décide {@link TitleRouting} devient un {@link LostIslandTitleScreen} — tout
 *       {@code TitleScreen} de classe exacte, et la liste Multijoueur ouverte avec un écran titre pour parent (retour
 *       d'une déconnexion volontaire d'un serveur). Une sous-classe venue d'un autre mod est laissée telle quelle.
 *       Les écrans ouverts depuis le titre (Options, liste des mods, connexion, expulsion) gardent notre instance
 *       comme parent et y reviennent directement. Priorité basse : un mod qui remplace tout le titre passe avant et
 *       l'emporte.</li>
 *   <li>{@link #onInitPost} : après tous les autres abonnés ({@code LOWEST}), range les boutons qu'ils ont
 *       ajoutés dans la rangée du bas.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class TitleEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** {@code JoinMultiplayerScreen.lastScreen} (nom SRG lu dans le jar srg de Forge 47.4.28). */
    private static final String LAST_SCREEN_SRG = "f_99676_";
    private static Field lastScreen;
    private static boolean lastScreenUnavailable;

    private TitleEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next == null) {
            return;
        }
        TitleRouting.Opening kind = next.getClass() == TitleScreen.class ? TitleRouting.Opening.VANILLA_TITLE
                : next.getClass() == JoinMultiplayerScreen.class ? TitleRouting.Opening.MULTIPLAYER_LIST
                : TitleRouting.Opening.OTHER;
        boolean parentIsTitle = kind == TitleRouting.Opening.MULTIPLAYER_LIST
                && parentOf(next) instanceof TitleScreen;
        if (TitleRouting.openOurTitle(kind, parentIsTitle)) {
            event.setNewScreen(new LostIslandTitleScreen());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInitPost(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof LostIslandTitleScreen screen) {
            screen.arrange();
        }
    }

    /** Parent d'une liste Multijoueur (champ privé lu par réflexion), {@code null} si illisible. */
    private static Screen parentOf(Screen multiplayerList) {
        if (lastScreenUnavailable) {
            return null;
        }
        try {
            if (lastScreen == null) {
                lastScreen = ObfuscationReflectionHelper.findField(JoinMultiplayerScreen.class, LAST_SCREEN_SRG);
            }
            return (Screen) lastScreen.get(multiplayerList);
        } catch (Throwable t) {
            lastScreenUnavailable = true;
            LOGGER.warn("Écran titre : parent de la liste Multijoueur illisible, liste laissée telle quelle", t);
            return null;
        }
    }
}
