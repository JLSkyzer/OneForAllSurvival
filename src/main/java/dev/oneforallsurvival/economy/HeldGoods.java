package dev.oneforallsurvival.economy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Les livraisons <b>en attente</b> : objets et pièces qui n'ont pas pu arriver chez un joueur (absent, mort, inventaire
 * plein). Sauvegardé avec le monde seulement (marqué sale à chaque changement), par identifiant de joueur. Ne se
 * touche que depuis le fil du serveur. La garde d'un échange en cours n'est pas ici : elle vit dans le playerdata de
 * son propriétaire ({@code TradeEscrow}).
 */
public final class HeldGoods extends SavedData {

    public static final String KEY = "oneforallsurvival_held";

    /** Ce qui attend un joueur : des objets (bourses comprises) et des pièces par coupure. */
    public record Pending(List<ItemStack> items, EnumMap<Coin, Long> coins) {
        public boolean isEmpty() {
            return items.isEmpty() && coins.isEmpty();
        }
    }

    private final Map<UUID, Pending> pending = new HashMap<>();

    HeldGoods() {}

    public static HeldGoods get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HeldGoods::load, HeldGoods::new, KEY);
    }

    // ---------------------------------------------------------------------------------------------
    // En attente
    // ---------------------------------------------------------------------------------------------

    /**
     * Met en attente pour ce joueur. Les pièces posées comme objets sont rangées avec les autres pièces ; les
     * piles vides sont ignorées.
     */
    public void addPending(UUID owner, List<ItemStack> items, Map<Coin, Long> coins) {
        Pending line = pending.computeIfAbsent(owner, k -> new Pending(new ArrayList<>(), new EnumMap<>(Coin.class)));
        boolean changed = false;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            Optional<Coin> coin = Coin.fromItem(stack.getItem());
            if (coin.isPresent()) {
                line.coins().merge(coin.get(), (long) stack.getCount(), Long::sum);
            } else {
                line.items().add(stack.copy());
            }
            changed = true;
        }
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            if (entry.getValue() > 0L) {
                line.coins().merge(entry.getKey(), entry.getValue(), Long::sum);
                changed = true;
            }
        }
        if (line.isEmpty()) {
            pending.remove(owner);
        }
        if (changed) {
            setDirty();
        }
    }

    public boolean hasPending(UUID owner) {
        return pending.containsKey(owner);
    }

    /** Une copie de ce qui attend ce joueur (vide s'il n'attend rien). */
    public Pending pendingFor(UUID owner) {
        Pending line = pending.get(owner);
        if (line == null) {
            return new Pending(new ArrayList<>(), new EnumMap<>(Coin.class));
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : line.items()) {
            items.add(stack.copy());
        }
        return new Pending(items, new EnumMap<>(line.coins()));
    }

    /** Retire et rend ce qui attend ce joueur (rien s'il n'attend rien). */
    public Pending takePending(UUID owner) {
        Pending line = pending.remove(owner);
        if (line == null) {
            return new Pending(new ArrayList<>(), new EnumMap<>(Coin.class));
        }
        setDirty();
        return line;
    }

    /** Les joueurs qui ont quelque chose en attente. */
    public List<UUID> pendingOwners() {
        return new ArrayList<>(pending.keySet());
    }

    /**
     * La valeur, en cuivres, des pièces et des bourses en attente (jamais d'exception : un débordement donne
     * {@link Long#MAX_VALUE}). Une pièce en attente existe encore : elle n'est ni créée ni détruite.
     */
    public long pendingMass() {
        long total = 0L;
        for (Pending line : pending.values()) {
            total = saturatedAdd(total, valueOf(line));
        }
        return total;
    }

    /** L'attente d'un joueur résumée pour l'équipe : valeur des pièces et des bourses, nombre d'objets. */
    public record OwnerPending(UUID owner, long value, long items) {}

    /** L'attente de chaque joueur, de la plus grosse valeur à la plus petite (puis du plus d'objets au moins). */
    public List<OwnerPending> pendingByOwner() {
        List<OwnerPending> out = new ArrayList<>();
        for (Map.Entry<UUID, Pending> entry : pending.entrySet()) {
            long items = 0L;
            for (ItemStack stack : entry.getValue().items()) {
                items += stack.getCount();
            }
            out.add(new OwnerPending(entry.getKey(), valueOf(entry.getValue()), items));
        }
        out.sort(java.util.Comparator.comparingLong(OwnerPending::value).reversed()
                .thenComparing(java.util.Comparator.comparingLong(OwnerPending::items).reversed()));
        return out;
    }

    private static long valueOf(Pending line) {
        long total = SafeAccounts.saturatedValue(line.coins());
        for (ItemStack stack : line.items()) {
            if (Purse.isPurse(stack)) {
                total = saturatedAdd(total, Purse.total(stack));
            }
        }
        return total;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag pendingList = new ListTag();
        for (Map.Entry<UUID, Pending> entry : pending.entrySet()) {
            CompoundTag line = new CompoundTag();
            line.putUUID("owner", entry.getKey());
            line.put("items", saveStacks(entry.getValue().items()));
            CompoundTag coins = new CompoundTag();
            for (Map.Entry<Coin, Long> coin : entry.getValue().coins().entrySet()) {
                coins.putLong(coin.getKey().key(), coin.getValue());
            }
            line.put("coins", coins);
            pendingList.add(line);
        }
        tag.put("pending", pendingList);
        return tag;
    }

    public static HeldGoods load(CompoundTag tag) {
        HeldGoods held = new HeldGoods();
        for (Tag raw : tag.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) raw;
            if (!line.hasUUID("owner")) {
                continue;
            }
            EnumMap<Coin, Long> coins = new EnumMap<>(Coin.class);
            CompoundTag saved = line.getCompound("coins");
            for (Coin coin : Coin.values()) {
                long count = saved.getLong(coin.key());
                if (count > 0L) {
                    coins.put(coin, count);
                }
            }
            held.addPending(line.getUUID("owner"), loadStacks(line.getList("items", Tag.TAG_COMPOUND)), coins);
        }
        return held;
    }

    private static ListTag saveStacks(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            list.add(stack.save(new CompoundTag()));
        }
        return list;
    }

    private static List<ItemStack> loadStacks(ListTag list) {
        List<ItemStack> out = new ArrayList<>();
        for (Tag raw : list) {
            ItemStack stack = ItemStack.of((CompoundTag) raw);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }
}
