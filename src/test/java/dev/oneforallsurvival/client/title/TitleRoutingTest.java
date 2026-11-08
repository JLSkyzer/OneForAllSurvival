package dev.oneforallsurvival.client.title;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.title.TitleRouting.Opening;
import org.junit.jupiter.api.Test;

class TitleRoutingTest {
    @Test
    void leTitreDeMinecraftDevientLeNotre() {
        assertTrue(TitleRouting.openOurTitle(Opening.VANILLA_TITLE, false));
    }

    @Test
    void seDeconnecterDUnServeurRameneAuTitre() {
        // PauseScreen.onDisconnect : setScreen(new JoinMultiplayerScreen(new TitleScreen()))
        assertTrue(TitleRouting.openOurTitle(Opening.MULTIPLAYER_LIST, true));
    }

    @Test
    void uneExpulsionRevientANotreInstanceSansRemplacement() {
        // DisconnectedScreen puis retour à son parent, notre écran (classe non exacte) : rien à remplacer
        assertFalse(TitleRouting.openOurTitle(Opening.OTHER, false));
        assertFalse(TitleRouting.openOurTitle(Opening.OTHER, true));
    }

    @Test
    void uneListeMultijoueurOuverteParUnAutreModEstLaissee() {
        assertFalse(TitleRouting.openOurTitle(Opening.MULTIPLAYER_LIST, false));
    }
}
