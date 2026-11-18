package dev.oneforallsurvival.grid.supplier;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.SafeAccounts;
import java.util.EnumMap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/**
 * Un fournisseur d'électricité, tel que le registre ({@link PowerSuppliers}) le garde. Valeur figée : le registre la
 * remplace à chaque changement, et {@link #till()} rend une copie. La caisse n'est jamais dans le bloc du poste.
 *
 * @param id identifiant stable, porté par l'objet du poste démonté
 * @param name nom affiché, de 1 à {@link PowerSuppliers#MAX_NAME} caractères, unique sans casse
 * @param price prix du jour, en cuivres par tranche de {@code powerPriceUnitFe}
 * @param subscription abonnement du jour, en cuivres
 * @param nextPrice prix du lendemain, s'il a été changé dans la journée
 * @param nextSubscription abonnement du lendemain, s'il a été changé dans la journée
 * @param station emplacement du poste posé, s'il l'est
 * @param till la caisse, coupures nulles omises
 * @param outToday FE sortis du poste aujourd'hui
 * @param outYesterday FE sortis du poste hier
 * @param metersYesterday somme des compteurs du fournisseur hier
 */
public record PowerSupplier(String id, String name, UUID founder, String founderName, long price, long subscription,
        @Nullable Long nextPrice, @Nullable Long nextSubscription, @Nullable GlobalPos station, EnumMap<Coin, Long> till,
        long outToday, long outYesterday, long metersYesterday) {

    public PowerSupplier {
        till = copy(till);
    }

    /** Une copie de la caisse : le registre seul la change. */
    @Override
    public EnumMap<Coin, Long> till() {
        return copy(till);
    }

    /** La valeur de la caisse, en cuivres (jamais d'exception : {@link Long#MAX_VALUE} au-delà). */
    public long tillValue() {
        return SafeAccounts.saturatedValue(till);
    }

    long tillCount(Coin coin) {
        return till.getOrDefault(coin, 0L);
    }

    private static EnumMap<Coin, Long> copy(EnumMap<Coin, Long> source) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        if (source != null) {
            source.forEach((coin, count) -> {
                if (count != null && count > 0L) {
                    out.put(coin, count);
                }
            });
        }
        return out;
    }

    PowerSupplier withStation(@Nullable GlobalPos pos) {
        return new PowerSupplier(id, name, founder, founderName, price, subscription, nextPrice, nextSubscription, pos,
                till, outToday, outYesterday, metersYesterday);
    }

    PowerSupplier withTill(EnumMap<Coin, Long> newTill) {
        return new PowerSupplier(id, name, founder, founderName, price, subscription, nextPrice, nextSubscription,
                station, newTill, outToday, outYesterday, metersYesterday);
    }

    PowerSupplier withOutToday(long out) {
        return new PowerSupplier(id, name, founder, founderName, price, subscription, nextPrice, nextSubscription,
                station, till, out, outYesterday, metersYesterday);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putUUID("founder", founder);
        tag.putString("founderName", founderName);
        tag.putLong("price", price);
        tag.putLong("subscription", subscription);
        if (nextPrice != null) {
            tag.putLong("nextPrice", nextPrice);
        }
        if (nextSubscription != null) {
            tag.putLong("nextSubscription", nextSubscription);
        }
        if (station != null) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, station).result().ifPresent(saved -> tag.put("station", saved));
        }
        // Par clé de coupure : l'ordre de l'énumération peut changer.
        CompoundTag coins = new CompoundTag();
        till.forEach((coin, count) -> coins.putLong(coin.key(), count));
        tag.put("till", coins);
        tag.putLong("outToday", outToday);
        tag.putLong("outYesterday", outYesterday);
        tag.putLong("metersYesterday", metersYesterday);
        return tag;
    }

    /** Relit un fournisseur ; nul si la ligne est inutilisable (sans identifiant, nom ou fondateur). */
    @Nullable
    static PowerSupplier load(CompoundTag tag) {
        String id = tag.getString("id");
        String name = tag.getString("name");
        if (id.isEmpty() || name.isEmpty() || !tag.hasUUID("founder")) {
            return null;
        }
        GlobalPos station = tag.contains("station")
                ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("station")).result().orElse(null) : null;
        EnumMap<Coin, Long> till = new EnumMap<>(Coin.class);
        CompoundTag coins = tag.getCompound("till");
        for (Coin coin : Coin.values()) {
            long n = Math.max(0L, coins.getLong(coin.key()));
            if (n > 0L) {
                till.put(coin, n);
            }
        }
        return new PowerSupplier(id, name, tag.getUUID("founder"), tag.getString("founderName"),
                Math.max(0L, tag.getLong("price")), Math.max(0L, tag.getLong("subscription")),
                tag.contains("nextPrice") ? Math.max(0L, tag.getLong("nextPrice")) : null,
                tag.contains("nextSubscription") ? Math.max(0L, tag.getLong("nextSubscription")) : null,
                station, till, Math.max(0L, tag.getLong("outToday")), Math.max(0L, tag.getLong("outYesterday")),
                Math.max(0L, tag.getLong("metersYesterday")));
    }
}
