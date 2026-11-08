package dev.oneforallsurvival.client.title;

import dev.oneforallsurvival.OneForAllSurvival;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Branche l'écran titre de Lost Island par les événements de Forge (aucun mixin).
 *
 * <ul>
 *   <li>{@link #onOpening} : tout {@code TitleScreen} de classe exacte (premier titre après le chargement,
 *       retour d'un monde, déconnexion, {@code setScreen(null)} hors monde) devient un
 *       {@link LostIslandTitleScreen}. Une sous-classe venue d'un autre mod est laissée telle quelle. Les écrans
 *       ouverts depuis le titre (Options, liste des mods, connexion) gardent notre instance comme parent et y
 *       reviennent directement. Priorité basse : un mod qui remplace tout le titre passe avant et l'emporte.</li>
 *   <li>{@link #onInitPost} : après tous les autres abonnés ({@code LOWEST}), range les boutons qu'ils ont
 *       ajoutés dans la rangée du bas.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class TitleEvents {
    private TitleEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next != null && next.getClass() == TitleScreen.class) {
            event.setNewScreen(new LostIslandTitleScreen());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInitPost(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof LostIslandTitleScreen screen) {
            screen.arrange();
        }
    }
}
