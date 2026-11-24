package dev.oneforallsurvival.phone.net;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.debit.DailyDebit;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * La clôture de minuit du téléphone (lot T2, tâche 9), émetteur du jour {@value #KIND} du prélèvement
 * ({@link DailyDebit}) : <b>elle ne facture rien</b> à ce lot. Elle fait entrer en vigueur les tarifs du lendemain
 * ({@link PhoneOperators#applyNextTariffs}, plafond du maire appliqué), prévient le fondateur d'un tarif ramené au plafond
 * et les titulaires d'un changement (tout de suite s'ils sont en ligne, sinon à leur connexion), puis clôt la
 * consommation du jour des antennes ({@link PhoneOperators#closeAntennaDay}).
 *
 * <p><b>Pour le lot T6</b> : les factures du jour de l'abonnement s'émettent ici, au tarif du jour qui se clôt,
 * <b>avant</b> {@code applyNextTariffs} ; sinon le jour clos serait facturé au tarif du lendemain.
 *
 * <p>L'horloge est celle de {@link DailyDebit} : un jour n'est clos qu'une fois, une horloge qui recule ne clôt rien, et
 * le rattrapage après un arrêt du serveur est borné ({@code payMaxMissedDays}). Le registre est celui du serveur pour
 * {@link #SERVER}, ou celui donné au constructeur (essais). Fil du serveur.
 */
public final class OperatorDay implements DailyDebit.DayIssuer {

    public static final String KIND = "phone";
    public static final OperatorDay SERVER = new OperatorDay(null);

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private final PhoneOperators operators;

    /** @param operators le registre des opérateurs, ou nul pour celui du serveur */
    public OperatorDay(@Nullable PhoneOperators operators) {
        this.operators = operators;
    }

    /** Inscrit cette clôture comme émetteur du jour du genre {@value #KIND} (remplace la précédente). */
    public static void install(OperatorDay day) {
        DailyDebit.registerIssuer(KIND, day);
    }

    private PhoneOperators registry(MinecraftServer server) {
        return operators != null ? operators : PhoneOperators.get(server);
    }

    @Override
    public void issue(MinecraftServer server, LocalDate day, long now) {
        PhoneOperators registry = registry(server);
        // Lot T6 : les factures du jour, au tarif du jour, s'émettent ici, avant les tarifs du lendemain.
        List<PhoneOperators.DayChange> changes = registry.applyNextTariffs();
        for (PhoneOperators.DayChange change : changes) {
            Optional<PhoneOperator> op = registry.byId(change.operatorId());
            if (op.isEmpty()) {
                continue;
            }
            try {
                announce(server, registry, op.get(), change);
            } catch (RuntimeException e) {
                LOGGER.warn("Telephone : avis du tarif de {} en echec", op.get().name(), e);
            }
        }
        registry.closeAntennaDay();
        LOGGER.info("Telephone : cloture du {} : {} tarif(s) change(s), consommation des antennes remise a zero", day,
                changes.size());
    }

    private static void announce(MinecraftServer server, PhoneOperators registry, PhoneOperator op,
            PhoneOperators.DayChange change) {
        if (change.capped()) {
            OperatorActions.notify(server, registry, op.founder(), Component.translatable(
                    OperatorActions.MESSAGE + "tariff_capped", op.name(), CoinMath.format(change.after())));
            LOGGER.info("Telephone : tarif de {} ramene au plafond ({} cuivres)", op.name(), change.after());
        }
        if (change.before() != change.after()) {
            Set<UUID> told = new HashSet<>();
            for (SimCard card : registry.cardsOf(op.id())) {
                if (card.holder() != null && card.state() != SimCard.State.DEACTIVATED && told.add(card.holder())) {
                    OperatorActions.notify(server, registry, card.holder(), Component.translatable(
                            OperatorActions.MESSAGE + "tariff_changed", op.name(), CoinMath.format(change.after())));
                }
            }
        }
    }

    /** À la connexion : les avis du téléphone gardés pendant l'absence (demandes de carte, réponses, tarifs). */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (Component notice : PhoneOperators.get(player.server).takeNotices(player.getUUID())) {
                player.sendSystemMessage(notice);
            }
        }
    }
}
