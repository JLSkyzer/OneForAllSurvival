package dev.oneforallsurvival.phone.client;

import java.util.OptionalInt;

/**
 * Le numéro du panneau de l'appli « Antenne » que tient l'écran de la page (lot T2, tâche 10 ; revue, mineur 4), sans
 * Minecraft (testable). Aucun panneau ne reste servi sans page pour le montrer : un rechargement de la page appli
 * ouverte ferme le panneau ({@link #ready}), et « Accueil » touché avant la première vue fait fermer la vue qui arrive
 * ensuite ({@link #view} rend {@link OnView#CLOSE}). Fil du client.
 */
final class AntennaSession {

    /** Ce que fait l'écran d'une vue reçue : la montrer, ou fermer son panneau sans la montrer. */
    enum OnView { SHOW, CLOSE }

    private int session = -1;
    private boolean closeOnNextView;

    /** Le numéro du panneau montré, -1 sans appli. */
    int session() {
        return session;
    }

    /** La page demande l'appli ({@code antennaApp}) : une fermeture en attente est oubliée. */
    void appRequested() {
        closeOnNextView = false;
    }

    /** Une vue de ce numéro arrive. */
    OnView view(int number) {
        if (closeOnNextView) {
            closeOnNextView = false;
            session = -1;
            return OnView.CLOSE;
        }
        session = number;
        return OnView.SHOW;
    }

    /** La page ferme l'appli ({@code opClose}) : le numéro à fermer, ou rien encore (la prochaine vue se fermera). */
    OptionalInt pageClose() {
        if (session >= 0) {
            return forget();
        }
        closeOnNextView = true;
        return OptionalInt.empty();
    }

    /** La page (re)chargée envoie {@code ready} : un panneau encore ouvert est fermé, la page ne le montre plus. */
    OptionalInt ready() {
        closeOnNextView = false;
        return session >= 0 ? forget() : OptionalInt.empty();
    }

    /** L'écran est retiré : le panneau encore ouvert, à fermer. */
    OptionalInt removed() {
        return session >= 0 ? forget() : OptionalInt.empty();
    }

    /** Le serveur a fermé le panneau de ce numéro. */
    void serverClosed(int number) {
        if (number == session) {
            session = -1;
        }
    }

    private OptionalInt forget() {
        int s = session;
        session = -1;
        return OptionalInt.of(s);
    }
}
