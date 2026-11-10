package dev.oneforallsurvival.town.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

/** L'eau chaude se dessine avec les textures de l'eau vanilla, teintées d'orange. */
public final class HotWaterRendering implements IClientFluidTypeExtensions {

    /** Teinte (ARGB) de l'eau chaude : aussi celle de la jauge de l'écran. */
    public static final int TINT = 0xFFFF9B4A;

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
