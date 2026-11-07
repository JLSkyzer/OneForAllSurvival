package dev.oneforallsurvival.food.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

/** L'eau potable se dessine avec les textures de l'eau vanilla, teintées d'un bleu plus clair. */
public final class PotableWaterRendering implements IClientFluidTypeExtensions {

    /** Teinte (ARGB) de l'eau potable : aussi celle de la jauge de l'écran. */
    public static final int TINT = 0xFF8FDCEB;

    private static final ResourceLocation STILL = new ResourceLocation("minecraft", "block/water_still");
    private static final ResourceLocation FLOWING = new ResourceLocation("minecraft", "block/water_flow");
    private static final ResourceLocation OVERLAY = new ResourceLocation("minecraft", "block/water_overlay");

    @Override
    public ResourceLocation getStillTexture() {
        return STILL;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return FLOWING;
    }

    @Override
    public ResourceLocation getOverlayTexture() {
        return OVERLAY;
    }

    @Override
    public int getTintColor() {
        return TINT;
    }
}
