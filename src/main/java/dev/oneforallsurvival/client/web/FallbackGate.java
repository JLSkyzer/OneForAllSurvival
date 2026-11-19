package dev.oneforallsurvival.client.web;

import java.util.function.Consumer;

/**
 * Décide quand {@link WebScreen} passe au repli : une seule fois, jamais après la fermeture, et jamais pendant
 * {@code init()} (donc pendant {@code Minecraft.setScreen}) : un repli demandé là est différé à la tâche suivante du
 * client, pour qu'une sous-classe puisse changer d'écran sans {@code setScreen} imbriqué.
 */
final class FallbackGate {

    private final Consumer<WebFailure> onFallback;
    private final Consumer<Runnable> later;
    private boolean fellBack;
    private boolean closed;
    private boolean inInit;

    FallbackGate(Consumer<WebFailure> onFallback, Consumer<Runnable> later) {
        this.onFallback = onFallback;
        this.later = later;
    }

    void beginInit() {
        inInit = true;
    }

    void endInit() {
        inInit = false;
    }

    void close() {
        closed = true;
    }

    boolean fellBack() {
        return fellBack;
    }

    /** Vrai si cet appel prend le repli (premier, écran ouvert) ; l'appel à la sous-classe peut être différé. */
    boolean fail(WebFailure failure) {
        if (fellBack || closed) {
            return false;
        }
        fellBack = true;
        if (inInit) {
            later.accept(() -> {
                if (!closed) {
                    onFallback.accept(failure);
                }
            });
        } else {
            onFallback.accept(failure);
        }
        return true;
    }
}
