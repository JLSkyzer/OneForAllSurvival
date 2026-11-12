package dev.oneforallsurvival.economy;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * La bourse : un objet qui garde un nombre de pièces par coupure ({@link Purse}). Clic droit en main : l'écran. Elle
 * ne s'empile pas. L'infobulle donne le total.
 *
 * <p>Jetée, une bourse qui contient des pièces devient une entité du mod, comme une pièce au sol
 * ({@link CoinDropEntity}) : sans cela un entonnoir, un tapis de Create, un aimant ou un allay la verrait comme un
 * objet ordinaire et la rangerait. Une bourse vide reste un objet au sol ordinaire.
 */
public class PurseItem extends Item {

    public PurseItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.purse.total", CoinMath.format(Purse.total(stack)))
                .withStyle(ChatFormatting.GRAY));
    }

    /** Le clic droit ouvre l'écran, qui retrouve la bourse par sa case dans l'inventaire du joueur. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : OFF_HAND_SLOT;
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, p) -> new PurseMenu(id, inventory, slot),
                            Component.translatable(getDescriptionId())),
                    buf -> buf.writeVarInt(slot));
        }
        return InteractionResultHolder.success(stack);
    }

    /** La case de la main gauche dans {@link Inventory#getItem} : les 36 cases, puis les 4 d'armure. */
    public static final int OFF_HAND_SLOT = Inventory.INVENTORY_SIZE + 4;

    /** Comme une pièce : une bourse n'entre pas dans une sacoche, un sac ou une boîte qui range sans passer par une case. */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Nullable
    @Override
    public Entity createEntity(Level level, Entity location, ItemStack stack) {
        // Côté client, l'objet reste un objet : le serveur envoie lui-même l'entité. Une bourse vide reste un objet.
        if (level.isClientSide || !(location instanceof ItemEntity item) || stack.isEmpty() || Purse.total(stack) <= 0L) {
            return null;
        }
        return CoinDropEntity.replacingPurse(level, item, stack.copy());
    }
}
