package dev.oneforallsurvival.economy.trade;

import dev.oneforallsurvival.economy.CommerceDelivery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * La garde d'un échange vit avec son propriétaire : à chaque changement, son contenu est recopié dans les données
 * persistantes du joueur ({@code ForgeData/PlayerPersisted/oneforallsurvival_escrow}). Minecraft l'écrit donc avec son
 * inventaire, dans le même fichier et au même instant (sauvegarde automatique, déconnexion) ; le mod n'écrit rien
 * lui-même sur le disque. Après un arrêt brutal, un objet posé est soit dans l'inventaire, soit dans la garde de ce
 * playerdata, jamais aux deux.
 *
 * <p>Un joueur qui se connecte avec une garde (le serveur s'est arrêté pendant son échange) la reçoit, une seule fois :
 * elle est vidée dans le même playerdata que l'inventaire qui la reçoit. Fil du serveur seulement.
 */
public final class TradeEscrow {

    /** Clé de la garde sous {@code PlayerPersisted} (recopiée par Forge à la réapparition). */
    public static final String KEY = "oneforallsurvival_escrow";

    private TradeEscrow() {}

    /** Recopie le contenu de cette garde chez son propriétaire (vide : la clé disparaît). */
    static void write(ServerPlayer owner, Container offer) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < offer.getContainerSize(); slot++) {
            ItemStack stack = offer.getItem(slot);
            if (!stack.isEmpty()) {
                list.add(stack.save(new CompoundTag()));
            }
        }
        CompoundTag persisted = persisted(owner);
        if (list.isEmpty()) {
            persisted.remove(KEY);
        } else {
            persisted.put(KEY, list);
        }
        owner.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Vide cette garde, efface sa copie chez son propriétaire et rend son contenu (copies). */
    static List<ItemStack> close(ServerPlayer owner, Container offer) {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < offer.getContainerSize(); slot++) {
            ItemStack stack = offer.getItem(slot);
            if (!stack.isEmpty()) {
                out.add(stack.copy());
            }
            offer.setItem(slot, ItemStack.EMPTY);
        }
        CompoundTag persisted = persisted(owner);
        persisted.remove(KEY);
        owner.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return out;
    }

    /**
     * Rend à ce joueur la garde que porte son playerdata, s'il en porte une, et l'efface : inventaire, sinon coffre-fort
     * (pièces) ou attente ({@link CommerceDelivery#giveBack}).
     */
    static void recover(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(KEY, Tag.TAG_LIST)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (Tag raw : persisted.getList(KEY, Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) raw);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        persisted.remove(KEY);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        if (!items.isEmpty()) {
            CommerceDelivery.giveBack(player, items);
        }
    }

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }
}
