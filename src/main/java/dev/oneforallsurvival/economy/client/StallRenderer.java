package dev.oneforallsurvival.economy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.oneforallsurvival.economy.stall.MarketStallBlock;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallOffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Les trois premières offres de vente, posées sur le comptoir : un passant voit ce qui se vend sans ouvrir l'étal.
 * Un objet plat (outil, aliment) est couché, un bloc se pose tel quel ; chacun est à sa place devant le comptoir, du
 * côté du client, quelle que soit l'orientation de l'étal. Seules les offres envoyées aux clients (modèle, lot, prix)
 * sont lues : jamais le stock.
 */
public final class StallRenderer implements BlockEntityRenderer<MarketStallBlockEntity> {

    /** Nombre d'offres montrées. */
    static final int SHOWN = 3;
    /** Hauteur du dessus du comptoir (10/16), et un souffle au-dessus pour ne pas se confondre avec lui. */
    private static final float TOP = 10.0F / 16.0F + 0.002F;
    /** Les trois places le long du comptoir (x, de gauche à droite pour qui regarde l'étal) et leur profondeur (z). */
    private static final float[] X = {3.4F / 16.0F, 8.0F / 16.0F, 12.6F / 16.0F};
    private static final float Z = 3.0F / 16.0F;
    private static final float SCALE = 0.36F;

    public StallRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MarketStallBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof MarketStallBlock)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(MarketStallBlock.FACING);
        int shown = 0;
        for (StallOffer offer : be.offers()) {
            if (offer == null) {
                continue;
            }
            if (shown >= SHOWN) {
                break;
            }
            ItemStack stack = offer.template();
            if (!stack.isEmpty()) {
                pose.pushPose();
                pose.translate(0.5D, 0.0D, 0.5D);
                // Le modèle est tourné vers le nord ; l'orientation du bloc le tourne dans le sens des aiguilles.
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
                pose.translate(X[shown] - 0.5D, TOP, Z - 0.5D);
                boolean solid = Minecraft.getInstance().getItemRenderer()
                        .getModel(stack, be.getLevel(), null, 0).isGui3d();
                if (!solid) {
                    // Couché, penché de 35 degrés vers le client, le haut du dessin vers le fond de l'étal : lisible
                    // de face comme de dessus, sans toucher l'auvent.
                    pose.translate(0.0D, 0.1D, 0.0D);
                    pose.mulPose(Axis.XP.rotationDegrees(-125.0F));
                    pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
                } else {
                    pose.translate(0.0D, 0.09D, 0.0D);
                }
                pose.scale(SCALE, SCALE, SCALE);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay,
                        pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong() + shown);
                pose.popPose();
            }
            shown++;
        }
    }
}
