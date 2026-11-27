package dev.oneforallsurvival.client.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.title.SceneLifecycle.Request;
import dev.oneforallsurvival.client.title.SceneLifecycle.State;
import dev.oneforallsurvival.earlywindow.scene.StoryClock;
import org.junit.jupiter.api.Test;

class SceneLifecycleTest {
    /** Cycle de vie au titre après un chargement normal : l'overlay a confié la scène. */
    private static SceneLifecycle auTitre() {
        SceneLifecycle l = new SceneLifecycle();
        l.overlayStarted();
        l.handedOver();
        return l;
    }

    @Test
    void leChargementConfieLaSceneAuTitre() {
        SceneLifecycle l = new SceneLifecycle();
        assertEquals(State.LOADING_OVERLAY, l.state());
        l.overlayStarted();
        // L'écran titre existe déjà derrière l'overlay (init pendant le fondu) : il ne prend rien, et ne conclut
        // pas pour autant à l'absence de scène.
        assertEquals(Request.NONE, l.acquire());
        assertEquals(State.LOADING_OVERLAY, l.state());
        assertFalse(l.holdsScene());

        l.handedOver();
        assertEquals(State.TITLE, l.state());
        assertTrue(l.holdsScene());
        assertEquals(Request.USE, l.acquire());
        assertEquals(Request.USE, l.acquire());
    }

    @Test
    void entrerDansUnMondeLibereLaScene() {
        SceneLifecycle l = auTitre();
        assertTrue(l.worldJoined(), "la scène confiée au titre doit être libérée");
        assertEquals(State.IN_WORLD, l.state());
        assertFalse(l.holdsScene());
        // Deux connexions (changement de dimension, reconnexion) : rien à libérer la seconde fois.
        assertFalse(l.worldJoined());

        // Une scène recréée, même pas encore prête, se libère aussi.
        assertEquals(Request.CREATE, l.acquire());
        assertEquals(State.RECREATING, l.state());
        assertTrue(l.worldJoined());
        assertEquals(State.IN_WORLD, l.state());

        // Pendant l'overlay, c'est lui qui tient la scène : un monde rejoint ne la lui retire pas.
        SceneLifecycle pendant = new SceneLifecycle();
        pendant.overlayStarted();
        assertFalse(pendant.worldJoined());
        assertEquals(State.LOADING_OVERLAY, pendant.state());
    }

    @Test
    void retourAuTitreRecreeLaScene() {
        SceneLifecycle l = auTitre();
        l.worldJoined();
        assertEquals(Request.CREATE, l.acquire(), "retour au titre : la scène se recrée");
        assertEquals(State.RECREATING, l.state());
        assertTrue(l.holdsScene());
        // Tant qu'elle se charge (fond sombre), les images suivantes la reprennent, sans en créer une autre.
        for (int i = 0; i < 100; i++) {
            assertEquals(Request.USE, l.acquire());
        }
        l.sceneReady();
        assertEquals(State.TITLE, l.state());
        assertEquals(Request.USE, l.acquire());

        // L'overlay a libéré la scène sans écran titre derrière lui (comportement du chantier 1) : le premier
        // titre, plus tard, la recrée.
        SceneLifecycle sansTitre = new SceneLifecycle();
        sansTitre.overlayStarted();
        sansTitre.overlayReleased();
        assertEquals(State.IN_WORLD, sansTitre.state());
        assertEquals(Request.CREATE, sansTitre.acquire());
    }

    @Test
    void sansOverlayDuModLEcranTitreCreeSaScene() {
        // Fenêtre anticipée de Forge choisie dans fml.toml : aucun overlay du mod. Le premier titre crée la scène,
        // une seule fois, puis la reprend tant qu'elle se charge, comme au retour d'un monde.
        SceneLifecycle forge = new SceneLifecycle();
        assertEquals(Request.CREATE, forge.acquire());
        assertEquals(State.RECREATING, forge.state());
        assertTrue(forge.holdsScene());
        for (int i = 0; i < 100; i++) {
            assertEquals(Request.USE, forge.acquire());
        }
        forge.sceneReady();
        assertEquals(State.TITLE, forge.state());
        // Un monde, puis le titre : recréée comme d'habitude.
        assertTrue(forge.worldJoined());
        assertEquals(Request.CREATE, forge.acquire());

        // Un monde rejoint avant tout écran titre (aucune scène tenue) : le premier titre la crée.
        SceneLifecycle mondeDAbord = new SceneLifecycle();
        assertFalse(mondeDAbord.worldJoined());
        assertEquals(Request.CREATE, mondeDAbord.acquire());

        // Une image du titre échoue sur la scène créée : libérée, puis repli pour la session.
        SceneLifecycle rendu = new SceneLifecycle();
        rendu.acquire();
        assertTrue(rendu.renderFailed());
        assertEquals(Request.NONE, rendu.acquire());
    }

    @Test
    void sansSceneLEcranResteUtilisable() {
        // Jar de service absent : la création demandée par le premier titre échoue. Une seule tentative, puis
        // repli à chaque image, même après un monde.
        SceneLifecycle sansJar = new SceneLifecycle();
        assertEquals(Request.CREATE, sansJar.acquire());
        sansJar.creationFailed();
        for (int i = 0; i < 1000; i++) {
            assertEquals(Request.NONE, sansJar.acquire());
        }
        assertEquals(State.UNAVAILABLE, sansJar.state());
        assertFalse(sansJar.worldJoined());
        assertEquals(Request.NONE, sansJar.acquire(), "même après un monde");

        // Recréation en échec (GL) : une tentative, puis plus jamais.
        SceneLifecycle echec = auTitre();
        echec.worldJoined();
        assertEquals(Request.CREATE, echec.acquire());
        echec.creationFailed();
        assertEquals(State.UNAVAILABLE, echec.state());
        assertFalse(echec.holdsScene());
        for (int i = 0; i < 1000; i++) {
            assertNotEquals(Request.CREATE, echec.acquire());
        }
        echec.worldJoined();
        assertEquals(Request.NONE, echec.acquire());

        // L'overlay est passé à celui de Forge (repli du chantier 1) : pas de scène au titre.
        SceneLifecycle repli = new SceneLifecycle();
        repli.overlayStarted();
        repli.overlayFailed();
        assertEquals(State.UNAVAILABLE, repli.state());
        assertEquals(Request.NONE, repli.acquire());

        // Une image du titre échoue : la scène est à libérer, puis repli.
        SceneLifecycle rendu = auTitre();
        assertTrue(rendu.renderFailed());
        assertEquals(State.UNAVAILABLE, rendu.state());
        assertFalse(rendu.renderFailed(), "plus rien à libérer");
        assertEquals(Request.NONE, rendu.acquire());
    }

    @Test
    void unRetourAuTitreNeRecommencePasLeRecit() {
        SceneLifecycle l = auTitre();
        // Le récit de la scène confiée : chargement fini à 20 s, accélération jusqu'à l'aube.
        StoryClock horloge = new StoryClock();
        horloge.storyTime(20.0, true);
        float auTitre = horloge.storyTime(24.0, true);
        assertTrue(auTitre > 20f);

        // Titre → Options → titre, titre → liste des mods → titre : la même scène, pas de recréation.
        for (int aller = 0; aller < 5; aller++) {
            assertEquals(Request.USE, l.acquire());
        }
        assertEquals(State.TITLE, l.state());
        // Tant qu'elle vit, son horloge ne revient pas en arrière.
        assertTrue(horloge.storyTime(30.0, true) >= auTitre);
        assertEquals(StoryClock.HOLD, horloge.storyTime(60.0, true), 0f);

        // Après un monde, la scène recréée part de l'aube tenue, pas du début du récit (chute, crash).
        l.worldJoined();
        assertEquals(Request.CREATE, l.acquire());
        StoryClock recreee = StoryClock.atDawn();
        assertEquals(StoryClock.HOLD, recreee.storyTime(0.0, false), 0f);
        assertEquals(StoryClock.HOLD, recreee.storyTime(0.5, true), 0f);
        assertEquals(StoryClock.HOLD, recreee.storyTime(500.0, true), 0f);
    }
}
