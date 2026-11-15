package dev.oneforallsurvival.economy.quest.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.oneforallsurvival.OneForAllSurvival;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * La touche de l'écran des quêtes : J par défaut (« Quêtes », catégorie Lost Island, réglable dans Contrôles). Au
 * relâchement, en jeu et sans écran ouvert, elle ouvre l'écran sur la copie du client et demande l'état au serveur.
 * Dans l'écran, la même touche le referme.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class QuestKeys {

    public static final KeyMapping OPEN = new KeyMapping("key.oneforallsurvival.quests", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.oneforallsurvival");

    private static boolean wasDown;

    private QuestKeys() {}

    @Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean down = OPEN.isDown();
        // Les appuis ne s'accumulent pas : seul le relâchement ouvre.
        while (OPEN.consumeClick()) {
            // rien
        }
        if (wasDown && !down && mc.screen == null && mc.player != null && mc.level != null) {
            QuestScreen.openAndRequest();
        }
        wasDown = down && mc.screen == null;
    }
}
