package dev.oneforallsurvival.phone.net;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;

/**
 * Pourquoi un appel ne passe pas ({@link PhoneNetwork#route}), et ce que l'appelant en lit ({@link #describe()}).
 *
 * <p>Le texte n'en dit jamais plus que ce que l'appelant a le droit de savoir : seule une antenne hors tension de
 * l'appelé nomme quelque chose, et c'est l'opérateur (ni son fondateur, ni la position de l'antenne, ni l'endroit où
 * se tient l'appelé).
 *
 * @param reason la raison
 * @param operatorName le nom de l'opérateur de l'appelé pour {@link Reason#CALLEE_ANTENNA_DOWN} ; ignoré sinon
 */
public record RelayFailure(Reason reason, String operatorName) {

    public enum Reason {
        /** L'appelant n'a aucune antenne en service de son opérateur à portée. */
        CALLER_NO_SIGNAL,
        /** L'opérateur de la carte de l'appelant n'existe plus. */
        CALLER_OPERATOR_REMOVED,
        /** L'opérateur de la carte de l'appelé n'existe plus. */
        CALLEE_OPERATOR_REMOVED,
        /** L'appelé est à portée d'antennes de son opérateur, mais aucune n'est en service. */
        CALLEE_ANTENNA_DOWN,
        /** L'appelé n'est à portée d'aucune antenne de son opérateur. */
        CALLEE_NO_COVERAGE,
        /** Le téléphone de l'appelant est en mode avion. */
        CALLER_AIRPLANE,
        /** L'appelant n'a pas de carte active (aucune, morte, suspendue ou désactivée). */
        CALLER_SIM_INACTIVE,
        /** La carte de l'appelé est suspendue. */
        CALLEE_SIM_INACTIVE,
        /** Aucune ligne n'a ce numéro (jamais émis, mal écrit, ou carte désactivée). */
        CALLEE_UNKNOWN;

        /** La clé de langue : {@code message.oneforallsurvival.relay.<raison>}. */
        public String langKey() {
            return "message.oneforallsurvival.relay." + name().toLowerCase(Locale.ROOT);
        }
    }

    public RelayFailure {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(operatorName, "operatorName");
    }

    /** L'échec du plan ({@link RelayPlanner#plan}) en échec lisible. */
    public static RelayFailure of(RelayPlanner.Failed failed) {
        return new RelayFailure(failed.reason(), failed.operatorName());
    }

    /**
     * Le texte que lit l'appelant (« Injoignable · Antenne de Lagon hors tension »). Seule
     * {@link Reason#CALLEE_ANTENNA_DOWN} porte un argument, le nom de l'opérateur ; les autres raisons n'en portent
     * aucun, quel que soit {@link #operatorName()}.
     */
    public Component describe() {
        if (reason == Reason.CALLEE_ANTENNA_DOWN) {
            return Component.translatable(reason.langKey(), Component.literal(operatorName));
        }
        return Component.translatable(reason.langKey());
    }
}
