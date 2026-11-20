package dev.oneforallsurvival.phone.net;

import java.util.List;
import java.util.Optional;
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
 * L'objet de l'antenne. Démontée, elle porte l'identifiant de son opérateur ({@link #ITEM_OPERATOR}) et en affiche le
 * nom dans l'infobulle ; seul l'identifiant compte à la pose ({@link AntennaBlock}), le nom n'est qu'un affichage.
 */
public final class AntennaItem extends BlockItem {

    /** Clés de l'objet : l'identifiant de l'opérateur (seul compté) et son nom (pour l'infobulle). */
    public static final String ITEM_OPERATOR = "oneforallsurvival_operator";
    public static final String ITEM_OPERATOR_NAME = "oneforallsurvival_operator_name";

    public AntennaItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** L'identifiant de l'opérateur porté par l'objet, s'il y en a un. */
    public static Optional<String> operatorOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ITEM_OPERATOR)) {
            return Optional.empty();
        }
        String id = tag.getString(ITEM_OPERATOR);
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }

    /** Écrit l'opérateur sur l'objet d'une antenne démontée. */
    public static void markOperator(ItemStack stack, PhoneOperator operator) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(ITEM_OPERATOR, operator.id());
        tag.putString(ITEM_OPERATOR_NAME, operator.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(ITEM_OPERATOR)) {
            tooltip.add(Component.translatable("tooltip.oneforallsurvival.antenna.operator",
                    tag.getString(ITEM_OPERATOR_NAME)).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.oneforallsurvival.antenna.new").withStyle(ChatFormatting.GRAY));
        }
    }
}
