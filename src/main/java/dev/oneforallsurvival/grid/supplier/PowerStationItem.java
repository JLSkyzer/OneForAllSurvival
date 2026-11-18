package dev.oneforallsurvival.grid.supplier;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * L'objet du poste de fournisseur. Démonté, il porte son fournisseur ({@link PowerStationBlock#ITEM_SUPPLIER}) et en
 * affiche le nom dans l'infobulle ; seul l'identifiant compte à la pose, le nom n'est qu'un affichage.
 */
public final class PowerStationItem extends BlockItem {

    public PowerStationItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(PowerStationBlock.ITEM_SUPPLIER)) {
            tooltip.add(Component.translatable("tooltip.oneforallsurvival.power_station.supplier",
                    tag.getString(PowerStationBlock.ITEM_SUPPLIER_NAME)).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.oneforallsurvival.power_station.new")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
