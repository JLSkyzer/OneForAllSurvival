package dev.oneforallsurvival.client.web;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
        warnOnce(failure, WebFallbackNotice::toChat);
    }

    /** Le même avis, envoyé par {@code send} : la décision « une seule fois » est celle de {@link #claim()}. */
    static void warnOnce(WebFailure failure, Consumer<WebFailure> send) {
        LOG.warn("[web] repli : {}", failure);
        if (failure == null || !claim()) {
            return;
        }
        send.accept(failure);
    }

    private static void toChat(WebFailure failure) {
        Minecraft.getInstance().gui.getChat().addMessage(Component.translatable(failure.translationKey()));
    }

    /** Vrai pour le premier appel de la session de jeu seulement (atomique). */
    static boolean claim() {
        return WARNED.compareAndSet(false, true);
    }

    static boolean shouldWarn() {
        return !WARNED.get();
    }

    /** Alias de {@link #claim()} pour les essais. */
    static void markWarned() {
        claim();
    }

    static void resetForTest() {
        WARNED.set(false);
    }
}
