package dev.oneforallsurvival.phone.call;

/**
 * Ce que {@link CallService} annonce : la page et le repli du téléphone (tâche 4), la voix (tâche 5). Appelé sur le fil
 * du serveur ; une exception d'un écouteur est journalisée et n'empêche ni les autres écouteurs ni la fin de l'appel.
 */
public interface CallListener {

    /** Un appel commence à sonner, est décroché, ou change (silence, haut-parleur). */
    void changed(CallBook.Call call);

    /** Un appel a pris fin ; {@code call} est son dernier état. Annoncé une fois, après la fermeture de son trafic. */
    void ended(CallBook.Call call, CallBook.End reason);
}
