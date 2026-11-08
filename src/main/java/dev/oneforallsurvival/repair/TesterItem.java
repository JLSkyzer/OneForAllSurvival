package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Le testeur : un clic droit sur une machine du mod affiche son rapport ; sur un compteur, il en fait le relevé
 * (réservé à l'ingénieur, voir {@link MeterBlockEntity#readBy}). Il agit dans
 * {@code onItemUseFirst}, avant le bloc, pour ne pas ouvrir l'interface de la machine.
 */
public class TesterItem extends Item {

    public TesterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MeterBlockEntity meter) {
            if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer player) {
                meter.readBy(player);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ElectricMachineBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            for (Component line : MachineReport.lines(machine)) {
                player.sendSystemMessage(line);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
