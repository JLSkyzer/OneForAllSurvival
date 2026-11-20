package dev.oneforallsurvival.phone.net;

/**
 * Pourquoi un appel ne passe pas. La classe complète arrive à la tâche 6 du lot T2 ; la tâche 1 n'en pose que
 * l'énumération des raisons qui tiennent au réseau (les raisons qui tiennent aux cartes viendront avec elle).
 */
public final class RelayFailure {

    private RelayFailure() {}

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
        CALLEE_NO_COVERAGE
    }
}
