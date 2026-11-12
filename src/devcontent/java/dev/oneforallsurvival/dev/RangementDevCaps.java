package dev.oneforallsurvival.dev;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contenu d'essai : donne à un feu de camp un stockage d'objets (ITEM_HANDLER) sans écran, l'image d'un dépôt de
 * Create, pour que les GameTests de {@link RangementGameTests} aient un vrai stockage sans écran sans le pack.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RangementDevCaps {

    private RangementDevCaps() {}

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof CampfireBlockEntity)) {
            return;
        }
        ItemStackHandler stock = new ItemStackHandler(1);
        LazyOptional<ItemStackHandler> option = LazyOptional.of(() -> stock);
        event.addCapability(new ResourceLocation("oneforallsurvival", "depot_d_essai"), new ICapabilityProvider() {
            @Override
            public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                return cap == ForgeCapabilities.ITEM_HANDLER ? option.cast() : LazyOptional.empty();
            }
        });
    }
}
