package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.OneForAllSurvival;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * Les gardes de l'argent hors des cases : balayage des cases interdites (au cas où un mod surcharge {@code mayPlace}
 * sans appeler l'original), clic droit sur un bloc de stockage sans écran, cadres, porte-armures, allays, et boîte
 * de Shulker cassée.
 */
public final class MoneyGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Tics entre deux balayages d'un écran resté ouvert : une seconde. */
    private static final int SWEEP_PERIOD = 20;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** Blocs d'autres mods qui ouvrent un écran sans MenuProvider : le clic droit avec de l'argent y reste permis. */
    public static final TagKey<Block> MONEY_USE_ALLOWED =
            TagKey.create(Registries.BLOCK, new ResourceLocation(OneForAllSurvival.MODID, "money_use_allowed"));

    /** Branché sur le bus de Forge. */
    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sweep(player, event.getContainer());
        }
    }

    /** Une fois par seconde, tant qu'un écran autre que l'inventaire reste ouvert. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % SWEEP_PERIOD == 0 && player.containerMenu != player.inventoryMenu) {
            sweep(player, player.containerMenu);
        }
    }

    /**
     * Rend au joueur toute pièce trouvée dans une case interdite de l'écran, avec une ligne de journal portant le
     * type d'écran (pour l'ajouter à la liste). Deux sortes de cases ne sont pas touchées, car leur contenu n'est
     * pas un rangement et le rendre ferait gagner de l'argent : les cases de résultat (contenu calculé) et les
     * cases sans stockage réel (voir {@link #isRealStorage} : les écrans à cases fantômes, comme le demandeur à
     * redstone de Create, y posent une copie du filtre sans rien prendre au joueur). Une pièce trouvée dans une de
     * ces dernières n'est que signalée.
     *
     * @return le nombre de piles rendues
     */
    public static int sweep(ServerPlayer player, AbstractContainerMenu menu) {
        int returned = 0;
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !MoneyStorageRules.isMoney(stack) || MoneyStorageRules.isMoneySlot(slot)
                    || isOutput(slot)) {
                continue;
            }
            if (!isRealStorage(slot)) {
                warnOnce(player, menu, "case " + slot.index + " (" + slot.getClass().getName() + ") sans stockage "
                        + "reel (case fantome ?), laissee en place : " + stack.getItem() + " x" + stack.getCount());
                continue;
            }
            ItemStack back = stack.copy();
            String what = back.getItem() + " x" + back.getCount();
            slot.set(ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(back);
            returned++;
            warnOnce(player, menu, what + " dans la case " + slot.index + ", rendues au joueur ; ajouter l'ecran "
                    + "a extraForbiddenMenus ou corriger son mod");
        }
        return returned;
    }

    /**
     * Le contenu de la case existe-t-il vraiment ailleurs que dans l'écran ? Seuls un bloc à entité, un double
     * coffre, le coffre de l'Ender et une entité à conteneur (wagonnet, bateau) sont du stockage durable. Une case
     * fantôme (filtre) repose sur un conteneur quelconque : lui rendre sa copie créerait de l'argent.
     */
    static boolean isRealStorage(Slot slot) {
        return slot.container instanceof BlockEntity || slot.container instanceof CompoundContainer
                || slot.container instanceof PlayerEnderChestContainer || slot.container instanceof ContainerEntity;
    }

    /** Un seul avertissement par type d'écran et par joueur, pour la durée du serveur. */
    private static void warnOnce(ServerPlayer player, AbstractContainerMenu menu, String message) {
        String key = MoneyStorageRules.typeId(menu) + "/" + menu.getClass().getName() + "/" + player.getUUID();
        if (WARNED.add(key)) {
            LOGGER.warn("Economie : ecran {} ({}), joueur {} : {}", MoneyStorageRules.typeId(menu),
                    menu.getClass().getName(), player.getGameProfile().getName(), message);
        }
    }

    private static boolean isOutput(Slot slot) {
        return slot instanceof ResultSlot || slot instanceof FurnaceResultSlot || slot instanceof MerchantResultSlot
                || slot.container instanceof ResultContainer;
    }

    /**
     * Avec de l'argent en main, le clic droit est refusé sur un bloc dont l'entité expose un stockage d'objets sans
     * ouvrir d'écran (un dépôt de Create...) : ces blocs prennent l'objet tenu sans passer par une case. Un bloc dont
     * l'entité ou le bloc fournit un écran ({@code MenuProvider}) n'est pas touché : ses cases font le tri.
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!MoneyStorageRules.isMoney(event.getItemStack())) {
            return;
        }
        BlockEntity entity = event.getLevel().getBlockEntity(event.getPos());
        if (entity == null || entity instanceof MenuProvider
                || entity.getBlockState().getMenuProvider(event.getLevel(), event.getPos()) != null
                || entity.getBlockState().is(MONEY_USE_ALLOWED)) {
            return;
        }
        if (entity.getCapability(ForgeCapabilities.ITEM_HANDLER, event.getFace()).isPresent()) {
            event.setUseBlock(Result.DENY);
            event.setUseItem(Result.DENY);
        }
    }

    /** Cadres, porte-armures et allays ne reçoivent pas d'argent. */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (refuses(event.getTarget(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Le porte-armures se touche en un point précis : c'est cet évènement-là qu'il reçoit. */
    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (refuses(event.getTarget(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean refuses(Entity target, ItemStack held) {
        return (target instanceof ItemFrame || target instanceof ArmorStand || target instanceof Allay)
                && MoneyStorageRules.isMoney(held);
    }

    /**
     * Une boîte de Shulker cassée garde son contenu. Elle ne peut pas avoir reçu de pièce par une case ; si
     * l'une y est entrée par un chemin imprévu, elle y reste, mais le journal la signale.
     */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof ShulkerBoxBlock)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof ShulkerBoxBlockEntity box)) {
            return;
        }
        for (int i = 0; i < box.getContainerSize(); i++) {
            if (MoneyStorageRules.isMoney(box.getItem(i))) {
                LOGGER.warn("Economie : boite de Shulker cassee en {} avec de l'argent dedans ({} x{}), "
                        + "entree par un chemin imprevu", event.getPos(), box.getItem(i).getItem(),
                        box.getItem(i).getCount());
            }
        }
    }
}
