package dev.oneforallsurvival.earlywindow.scene.effects;

import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Les effets de la scène, dans l'ordre de dessin. Un effet dont {@code init} ou {@code render} lève est retiré,
 * journalisé une seule fois (il n'est plus jamais appelé), et les autres continuent.
 */
public final class EffectChain implements AutoCloseable {
    private final List<SceneEffect> effects;
    private final Consumer<String> log;

    /** @param log reçoit une ligne par effet retiré */
    public EffectChain(List<SceneEffect> effects, Consumer<String> log) {
        this.effects = new ArrayList<>(effects);
        this.log = log;
    }

    /** Initialise chaque effet ; ceux qui refusent sont retirés. */
    public void init(TextureStore textures, ShaderLoader shaders) {
        for (SceneEffect e : List.copyOf(effects)) {
            try {
                e.init(textures, shaders);
            } catch (SceneEffect.EffectUnavailable | RuntimeException failure) {
                remove(e, "à l'initialisation", failure);
            }
        }
    }

    /** Dessine une passe ; un effet qui lève est retiré, l'image continue avec les suivants. */
    public void render(SceneState state, SceneEffect.View view) {
        for (SceneEffect e : List.copyOf(effects)) {
            try {
                e.render(state, view);
            } catch (RuntimeException failure) {
                remove(e, "au rendu", failure);
            }
        }
    }

    /** Noms des effets encore actifs, dans l'ordre de dessin. */
    public List<String> active() {
        List<String> names = new ArrayList<>();
        for (SceneEffect e : effects) {
            names.add(e.name());
        }
        return names;
    }

    private void remove(SceneEffect e, String phase, Throwable failure) {
        effects.remove(e);
        log.accept("effet « " + e.name() + " » retiré (échec " + phase + ") : " + failure.getMessage());
        try {
            e.close();
        } catch (RuntimeException ignored) {
            // L'effet est déjà retiré ; une seconde erreur n'apprendrait rien de plus.
        }
    }

    @Override
    public void close() {
        for (SceneEffect e : effects) {
            try {
                e.close();
            } catch (RuntimeException ignored) {
                // On libère les autres quoi qu'il arrive.
            }
        }
        effects.clear();
    }
}
