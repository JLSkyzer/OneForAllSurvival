package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.economy.Coin;
import java.util.EnumMap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Un opérateur de téléphonie, tel que le registre ({@link PhoneOperators}) le garde. Valeur figée : le registre la
 * remplace à chaque changement, et {@link #till()} rend une copie. La caisse n'est jamais dans le bloc d'une antenne.
 *
 * @param id identifiant stable (12 caractères)
 * @param name nom affiché, unique sans casse ({@code PowerSuppliers.validName})
 * @param founder le joueur qui l'a fondé (un opérateur par joueur)
 * @param founderName son nom, tel qu'il était à la fondation ou au dernier changement de fondateur
 * @param tariff tarif du jour, en cuivres
 * @param nextTariff tarif du lendemain, s'il a été changé dans la journée
 * @param till la caisse, coupures nulles omises
 * @param mainAntenna l'antenne principale, nulle tant qu'aucune n'est posée
 * @param createdAt instant de la fondation ({@code EconomyClock.now()})
 */
public record PhoneOperator(String id, String name, UUID founder, String founderName, long tariff,
        @Nullable Long nextTariff, EnumMap<Coin, Long> till, @Nullable UUID mainAntenna, long createdAt) {

    public PhoneOperator {
        till = copy(till);
    }

    /** Une copie de la caisse : le registre seul la change. */
    @Override
    public EnumMap<Coin, Long> till() {
        return copy(till);
    }

    long tillCount(Coin coin) {
        return till.getOrDefault(coin, 0L);
    }

    private static EnumMap<Coin, Long> copy(@Nullable EnumMap<Coin, Long> source) {
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

    PhoneOperator withFounder(UUID newFounder, String newFounderName) {
        return new PhoneOperator(id, name, newFounder, newFounderName, tariff, nextTariff, till, mainAntenna, createdAt);
    }

    PhoneOperator withTill(EnumMap<Coin, Long> newTill) {
        return new PhoneOperator(id, name, founder, founderName, tariff, nextTariff, newTill, mainAntenna, createdAt);
    }

    PhoneOperator withNextTariff(@Nullable Long newNextTariff) {
        return new PhoneOperator(id, name, founder, founderName, tariff, newNextTariff, till, mainAntenna, createdAt);
    }

    /** Le jour clos : le tarif donné entre en vigueur et celui du lendemain est vidé. */
    PhoneOperator withDayClosed(long newTariff) {
        return new PhoneOperator(id, name, founder, founderName, newTariff, null, till, mainAntenna, createdAt);
    }

    PhoneOperator withMainAntenna(@Nullable UUID antenna) {
        return new PhoneOperator(id, name, founder, founderName, tariff, nextTariff, till, antenna, createdAt);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        tag.putUUID("founder", founder);
        tag.putString("founderName", founderName);
        tag.putLong("tariff", tariff);
        if (nextTariff != null) {
            tag.putLong("nextTariff", nextTariff);
        }
        // Par clé de coupure : l'ordre de l'énumération peut changer.
        CompoundTag coins = new CompoundTag();
        till.forEach((coin, count) -> coins.putLong(coin.key(), count));
        tag.put("till", coins);
        if (mainAntenna != null) {
            tag.putUUID("main", mainAntenna);
        }
        tag.putLong("createdAt", createdAt);
        return tag;
    }

    /** Relit un opérateur ; nul si la ligne est inutilisable (sans identifiant, nom ou fondateur). */
    @Nullable
    static PhoneOperator load(CompoundTag tag) {
        String id = tag.getString("id");
        String name = tag.getString("name");
        if (id.isEmpty() || name.isEmpty() || !tag.hasUUID("founder")) {
            return null;
        }
        EnumMap<Coin, Long> till = new EnumMap<>(Coin.class);
        CompoundTag coins = tag.getCompound("till");
        for (Coin coin : Coin.values()) {
            long n = Math.max(0L, coins.getLong(coin.key()));
            if (n > 0L) {
                till.put(coin, n);
            }
        }
        return new PhoneOperator(id, name, tag.getUUID("founder"), tag.getString("founderName"),
                Math.max(0L, tag.getLong("tariff")),
                tag.contains("nextTariff", 99) ? Math.max(0L, tag.getLong("nextTariff")) : null, till,
                tag.hasUUID("main") ? tag.getUUID("main") : null, Math.max(0L, tag.getLong("createdAt")));
    }
}
