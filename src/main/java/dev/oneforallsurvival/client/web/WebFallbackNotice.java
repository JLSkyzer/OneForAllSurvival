package dev.oneforallsurvival.client.web;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** L'avis « le navigateur ne sert pas, l'interface classique prend le relais » : une seule fois par session de jeu. */
public final class WebFallbackNotice {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-web");
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private WebFallbackNotice() {}

    /** Message de chat traduit la première fois de la session de jeu ; ensuite, le journal seulement. */
    public static void warnOnce(WebFailure failure) {
        LOG.warn("[web] repli : {}", failure);
        if (failure == null || !WARNED.compareAndSet(false, true)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().addMessage(Component.translatable(failure.translationKey()));
    }

    static boolean shouldWarn() {
        return !WARNED.get();
    }

    static void markWarned() {
        WARNED.set(true);
    }

    static void resetForTest() {
        WARNED.set(false);
    }
}
