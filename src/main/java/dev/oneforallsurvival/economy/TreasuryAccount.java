package dev.oneforallsurvival.economy;

import dev.oneforallsurvival.economy.stall.CommerceMath;
import dev.oneforallsurvival.economy.town.TreasuryLedger;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Le compte du trésor, gardé par le serveur (données du monde principal) : là où va la taxe sur les ventes, les
 * dépôts et l'injection de l'équipe, et d'où partent les dépenses publiques et les salaires. Le solde, le registre
 * ({@link TreasuryLedger}) et l'emplacement du bloc vivent dans le même fichier, écrit par le jeu avec le reste du
 * monde. Ne se touche que depuis le fil du serveur. Les pièces y sont des pièces qui existent : ni créées ni
 * détruites par le compte lui-même (l'injection et le retrait de l'équipe sont comptés par {@link EconomyLedger}).
 */
public final class TreasuryAccount extends SavedData {

    public static final String KEY = "oneforallsurvival_treasury";

    private final long[] coins = new long[Coin.values().length];
    private TreasuryLedger ledger = new TreasuryLedger();
    @Nullable
    private GlobalPos position;
    /** Des fonds sont arrivés depuis la dernière lecture ; transitoire, jamais sauvegardé. */
    private boolean fundsArrived;

    TreasuryAccount() {}

    public static TreasuryAccount get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TreasuryAccount::load, TreasuryAccount::new, KEY);
    }

    /**
     * Ajoute des pièces au compte, note la ligne au registre ({@code kind}, montant = valeur des pièces) et lève le
     * drapeau des fonds arrivés. Rien n'est noté pour un apport nul.
     *
     * @throws IllegalArgumentException si un nombre est négatif (rien n'est alors ajouté)
     */
    public void credit(Map<Coin, Long> added, TreasuryLedger.Kind kind, @Nullable UUID actor, String actorName,
            String reason) {
        for (Map.Entry<Coin, Long> entry : added.entrySet()) {
            if (entry.getValue() < 0L) {
                throw new IllegalArgumentException("depot de " + entry.getValue() + " " + entry.getKey());
            }
        }
        boolean changed = false;
        for (Map.Entry<Coin, Long> entry : added.entrySet()) {
            if (entry.getValue() > 0L) {
                coins[entry.getKey().ordinal()] = Math.addExact(coins[entry.getKey().ordinal()], entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            fundsArrived = true;
            note(new TreasuryLedger.Line(System.currentTimeMillis(), actor, actorName, kind,
                    SafeAccounts.saturatedValue(added), reason));
        }
    }

    public long count(Coin coin) {
        return coins[coin.ordinal()];
    }

    /** Combien de pièces de cette coupure le compte peut encore recevoir (plafond {@link CoinStore#MAX_PER_COIN}). */
    public long room(Coin coin) {
        return Math.max(0L, CoinStore.MAX_PER_COIN - coins[coin.ordinal()]);
    }

    /** Ajoute des pièces sans rien noter ; l'appelant a vérifié {@link #room} et note lui-même le mouvement. */
    public void add(Coin coin, long count) {
        if (count < 0L || count > room(coin)) {
            throw new IllegalArgumentException("ajout de " + count + " " + coin + " (place : " + room(coin) + ")");
        }
        if (count > 0L) {
            coins[coin.ordinal()] += count;
            setDirty();
        }
    }

    /** Retire exactement {@code count} pièces, ou rien. @return faux si le compte en a moins */
    public boolean remove(Coin coin, long count) {
        if (count < 0L || coins[coin.ordinal()] < count) {
            return false;
        }
        if (count > 0L) {
            coins[coin.ordinal()] -= count;
            setDirty();
        }
        return true;
    }

    /**
     * Regroupe tout le contenu : dix pièces deviennent une pièce de la coupure au-dessus, à valeur égale (valeurs
     * vivantes). Jamais au-dessus du plafond : l'excédent redescend en dizaines ; si même cela ne tient pas, rien ne
     * bouge. @return vrai si le compte a changé
     */
    public boolean group() {
        EnumMap<Coin, Long> grouped = CoinMath.group(balance(), Coin::value);
        Coin[] all = Coin.values();
        for (int i = all.length - 1; i > 0; i--) {
            long over = grouped.getOrDefault(all[i], 0L) - CoinStore.MAX_PER_COIN;
            if (over > 0L) {
                grouped.put(all[i], CoinStore.MAX_PER_COIN);
                grouped.merge(all[i - 1], over * 10L, Long::sum);
            }
        }
        boolean changed = false;
        for (Coin coin : all) {
            long now = grouped.getOrDefault(coin, 0L);
            if (now > CoinStore.MAX_PER_COIN) {
                return false;
            }
            changed |= now != coins[coin.ordinal()];
        }
        if (changed) {
            for (Coin coin : all) {
                coins[coin.ordinal()] = grouped.getOrDefault(coin, 0L);
            }
            setDirty();
        }
        return changed;
    }

    /**
     * Retire exactement cette valeur, en cassant des pièces au besoin (la monnaie rendue reste au compte). Faux, et
     * rien ne bouge, si le solde ne suffit pas ou si le montant n'est pas positif. Ne note rien : l'appelant le fait.
     */
    public boolean payValue(long amount) {
        Optional<EnumMap<Coin, Long>> after = CommerceMath.payFromBox(balance(), amount, Coin::value);
        if (after.isEmpty()) {
            return false;
        }
        for (Coin coin : Coin.values()) {
            coins[coin.ordinal()] = after.get().getOrDefault(coin, 0L);
        }
        setDirty();
        return true;
    }

    /** Le nombre de pièces de chaque coupure (les cinq clés sont présentes). */
    public EnumMap<Coin, Long> balance() {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            out.put(coin, coins[coin.ordinal()]);
        }
        return out;
    }

    /** La valeur du compte, en cuivres (jamais d'exception : un débordement donne {@link Long#MAX_VALUE}). */
    public long total() {
        return SafeAccounts.saturatedValue(balance());
    }

    public TreasuryLedger ledger() {
        return ledger;
    }

    /** Note une ligne au registre, au plafond de {@link EconomyConfig#treasuryLedgerLines()}. */
    public void note(TreasuryLedger.Line line) {
        ledger.add(line, EconomyConfig.treasuryLedgerLines());
        setDirty();
    }

    /** Où est posé le bloc du trésor, s'il l'est. */
    public Optional<GlobalPos> position() {
        return Optional.ofNullable(position);
    }

    public void setPosition(GlobalPos pos) {
        if (!pos.equals(position)) {
            position = pos;
            setDirty();
        }
    }

    /** Efface l'emplacement seulement s'il est bien celui-ci (un autre bloc l'a peut-être pris entre-temps). */
    public void clearPosition(GlobalPos pos) {
        if (pos.equals(position)) {
            position = null;
            setDirty();
        }
    }

    /** Lit et baisse le drapeau des fonds arrivés (la paie le consulte au tick suivant). Non sauvegardé. */
    public boolean takeFundsArrived() {
        boolean arrived = fundsArrived;
        fundsArrived = false;
        return arrived;
    }

    /** Les pièces sont gardées par clé de coupure : l'ordre de l'énumération peut changer. */
    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag out = new CompoundTag();
        for (Coin coin : Coin.values()) {
            out.putLong(coin.key(), coins[coin.ordinal()]);
        }
        tag.put("coins", out);
        tag.put("ledger", ledger.save());
        if (position != null) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, position).result().ifPresent(saved -> tag.put("pos", saved));
        }
        return tag;
    }

    public static TreasuryAccount load(CompoundTag tag) {
        TreasuryAccount account = new TreasuryAccount();
        CompoundTag saved = tag.getCompound("coins");
        for (Coin coin : Coin.values()) {
            account.coins[coin.ordinal()] = Math.max(0L, saved.getLong(coin.key()));
        }
        account.ledger = TreasuryLedger.load(tag.getList("ledger", Tag.TAG_COMPOUND));
        if (tag.contains("pos")) {
            GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("pos")).result()
                    .ifPresent(pos -> account.position = pos);
        }
        return account;
    }
}
