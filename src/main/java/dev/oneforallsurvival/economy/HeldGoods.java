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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Ce que le serveur garde pour quelqu'un : la <b>garde</b> d'un échange en cours (les objets posés quittent
 * l'inventaire et vivent ici) et les livraisons <b>en attente</b> (objets et pièces qui n'ont pas pu arriver).
 * Sauvegardé avec le monde, par identifiant de joueur. Ne se touche que depuis le fil du serveur.
 *
 * <p>Au chargement, toute garde retrouvée passe en attente de son propriétaire : le serveur s'est arrêté pendant
 * un échange, et personne d'autre ne la réclamera. La garde est écrite avec le monde (au même tick que les joueurs)
 * et, en plus, à chaque déconnexion avant le playerdata du joueur ({@link #saveNow}) : rien ne se double.
 */
public final class HeldGoods extends SavedData {

    public static final String KEY = "oneforallsurvival_held";

    /** Ce qui attend un joueur : des objets (bourses comprises) et des pièces par coupure. */
    public record Pending(List<ItemStack> items, EnumMap<Coin, Long> coins) {
        public boolean isEmpty() {
            return items.isEmpty() && coins.isEmpty();
        }
    }

    private final Map<UUID, SimpleContainer> escrows = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    /** Vrai si le chargement a converti des gardes : le prochain {@link #get} le note à sauvegarder. */
    private boolean recovered;

    HeldGoods() {}

    public static HeldGoods get(MinecraftServer server) {
        HeldGoods held = server.overworld().getDataStorage().computeIfAbsent(HeldGoods::load, HeldGoods::new, KEY);
        if (held.recovered) {
            held.recovered = false;
            held.setDirty();
        }
        return held;
    }

    /** Le fichier de la garde : {@code <monde>/data/oneforallsurvival_held.dat} (stockage de l'Overworld). */
    public static java.nio.file.Path file(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(KEY + ".dat");
    }

    /**
     * Écrit tout de suite la garde sur le disque si elle a changé depuis la dernière écriture. Appelé à chaque
     * déconnexion, avant que Minecraft n'écrive le playerdata du joueur : sinon, après un arrêt brutal, une garde
     * déjà rendue (ou livrée par un échange conclu) reviendrait de l'ancien fichier en plus de ce que le playerdata
     * contient, et l'objet serait doublé.
     */
    public void saveNow(MinecraftServer server) {
        if (isDirty()) {
            save(file(server).toFile());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Garde
    // ---------------------------------------------------------------------------------------------

    /**
     * La garde de ce joueur, créée de {@code size} cases si elle n'existe pas (une seule par joueur : si elle
     * existe déjà, c'est elle qui est rendue). Chaque changement de son contenu marque la sauvegarde.
     */
    public SimpleContainer openEscrow(UUID owner, int size) {
        SimpleContainer existing = escrows.get(owner);
        if (existing != null) {
            return existing;
        }
        SimpleContainer container = new SimpleContainer(size);
        container.addListener(changed -> setDirty());
        escrows.put(owner, container);
        setDirty();
        return container;
    }

    /** Vide et oublie la garde de ce joueur, et rend son contenu (rien si elle n'existe pas). */
    public List<ItemStack> closeEscrow(UUID owner) {
        SimpleContainer container = escrows.remove(owner);
        List<ItemStack> out = new ArrayList<>();
        if (container == null) {
            return out;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                out.add(stack.copy());
            }
            container.setItem(slot, ItemStack.EMPTY);
        }
        setDirty();
        return out;
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
            total = saturatedAdd(total, SafeAccounts.saturatedValue(line.coins()));
            for (ItemStack stack : line.items()) {
                if (Purse.isPurse(stack)) {
                    total = saturatedAdd(total, Purse.total(stack));
                }
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
        ListTag escrowList = new ListTag();
        for (Map.Entry<UUID, SimpleContainer> entry : escrows.entrySet()) {
            CompoundTag line = new CompoundTag();
            line.putUUID("owner", entry.getKey());
            line.put("items", saveStacks(containerStacks(entry.getValue())));
            escrowList.add(line);
        }
        tag.put("escrows", escrowList);
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
        // Une garde encore là au chargement : le serveur s'est arrêté pendant un échange. Elle devient une
        // attente de son propriétaire, une seule fois (elle n'est pas gardée en mémoire).
        for (Tag raw : tag.getList("escrows", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) raw;
            if (!line.hasUUID("owner")) {
                continue;
            }
            List<ItemStack> items = loadStacks(line.getList("items", Tag.TAG_COMPOUND));
            if (!items.isEmpty()) {
                held.addPending(line.getUUID("owner"), items, Map.of());
            }
            held.recovered = true;
        }
        return held;
    }

    private static List<ItemStack> containerStacks(SimpleContainer container) {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
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
