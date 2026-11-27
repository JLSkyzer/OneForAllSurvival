package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.oneforallsurvival.phone.call.Contact;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Book;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import dev.oneforallsurvival.phone.call.Recent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Lot T3, tâche 4b : ce que Java passe à {@code window.phone.call} et {@code window.phone.directory}. Clés du contrat,
 * phases et genres en noms, secondes depuis le décroché à l'heure du serveur, message déjà traduit, nom des récents
 * pris au carnet ; rien de plus (ni jeton, ni carte, ni joueur, que le paquet ne porte de toute façon pas).
 */
class PhoneCallJsonTest {

    private static final long DECROCHE = 1_763_600_000_000L;

    private static PhoneCallStatePacket etat(Phase phase, long answered) {
        return new PhoneCallStatePacket(phase, 42, "0612345678", "Maman", answered, true, false, true, null, null);
    }

    @Test
    void lesClesSontCellesDuContrat() {
        JsonObject v = PhoneCallJson.call(etat(Phase.ACTIVE, DECROCHE), DECROCHE + 83_400L, "");
        assertEquals(Set.of("phase", "id", "number", "name", "seconds", "muted", "speaker", "message"), v.keySet());
        assertEquals("ACTIVE", v.get("phase").getAsString());
        assertEquals(42, v.get("id").getAsInt());
        assertEquals("0612345678", v.get("number").getAsString());
        assertEquals("Maman", v.get("name").getAsString());
        assertEquals(83, v.get("seconds").getAsInt());
        assertTrue(v.get("muted").getAsBoolean());
        assertFalse(v.get("speaker").getAsBoolean());
        assertEquals("", v.get("message").getAsString());
    }

    @Test
    void chaquePhasePartSousSonNom() {
        for (Phase phase : Phase.values()) {
            assertEquals(phase.name(), PhoneCallJson.call(etat(phase, -1L), DECROCHE, "").get("phase").getAsString());
        }
    }

    /** Les secondes ne comptent qu'une fois décroché : 0 avant, jamais négatives (horloge en retard), jamais au-delà. */
    @Test
    void lesSecondesPartentDuDecroche() {
        assertEquals(0, PhoneCallJson.call(etat(Phase.OUTGOING, -1L), DECROCHE, "").get("seconds").getAsInt());
        assertEquals(0, PhoneCallJson.call(etat(Phase.INCOMING, -1L), DECROCHE, "").get("seconds").getAsInt());
        assertEquals(0, PhoneCallJson.call(etat(Phase.ACTIVE, DECROCHE), DECROCHE - 5_000L, "").get("seconds")
                .getAsInt());
        assertEquals(0, PhoneCallJson.call(etat(Phase.ACTIVE, DECROCHE), DECROCHE + 999L, "").get("seconds")
                .getAsInt());
        assertEquals(1, PhoneCallJson.call(etat(Phase.ACTIVE, DECROCHE), DECROCHE + 1_000L, "").get("seconds")
                .getAsInt());
        assertEquals(0, PhoneCallJson.call(etat(Phase.NONE, DECROCHE), DECROCHE + 60_000L, "").get("seconds")
                .getAsInt());
        assertEquals(Integer.MAX_VALUE, PhoneCallJson.call(etat(Phase.ACTIVE, 0L), Long.MAX_VALUE, "")
                .get("seconds").getAsInt());
    }

    @Test
    void leMessageEstLeTexteDonne() {
        JsonObject fin = PhoneCallJson.call(etat(Phase.NONE, -1L), DECROCHE, "Appel terminé");
        assertEquals("Appel terminé", fin.get("message").getAsString());
        assertEquals("", PhoneCallJson.call(etat(Phase.NONE, -1L), DECROCHE, null).get("message").getAsString());
    }

    /**
     * Revue de la tâche 4a, I3 : un appel porté par un autre téléphone que celui de l'écran n'arrive pas à la page (ni
     * phase, ni numéro, ni nom) ; seul le message d'une réponse à un geste de cet écran reste (« Un appel est déjà en
     * cours »). Une fin d'appel ailleurs ne dit rien à la page : la barre d'action la donne.
     */
    @Test
    void unAppelPorteAilleursNArrivePasALaPage() {
        PhoneCallStatePacket ailleurs = new PhoneCallStatePacket(Phase.ACTIVE, 42, "0612345678", "Maman", DECROCHE,
                true, true, false, null, null);
        JsonObject v = PhoneCallJson.call(ailleurs, DECROCHE + 60_000L, "Un appel est déjà en cours");
        assertEquals("NONE", v.get("phase").getAsString());
        assertEquals(0, v.get("id").getAsInt());
        assertEquals("", v.get("number").getAsString());
        assertEquals("", v.get("name").getAsString());
        assertEquals(0, v.get("seconds").getAsInt());
        assertFalse(v.get("muted").getAsBoolean());
        assertFalse(v.get("speaker").getAsBoolean());
        assertEquals("Un appel est déjà en cours", v.get("message").getAsString());
        PhoneCallStatePacket finAilleurs = new PhoneCallStatePacket(Phase.NONE, 42, "0612345678", "Maman", DECROCHE,
                false, false, false, null, null);
        JsonObject fin = PhoneCallJson.call(finAilleurs, DECROCHE, "Appel terminé");
        assertEquals("NONE", fin.get("phase").getAsString());
        assertEquals("", fin.get("number").getAsString());
        assertEquals("", fin.get("message").getAsString());
    }

    @Test
    void leCarnetPorteContactsEtRecentsNommes() {
        Book book = new Book(List.of(new Contact("0612345678", "Maman"), new Contact("0798765432", "Léo")),
                List.of(new Recent("0612345678", Recent.Kind.MISSED, 1_000L, 0),
                        new Recent("0655555555", Recent.Kind.OUTGOING, 2_000L, 75),
                        new Recent("0798765432", Recent.Kind.INCOMING, 3_000L, 12)));
        JsonObject d = PhoneCallJson.directory(book);
        assertEquals(Set.of("contacts", "recents"), d.keySet());
        JsonArray contacts = d.getAsJsonArray("contacts");
        assertEquals(2, contacts.size());
        assertEquals(Set.of("number", "name"), contacts.get(0).getAsJsonObject().keySet());
        assertEquals("0612345678", contacts.get(0).getAsJsonObject().get("number").getAsString());
        assertEquals("Maman", contacts.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("Léo", contacts.get(1).getAsJsonObject().get("name").getAsString());
        JsonArray recents = d.getAsJsonArray("recents");
        assertEquals(3, recents.size());
        JsonObject manque = recents.get(0).getAsJsonObject();
        assertEquals(Set.of("number", "name", "kind", "at", "seconds"), manque.keySet());
        assertEquals("0612345678", manque.get("number").getAsString());
        assertEquals("Maman", manque.get("name").getAsString());
        assertEquals("MISSED", manque.get("kind").getAsString());
        assertEquals(1_000L, manque.get("at").getAsLong());
        assertEquals(0, manque.get("seconds").getAsInt());
        JsonObject inconnu = recents.get(1).getAsJsonObject();
        assertEquals("", inconnu.get("name").getAsString());
        assertEquals("OUTGOING", inconnu.get("kind").getAsString());
        assertEquals(75, inconnu.get("seconds").getAsInt());
        assertEquals("Léo", recents.get(2).getAsJsonObject().get("name").getAsString());
        assertEquals("INCOMING", recents.get(2).getAsJsonObject().get("kind").getAsString());
    }

    @Test
    void unCarnetVideDonneDeuxListesVides() {
        JsonObject d = PhoneCallJson.directory(new Book(null, null));
        assertEquals(0, d.getAsJsonArray("contacts").size());
        assertEquals(0, d.getAsJsonArray("recents").size());
    }

    /** L'ordre du carnet est celui du serveur : la page le montre tel quel. */
    @Test
    void lOrdreDuCarnetEstGarde() {
        Book book = new Book(List.of(new Contact("0612345678", "Zoé"), new Contact("0611111111", "Abel")),
                List.of(new Recent("0611111111", Recent.Kind.OUTGOING, 9_000L, 1),
                        new Recent("0612345678", Recent.Kind.OUTGOING, 1_000L, 1)));
        JsonObject d = PhoneCallJson.directory(book);
        assertEquals("Zoé", d.getAsJsonArray("contacts").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(9_000L, d.getAsJsonArray("recents").get(0).getAsJsonObject().get("at").getAsLong());
    }
}
