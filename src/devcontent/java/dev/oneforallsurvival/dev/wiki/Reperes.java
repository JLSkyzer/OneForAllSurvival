package dev.oneforallsurvival.dev.wiki;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Les matrices réelles de la caméra, relevées à chaque image pendant le rendu du monde (étape des blocs pleins) : la
 * rotation de la vue, la projection (FOV, balancement et proportions de la fenêtre compris) et la position de l'œil.
 * Un point du monde se projette avec celles de l'image capturée, en pourcentage de l'image.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Reperes {

    private static final Matrix4f VUE = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 oeil = Vec3.ZERO;
    private static long image = -1;

    private Reperes() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!WikiStudio.ACTIF || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        VUE.set(event.getPoseStack().last().pose());
        PROJECTION.set(event.getProjectionMatrix());
        oeil = event.getCamera().getPosition();
        image = WikiStudio.numeroImage();
    }

    /** Vrai si les matrices viennent de l'image en cours (le monde a été rendu à cette image). */
    static boolean aJour() {
        return image == WikiStudio.numeroImage();
    }

    /**
     * Le point du monde en pourcentage de l'image ({@code [x, y]}, origine en haut à gauche), ou vide s'il est derrière
     * la caméra.
     */
    static Optional<double[]> projeter(Vec3 point) {
        Vector4f p = new Vector4f((float) (point.x - oeil.x), (float) (point.y - oeil.y), (float) (point.z - oeil.z), 1F);
        VUE.transform(p);
        PROJECTION.transform(p);
        if (p.w <= 1e-6F) {
            return Optional.empty();
        }
        double x = p.x / p.w;
        double y = p.y / p.w;
        return Optional.of(new double[] {(x + 1) * 50, (1 - y) * 50});
    }

    /** Un point de l'interface (coordonnées mises à l'échelle) en pourcentage de l'image. */
    static double[] interfaceEnPourcent(Minecraft mc, double gx, double gy) {
        var f = mc.getWindow();
        double echelle = f.getGuiScale();
        return new double[] {gx * echelle * 100 / f.getWidth(), gy * echelle * 100 / f.getHeight()};
    }
}
