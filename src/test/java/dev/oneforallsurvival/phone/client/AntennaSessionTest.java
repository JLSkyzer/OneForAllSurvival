package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * Le numéro du panneau de l'appli « Antenne » tenu par l'écran de la page (revue de la tâche 10, mineur 4) : aucun
 * panneau ne reste servi par le serveur sans page pour le montrer, ni après un rechargement de la page, ni quand
 * « Accueil » est touché avant la première vue.
 */
class AntennaSessionTest {

    @Test
    void uneVueSeMontreEtLaFermetureRendSonNumero() {
        AntennaSession s = new AntennaSession();
        s.appRequested();
        assertEquals(AntennaSession.OnView.SHOW, s.view(7));
        assertEquals(7, s.session());
        assertEquals(OptionalInt.of(7), s.pageClose());
        assertEquals(-1, s.session());
    }

    @Test
    void accueilAvantLaPremiereVueFermeAuPremierArrivage() {
        AntennaSession s = new AntennaSession();
        s.appRequested();
        assertEquals(OptionalInt.empty(), s.pageClose(), "pas encore de numéro");
        assertEquals(AntennaSession.OnView.CLOSE, s.view(8), "la vue arrivée après se ferme");
        assertEquals(-1, s.session());
        // La suivante (appli rouverte) se montre.
        s.appRequested();
        assertEquals(AntennaSession.OnView.SHOW, s.view(9));
    }

    @Test
    void unRechargementDeLaPageFermeLePanneau() {
        AntennaSession s = new AntennaSession();
        s.appRequested();
        s.view(3);
        assertEquals(OptionalInt.of(3), s.ready(), "à ready, le panneau ouvert se ferme");
        assertEquals(OptionalInt.empty(), s.ready(), "une seule fois");
    }

    @Test
    void uneAppliRouverteOublieLaFermetureEnAttente() {
        AntennaSession s = new AntennaSession();
        s.appRequested();
        s.pageClose();
        s.appRequested();
        assertEquals(AntennaSession.OnView.SHOW, s.view(4));
    }

    @Test
    void laFermetureParLeServeurOublieLeNumero() {
        AntennaSession s = new AntennaSession();
        s.appRequested();
        s.view(5);
        s.serverClosed(6);
        assertEquals(5, s.session(), "un autre numéro ne ferme rien");
        s.serverClosed(5);
        assertEquals(-1, s.session());
        assertEquals(OptionalInt.empty(), s.removed());
    }
}
