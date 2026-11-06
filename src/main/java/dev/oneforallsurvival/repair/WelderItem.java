package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineConfig;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Le poste à souder : une batterie qui paie les réparations. Accroupi, un clic sur une machine le
 * recharge ; debout, il répare avec la pièce tenue dans l'autre main. Il agit dans
 * {@code onItemUseFirst}, avant le bloc. Sa barre de durabilité montre la batterie.
 */
public class WelderItem extends Item {

    private static final int BAR_COLOR = 0xE0A030;

    public WelderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockEntity target = level.getBlockEntity(context.getClickedPos());
        if (!(target instanceof ElectricMachineBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            if (player.isSecondaryUseActive()) {
                int received = RepairActions.charge(stack, machine, context.getClickedFace());
                player.displayClientMessage(Component.translatable(received > 0
                        ? "message.oneforallsurvival.welder.charged" : "message.oneforallsurvival.welder.not_charged",
                        received), true);
            } else {
                InteractionHand other = context.getHand() == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                RepairActions.repair(player, machine, stack, player.getItemInHand(other));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new WelderEnergy(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * WelderEnergy.stored(stack) / MachineConfig.welderCapacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.welder.energy",
                WelderEnergy.stored(stack), MachineConfig.welderCapacity()));
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.welder.usage"));
    }
}
