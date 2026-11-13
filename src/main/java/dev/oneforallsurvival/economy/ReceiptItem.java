package dev.oneforallsurvival.economy;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Le reçu : un papier, empilable à 1, dont l'infobulle dit ce qui a été échangé ({@link Receipt}). */
public final class ReceiptItem extends Item {

    public ReceiptItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.addAll(Receipt.tooltip(stack));
    }
}
