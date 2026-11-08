package dev.oneforallsurvival.grid;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Le scellé : l'ingénieur le pose sur un disjoncteur coupé pour qu'on ne le réenclenche plus. Le geste vit dans
 * {@link BreakerBlock} ; l'objet n'a pas d'autre usage.
 */
public final class SealItem extends Item {

    public SealItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.seal").withStyle(ChatFormatting.GRAY));
    }
}
