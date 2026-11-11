package dev.oneforallsurvival.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.oneforallsurvival.light.LighthouseBlock;
import dev.oneforallsurvival.light.LighthouseBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Les deux faisceaux opposés du phare : un tour en {@value #PERIOD_TICKS} ticks, calé sur le temps de jeu (le même pour
 * tous les clients, donc le même angle), 96 blocs de long, translucides, seulement quand le phare est allumé. Chaque
 * faisceau est une croix de deux bandes (verticale et horizontale) qui s'évase et s'efface au loin.
 */
public final class LighthouseRenderer implements BlockEntityRenderer<LighthouseBlockEntity> {

    /** Un tour en 8 secondes. */
    static final int PERIOD_TICKS = 160;
    static final float LENGTH = 96.0F;
    private static final float START_HALF_WIDTH = 0.12F;
    private static final float END_HALF_WIDTH = 2.5F;
    private static final float START_ALPHA = 0.85F;
    /** La lampe, vers le haut du bloc. */
    private static final float LAMP_HEIGHT = 0.75F;
    /** Blanc-jaune chaud, comme la lampe d'un phare : la couleur est dans la texture du mod, pas teintée sur celle de la balise. */
    private static final ResourceLocation BEAM = new ResourceLocation("oneforallsurvival", "textures/misc/lighthouse_beam.png");

    public LighthouseRenderer(BlockEntityRendererProvider.Context context) {}

    /** Angle du faisceau, en degrés, à ce temps de jeu : ne dépend de rien de local au client. */
    static float angle(long gameTime, float partialTick) {
        return ((gameTime % PERIOD_TICKS) + partialTick) * 360.0F / PERIOD_TICKS;
    }

    @Override
    public void render(LighthouseBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().getValue(LighthouseBlock.LIT)) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, LAMP_HEIGHT, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-angle(be.getLevel().getGameTime(), partialTick)));
        VertexConsumer consumer = buffers.getBuffer(RenderType.beaconBeam(BEAM, true));
        PoseStack.Pose last = pose.last();
        beam(consumer, last.pose(), last.normal(), 1.0F);
        beam(consumer, last.pose(), last.normal(), -1.0F);
        pose.popPose();
    }

    /** Un faisceau le long de l'axe x, vers {@code sign} (1 ou -1) : une bande verticale et une horizontale. */
    private static void beam(VertexConsumer c, Matrix4f m, Matrix3f n, float sign) {
        float far = LENGTH * sign;
        quad(c, m, n, far, true);
        quad(c, m, n, far, false);
    }

    private static void quad(VertexConsumer c, Matrix4f m, Matrix3f n, float far, boolean vertical) {
        // Chaque bande est émise dans les deux sens : le rendu ne dessine pas les faces de dos.
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? 1.0F : -1.0F;
            float ny = vertical ? 0 : s;
            float nz = vertical ? s : 0;
            vertex(c, m, n, 0, -START_HALF_WIDTH * s, vertical, START_ALPHA, 0, 0, ny, nz);
            vertex(c, m, n, 0, START_HALF_WIDTH * s, vertical, START_ALPHA, 1, 0, ny, nz);
            vertex(c, m, n, far, END_HALF_WIDTH * s, vertical, 0.0F, 1, 4, ny, nz);
            vertex(c, m, n, far, -END_HALF_WIDTH * s, vertical, 0.0F, 0, 4, ny, nz);
        }
    }

    private static void vertex(VertexConsumer c, Matrix4f m, Matrix3f n, float x, float spread, boolean vertical,
            float alpha, float u, float v, float ny, float nz) {
        c.vertex(m, x, vertical ? spread : 0, vertical ? 0 : spread)
                .color(1.0F, 1.0F, 1.0F, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0, ny, nz)
                .endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(LighthouseBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 4096;
    }
}
