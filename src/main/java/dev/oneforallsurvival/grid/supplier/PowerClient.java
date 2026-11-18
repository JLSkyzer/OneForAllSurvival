package dev.oneforallsurvival.grid.supplier;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * Un compteur jumelé à un fournisseur, et le joueur qu'il facture. Valeur figée, remplacée par le registre
 * ({@link PowerSuppliers}) à chaque changement. La consommation du jour vit ici, pas dans le chunk du compteur : elle
 * se facture à minuit même zone déchargée.
 *
 * @param meterSerial numéro du compteur (clé)
 * @param subscriber nom d'abonné inscrit sur le compteur au jumelage
 * @param feToday FE passés par le compteur aujourd'hui
 * @param feYesterday FE passés hier
 * @param breakers disjoncteurs jumelés à ce client
 * @param cut coupé à distance par le fournisseur
 */
public record PowerClient(UUID meterSerial, String supplierId, UUID player, String playerName, String subscriber,
        State state, long feToday, long feYesterday, Set<UUID> breakers, boolean cut) {

    /**
     * Où en est le raccordement. Le courant passe et la facture court à {@link #ACTIVE} ; {@link #RESIGNING} passe
     * encore jusqu'à la dernière facture.
     */
    public enum State {
        /** Jumelé ; le joueur n'a pas encore donné son accord. */
        AWAITING_CONSENT,
        /** Le joueur a demandé le raccordement ; le fournisseur n'a pas encore accepté. */
        AWAITING_ACCEPT,
        ACTIVE,
        /** Résilié par le client : dernière facture à la minuit suivante, puis détaché. */
        RESIGNING;

        /** Le courant passe-t-il ? */
        public boolean passes() {
            return this == ACTIVE || this == RESIGNING;
        }
    }

    public PowerClient {
        breakers = Set.copyOf(breakers);
    }

    PowerClient withFeToday(long fe) {
        return new PowerClient(meterSerial, supplierId, player, playerName, subscriber, state, fe, feYesterday, breakers,
                cut);
    }

    PowerClient withState(State next) {
        return new PowerClient(meterSerial, supplierId, player, playerName, subscriber, next, feToday, feYesterday,
                breakers, cut);
    }

    PowerClient withBreakers(Set<UUID> next) {
        return new PowerClient(meterSerial, supplierId, player, playerName, subscriber, state, feToday, feYesterday, next,
                cut);
    }

    PowerClient withCut(boolean next) {
        return new PowerClient(meterSerial, supplierId, player, playerName, subscriber, state, feToday, feYesterday,
                breakers, next);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("meter", meterSerial);
        tag.putString("supplier", supplierId);
        tag.putUUID("player", player);
        tag.putString("playerName", playerName);
        tag.putString("subscriber", subscriber);
        tag.putString("state", state.name());
        tag.putLong("feToday", feToday);
        tag.putLong("feYesterday", feYesterday);
        ListTag list = new ListTag();
        for (UUID breaker : breakers) {
            list.add(NbtUtils.createUUID(breaker));
        }
        tag.put("breakers", list);
        tag.putBoolean("cut", cut);
        return tag;
    }

    /** Relit un client ; nul si la ligne est inutilisable. */
    @Nullable
    static PowerClient load(CompoundTag tag) {
        if (!tag.hasUUID("meter") || !tag.hasUUID("player") || tag.getString("supplier").isEmpty()) {
            return null;
        }
        State state;
        try {
            state = State.valueOf(tag.getString("state").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Un état inconnu ne doit jamais laisser passer le courant ni facturer : on repart de l'accord.
            state = State.AWAITING_CONSENT;
        }
        Set<UUID> breakers = new HashSet<>();
        for (Tag entry : tag.getList("breakers", Tag.TAG_INT_ARRAY)) {
            breakers.add(NbtUtils.loadUUID(entry));
        }
        return new PowerClient(tag.getUUID("meter"), tag.getString("supplier"), tag.getUUID("player"),
                tag.getString("playerName"), tag.getString("subscriber"), state, Math.max(0L, tag.getLong("feToday")),
                Math.max(0L, tag.getLong("feYesterday")), breakers, tag.getBoolean("cut"));
    }
}
