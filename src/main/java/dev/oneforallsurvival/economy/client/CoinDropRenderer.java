package dev.oneforallsurvival.economy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.oneforallsurvival.economy.CoinDropEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Dessine une pièce au sol comme l'objet au sol : le modèle de la pièce, qui tourne et flotte un peu. */
public class CoinDropRenderer extends EntityRenderer<CoinDropEntity> {

    private final ItemRenderer itemRenderer;

    public CoinDropRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    @Override
    public void render(CoinDropEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        pose.pushPose();
        ItemStack stack = entity.getItem();
        BakedModel model = itemRenderer.getModel(stack, entity.level(), null, entity.getId());
        float bob = Mth.sin(((float) entity.getAge() + partialTick) / 10.0F + entity.bobOffs) * 0.1F + 0.1F;
        float scaleY = model.getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y();
        pose.translate(0.0F, bob + 0.25F * scaleY, 0.0F);
        pose.mulPose(Axis.YP.rotation(entity.getSpin(partialTick)));
        itemRenderer.render(stack, ItemDisplayContext.GROUND, false, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                model);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CoinDropEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
