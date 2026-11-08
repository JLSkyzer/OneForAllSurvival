package dev.oneforallsurvival.client.debug;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.OneForAllSurvival;
import java.lang.reflect.Field;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * F3 + Tab fait défiler les vues du F3. Sans marquer le combo comme traité, Minecraft fermerait le F3 au relâchement
 * de F3 : d'où {@code KeyboardHandler.handledDebugKey}, privé, posé par réflexion. Si ce champ est introuvable, F3 + Tab
 * reste inactif et une touche ordinaire, « Vue détaillée du F3 », prend le relais tant que le F3 est ouvert.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class DebugKeys {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** {@code KeyboardHandler.handledDebugKey}, relevé dans {@code joined.tsrg} et par javap du jar SRG. */
    private static final String HANDLED_DEBUG_KEY = "f_90873_";

    @Nullable
    private static Field handledDebugKey;
    /** Créée seulement si le champ manque : une {@code KeyMapping} s'inscrit dans les tables de touches dès sa construction. */
    @Nullable
    private static KeyMapping fallback;

    private DebugKeys() {}

    /** Bus du mod : le choix entre le combo et la touche se fait une fois, à l'enregistrement des touches. */
    @Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            try {
                handledDebugKey = ObfuscationReflectionHelper.findField(KeyboardHandler.class, HANDLED_DEBUG_KEY);
            } catch (ObfuscationReflectionHelper.UnableToFindFieldException e) {
                LOGGER.warn("KeyboardHandler.handledDebugKey est introuvable : F3 + Tab est remplacé par une touche ordinaire", e);
                fallback = new KeyMapping("key.oneforallsurvival.f3_detail", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9,
                        "key.categories.oneforallsurvival");
                event.register(fallback);
            }
        }
    }

    /** Le raccourci à rappeler à l'écran. */
    static String shortcut() {
        return fallback == null ? "F3 + Tab" : fallback.getTranslatedKeyMessage().getString();
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (handledDebugKey == null || event.getKey() != GLFW.GLFW_KEY_TAB || event.getAction() == GLFW.GLFW_RELEASE
                || mc.screen != null || mc.level == null
                || !InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F3)) {
            return;
        }
        // Minecraft a déjà transmis Tab aux touches : le combo ne doit pas ouvrir la liste des joueurs.
        if (mc.options.keyPlayerList.matches(event.getKey(), event.getScanCode())) {
            mc.options.keyPlayerList.setDown(false);
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        try {
            handledDebugKey.setBoolean(mc.keyboardHandler, true);
        } catch (IllegalAccessException e) {
            LOGGER.warn("KeyboardHandler.handledDebugKey refuse l'écriture : F3 + Tab est désactivé", e);
            handledDebugKey = null;
            return;
        }
        mc.options.renderDebug = true;
        DebugHud.nextView();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || fallback == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (fallback.consumeClick()) {
            if (mc.options.renderDebug && mc.screen == null) {
                DebugHud.nextView();
            }
        }
    }
}
