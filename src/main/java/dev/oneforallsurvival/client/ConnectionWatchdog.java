package dev.oneforallsurvival.client;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.OneForAllSurvival;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

/** Tant que l'écran de connexion est ouvert, relance la lecture de sa connexion, voir {@link ReadRescue}. */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class ConnectionWatchdog {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ReadRescue RHYTHM = new ReadRescue();

    private static Field connectionField;
    private static boolean disabled;

    private ConnectionWatchdog() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || disabled
                || !(Minecraft.getInstance().screen instanceof ConnectScreen screen)) {
            return;
        }
        Connection connection = connectionOf(screen);
        if (connection != null && RHYTHM.isDue(connection)) {
            LOGGER.info("Connexion encore en attente du serveur : relance de la lecture");
            ReadRescue.rescue(connection.channel());
        }
    }

    private static Connection connectionOf(ConnectScreen screen) {
        try {
            if (connectionField == null) {
                connectionField = ObfuscationReflectionHelper.findField(ConnectScreen.class, "f_95684_");
            }
            return (Connection) connectionField.get(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Sans la surveillance, le client garde seulement le comportement de Minecraft.
            disabled = true;
            LOGGER.warn("Surveillance de la connexion désactivée", e);
            return null;
        }
    }
}
