package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Feux de l'épave : port de {@code draw_fires} (flammes animées des bandes {@code fx/fire_*_strip.png}, aux
 * positions, tailles et déphasages du manifeste) et de la lueur des plaques {@code *_fire} ({@code fire_plate}),
 * ajoutée au décor du milieu et au premier plan.
 *
 * <p>Intensité ({@code s["fire"]}) sur le récit ; image de la bande et vacillement sur le temps réel.
 */
public final class FireEffect implements SceneEffect {
    private static final float[] ADD = Ambiance.rgb("#ff9a3a");
    /** Images par seconde de la bande de flammes ({@code int(t * 22)}). */
    private static final double FLAME_FPS = 22;
    /** Côté d'une image de la bande (texture vanilla 16x16, 32 images empilées). */
    private static final int TILE = 16;
    private static final int TILES = 32;

    /** Une flamme : bande (0 ou 1), image de la bande, coin haut-gauche et côté, alpha recouvrant et ajouté. */
    public record Flame(int strip, int index, float x, float y, int size, float alpha, float addAlpha) {
    }

    private final SceneManifest manifest;
    private TextureStore textures;
    private EffectProgram flames;
    private EffectProgram glow;

    public FireEffect(SceneManifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public String name() {
        return "fire";
    }

    @Override
    public void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable {
        this.textures = textures;
        flames = EffectProgram.load(shaders, name(), "fire");
        glow = EffectProgram.load(shaders, name(), "fireglow");
    }

    /** Les flammes d'une image, dans l'ordre du manifeste. */
    public static List<Flame> flames(SceneManifest m, Ambiance a, float storyTime, double realTime) {
        List<Flame> out = new ArrayList<>();
        double level = EffectMath.fireLevel(storyTime);
        if (level <= 0.01) {
            return out;
        }
        Ambiance.View view = a.mid();
        long frame = (long) Math.floor(realTime * FLAME_FPS);
        for (SceneManifest.Fire fire : m.fires()) {
            double size = fire.size() * (0.55 + 0.45 * level) * view.zoom();
            int side = Math.max(2, (int) size);
            int index = (int) Math.floorMod(frame + fire.phase(), (long) TILES);
            float[] p = view.point(m, fire.x(), fire.y());
            out.add(new Flame(fire.strip(), index, HaloEffect.trunc(p[0] - side / 2.0),
                    HaloEffect.trunc(p[1] - side * 0.8), side, (float) Math.min(1.0, level * 1.4),
                    (float) (0.35 * level)));
        }
        return out;
    }

    @Override
    public void render(SceneState state, View view) {
        double level = EffectMath.fireLevel(state.storyTime());
        if (level <= 0.01) {
            return;
        }
        switch (view.pass()) {
            case MID_GLOW -> drawGlow(view, "mid", view.ambiance().mid(), level * EffectMath.flicker(state.realTime()) * 1.8);
            case MID -> drawFlames(view, flames(manifest, view.ambiance(), state.storyTime(), state.realTime()));
            case FRONT -> drawGlow(view, "fg", view.ambiance().fg(), level * EffectMath.flicker(state.realTime()) * 0.9);
            default -> {
            }
        }
    }

    /** {@code frame += view.sample(A.fire[layer]) * fire * flicker * gain} : couleurs du feu, alpha de la nuit. */
    private void drawGlow(View view, String layer, Ambiance.View camera, double gain) {
        int fire = EffectProgram.texture(textures, "plates/" + layer + "_fire.png");
        int mask = EffectProgram.texture(textures, "plates/" + layer + "_night.png");
        glow.use(view, manifest);
        glow.rect(0, 0, manifest.outW(), manifest.outH());
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fire);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, mask);
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(glow.loc("uFire"), 0);
        glUniform1i(glow.loc("uMask"), 1);
        glUniform3f(glow.loc("uView"), camera.zoom(), camera.offX(), camera.offY());
        glUniform1f(glow.loc("uGain"), (float) gain);
        view.quad().draw();
    }

    private void drawFlames(View view, List<Flame> list) {
        if (list.isEmpty()) {
            return;
        }
        int[] strips = {EffectProgram.texture(textures, "fx/fire_0_strip.png"),
                EffectProgram.texture(textures, "fx/fire_1_strip.png")};
        flames.use(view, manifest);
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(flames.loc("uStrip"), 0);
        glUniform1i(flames.loc("uTile"), TILE);
        glUniform3fv(flames.loc("uAdd"), ADD);
        int bound = -1;
        for (Flame f : list) {
            if (f.strip() != bound) {
                bound = f.strip();
                glBindTexture(GL_TEXTURE_2D, strips[bound]);
            }
            flames.rect(f.x(), f.y(), f.size(), f.size());
            glUniform1i(flames.loc("uIndex"), f.index());
            glUniform1f(flames.loc("uAlpha"), f.alpha());
            glUniform1f(flames.loc("uAddAlpha"), f.addAlpha());
            view.quad().draw();
        }
    }

    @Override
    public void close() {
        if (flames != null) {
            flames.close();
            flames = null;
        }
        if (glow != null) {
            glow.close();
            glow = null;
        }
    }
}
