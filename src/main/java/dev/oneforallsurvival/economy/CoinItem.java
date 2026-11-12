package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** Une pièce. L'infobulle donne sa valeur et, pour une pile, le total. */
public class CoinItem extends Item {

    private static final Logger LOGGER = LogUtils.getLogger();

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

    /**
     * Filet d'exécution (serveur). Le jeu appelle ce crochet sur la pile fabriquée, avant de la donner : au clic
     * sur la case résultat (la pile est celle du curseur) et au maj-clic de la table (la pile est celle de la
     * case, avant son déplacement). La vider annule le gain ; au maj-clic rien n'est consommé, au clic simple
     * les ingrédients le sont déjà. Ne couvre pas une machine qui ne passe pas par ce crochet, ni le maj-clic de la grille 2x2 du joueur, où
     * le jeu déplace la pile avant d'appeler le crochet : le refus y est journalisé sans retirer les pièces
     * (à fermer en tâche 3).
     */
    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        if (level.isClientSide || CoinRecipeGuard.craftAllowed(player.containerMenu, level, level.getRecipeManager())) {
            return;
        }
        LOGGER.warn("Economie : {} x{} fabriquees par {} hors change dans {}, gain annule", coin.key(),
                stack.getCount(), player.getGameProfile().getName(), player.containerMenu.getClass().getName());
        stack.setCount(0);
    }
}
