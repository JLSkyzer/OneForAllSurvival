package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToLongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Les comptes des coffres-forts : ce que chaque joueur y a mis et où se trouve son coffre-fort, gardés par le
 * serveur (données du monde principal), par identifiant de joueur. Le contenu n'est donc jamais dans le bloc : il
 * survit à une zone déchargée, à un bloc cassé et à une absence du propriétaire. Ne se touche que depuis le fil
 * du serveur.
 *
 * <p>Point d'extension : l'impôt du maire (lot suivant) se prélèvera ici par {@link #withdraw}, au nom du compte ;
 * le crochetage du policier (lot suivant) passe par {@link dev.oneforallsurvival.economy.SafeBlock#mayAccess}. Ni
 * l'un ni l'autre n'existe encore. Les dépôts et retraits ne créent ni ne détruisent de pièces : ils n'entrent
 * pas au registre ({@link EconomyLedger} ne connaît que des créations et des destructions).
 */
public final class SafeAccounts extends SavedData {

    public static final String KEY = "oneforallsurvival_safes";
    /**
     * Plafond de pièces d'une même coupure par compte : à 10 000 cuivres la pièce, cinq coupures à ce plafond
     * tiennent encore dans un {@code long}. Hors de portée du jeu, mais un compte ne déborde jamais.
     */
    public static final long MAX_PER_COIN = 1_000_000_000_000L;

    private static final class Account {
        final long[] coins = new long[Coin.values().length];
        GlobalPos safePos;

        boolean isEmpty() {
            for (long count : coins) {
                if (count != 0L) {
                    return false;
                }
            }
            return true;
        }
    }

    private final Map<UUID, Account> accounts = new HashMap<>();

    SafeAccounts() {}

    public static SafeAccounts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SafeAccounts::load, SafeAccounts::new, KEY);
    }

    // ---------------------------------------------------------------------------------------------
    // Lecture
    // ---------------------------------------------------------------------------------------------

    /** Le nombre de pièces de chaque coupure (les cinq clés sont présentes). */
    public EnumMap<Coin, Long> balance(UUID owner) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        Account account = accounts.get(owner);
        for (Coin coin : Coin.values()) {
            out.put(coin, account == null ? 0L : account.coins[coin.ordinal()]);
        }
        return out;
    }

    public long count(UUID owner, Coin coin) {
        Account account = accounts.get(owner);
        return account == null ? 0L : account.coins[coin.ordinal()];
    }

    /** La valeur du compte, en cuivres. */
    public long total(UUID owner) {
        return CoinMath.valueOf(balance(owner));
    }

    /** Combien de pièces de cette coupure le compte peut encore recevoir. */
    public long room(UUID owner, Coin coin) {
        return MAX_PER_COIN - count(owner, coin);
    }

    /** Position du coffre-fort de ce joueur, s'il en a posé un qui tient encore. */
    public Optional<GlobalPos> safePos(UUID owner) {
        Account account = accounts.get(owner);
        return account == null ? Optional.empty() : Optional.ofNullable(account.safePos);
    }

    /** La valeur de tous les comptes, en cuivres : la « masse exacte » du tableau de bord. */
    public long totalMass() {
        long total = 0L;
        for (UUID owner : accounts.keySet()) {
            total = Math.addExact(total, total(owner));
        }
        return total;
    }

    public int accountCount() {
        return accounts.size();
    }

    // ---------------------------------------------------------------------------------------------
    // Écriture : chaque opération vérifie puis valide, d'un seul bloc
    // ---------------------------------------------------------------------------------------------

    /**
     * Ajoute des pièces au compte.
     *
     * @throws IllegalArgumentException si le nombre est négatif ou si le compte dépasserait {@link #MAX_PER_COIN}
     *         (l'appelant a vérifié {@link #room})
     */
    public void deposit(UUID owner, Coin coin, long count) {
        if (count < 0L || count > room(owner, coin)) {
            throw new IllegalArgumentException("depot de " + count + " " + coin + " : place " + room(owner, coin));
        }
        if (count == 0L) {
            return;
        }
        accounts.computeIfAbsent(owner, k -> new Account()).coins[coin.ordinal()] += count;
        setDirty();
    }

    /**
     * Retire exactement {@code count} pièces, ou rien.
     *
     * @return faux, sans rien toucher, si le nombre n'est pas strictement positif ou si le compte en a moins
     */
    public boolean withdraw(UUID owner, Coin coin, long count) {
        if (count <= 0L || count(owner, coin) < count) {
            return false;
        }
        Account account = accounts.get(owner);
        account.coins[coin.ordinal()] -= count;
        dropIfUnused(owner, account);
        setDirty();
        return true;
    }

    /** Regroupe le compte selon les valeurs vivantes de la configuration. */
    public boolean group(UUID owner) {
        return group(owner, Coin::value);
    }

    /**
     * Regroupe le compte : chaque tranche de 10 pièces devient 1 pièce de la coupure au-dessus, à valeur égale,
     * pour les seules paires dont le rapport est de 10 selon {@code value} ({@link CoinMath#group(EnumMap,
     * ToLongFunction)}). La valeur du compte ne change pas.
     *
     * @return vrai si le compte a changé
     */
    public boolean group(UUID owner, ToLongFunction<Coin> value) {
        Account account = accounts.get(owner);
        if (account == null) {
            return false;
        }
        EnumMap<Coin, Long> grouped = CoinMath.group(balance(owner), value);
        boolean changed = false;
        for (Coin coin : Coin.values()) {
            long now = grouped.getOrDefault(coin, 0L);
            if (now != account.coins[coin.ordinal()]) {
                changed = true;
                account.coins[coin.ordinal()] = now;
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Note l'emplacement du coffre-fort du joueur. */
    public void setSafePos(UUID owner, GlobalPos pos) {
        accounts.computeIfAbsent(owner, k -> new Account()).safePos = pos;
        setDirty();
    }

    /**
     * Efface l'emplacement, si c'est bien {@code pos} qui est noté : un coffre-fort copié ailleurs, puis cassé,
     * ne fait pas oublier le vrai. Le contenu n'est jamais touché.
     */
    public void clearSafePos(UUID owner, GlobalPos pos) {
        Account account = accounts.get(owner);
        if (account != null && pos.equals(account.safePos)) {
            account.safePos = null;
            dropIfUnused(owner, account);
            setDirty();
        }
    }

    /** Efface l'emplacement noté, quel qu'il soit (coffre-fort introuvable). */
    public void forgetSafePos(UUID owner) {
        Account account = accounts.get(owner);
        if (account != null && account.safePos != null) {
            account.safePos = null;
            dropIfUnused(owner, account);
            setDirty();
        }
    }

    private void dropIfUnused(UUID owner, Account account) {
        if (account.safePos == null && account.isEmpty()) {
            accounts.remove(owner);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde : les pièces sont gardées par clé de coupure, l'ordre de l'énumération peut changer
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            Account account = entry.getValue();
            CompoundTag line = new CompoundTag();
            line.putUUID("owner", entry.getKey());
            CompoundTag coins = new CompoundTag();
            for (Coin coin : Coin.values()) {
                coins.putLong(coin.key(), account.coins[coin.ordinal()]);
            }
            line.put("coins", coins);
            if (account.safePos != null) {
                line.putString("dim", account.safePos.dimension().location().toString());
                line.putLong("pos", account.safePos.pos().asLong());
            }
            list.add(line);
        }
        tag.put("accounts", list);
        return tag;
    }

    public static SafeAccounts load(CompoundTag tag) {
        SafeAccounts out = new SafeAccounts();
        for (Tag raw : tag.getList("accounts", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) raw;
            if (!line.hasUUID("owner")) {
                continue;
            }
            Account account = new Account();
            CompoundTag coins = line.getCompound("coins");
            for (Coin coin : Coin.values()) {
                account.coins[coin.ordinal()] = Math.max(0L, Math.min(MAX_PER_COIN, coins.getLong(coin.key())));
            }
            ResourceLocation dimension = line.contains("dim", Tag.TAG_STRING)
                    ? ResourceLocation.tryParse(line.getString("dim")) : null;
            if (dimension != null && line.contains("pos", Tag.TAG_LONG)) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
                account.safePos = GlobalPos.of(key, BlockPos.of(line.getLong("pos")));
            }
            if (account.safePos != null || !account.isEmpty()) {
                out.accounts.put(line.getUUID("owner"), account);
            }
        }
        return out;
    }
}
