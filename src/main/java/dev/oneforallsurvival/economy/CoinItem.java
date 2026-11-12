package dev.oneforallsurvival.economy;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Une pièce. L'infobulle donne sa valeur et, pour une pile, le total. */
public class CoinItem extends Item {

    private final Coin coin;

    public CoinItem(Coin coin, Properties properties) {
        super(properties);
        this.coin = coin;
    }

    public Coin coin() {
        return coin;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.coin.value", CoinMath.format(coin.value()))
                .withStyle(ChatFormatting.GRAY));
        if (stack.getCount() > 1) {
            tooltip.add(Component.translatable("tooltip.oneforallsurvival.coin.total",
                    CoinMath.format(CoinMath.valueOf(coin, stack.getCount()))).withStyle(ChatFormatting.GRAY));
        }
    }
}
