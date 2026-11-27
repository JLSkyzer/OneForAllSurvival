package dev.oneforallsurvival.phone.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** Le carnet d'une carte SIM : contacts et récents. NBT seulement, pas de monde. */
class PhoneDirectoryTest {

    private static final UUID SIM = uuid(1);
    private static final UUID OTHER = uuid(2);
    private static final String ALICE = "0611111111";
    private static final String BOB = "0622222222";

    private static UUID uuid(int n) {
        return UUID.fromString(String.format(Locale.ROOT, "00000000-0000-0000-0000-%012d", n));
    }

    /** Un numéro de carte différent pour chaque {@code n}. */
    private static String number(int n) {
        return String.format(Locale.ROOT, "06%08d", n);
    }

    private static Recent recent(int n) {
        return new Recent(number(n), Recent.Kind.OUTGOING, 1_000L + n, n);
    }

    private static PhoneDirectory reload(PhoneDirectory directory) {
        return PhoneDirectory.load(directory.save(new CompoundTag()));
    }

    private static List<String> names(PhoneDirectory directory, UUID simId) {
        return directory.contacts(simId).stream().map(Contact::name).toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Contacts
    // ---------------------------------------------------------------------------------------------

    @Test
    void uneCarteInconnueAUnCarnetVide() {
        PhoneDirectory directory = new PhoneDirectory();
        assertEquals(List.of(), directory.contacts(SIM));
        assertEquals(List.of(), directory.recents(SIM));
        assertEquals(Optional.empty(), directory.nameOf(SIM, ALICE));
        assertFalse(directory.removeContact(SIM, ALICE));
        assertFalse(directory.isDirty());
        assertTrue(directory.save(new CompoundTag()).getList("books", Tag.TAG_COMPOUND).isEmpty());
    }

    @Test
    void unContactEstEnregistreSousSonNumeroSansEspaces() {
        PhoneDirectory directory = new PhoneDirectory();
        assertEquals(PhoneDirectory.SaveResult.SAVED,
                directory.saveContact(SIM, " 06 11 11 11 11 ", "  Alice   Martin "));
        assertEquals(List.of(new Contact(ALICE, "Alice Martin")), directory.contacts(SIM));
        assertEquals(Optional.of("Alice Martin"), directory.nameOf(SIM, ALICE));
        assertEquals(Optional.of("Alice Martin"), directory.nameOf(SIM, "06 11 11 11 11"));
        assertEquals(Optional.empty(), directory.nameOf(SIM, BOB));
        assertEquals(Optional.empty(), directory.nameOf(SIM, "n'importe quoi"));
        assertEquals(Optional.empty(), directory.nameOf(SIM, null));
        assertEquals(List.of(), directory.contacts(OTHER));
        assertTrue(directory.isDirty());
    }

    @Test
    void unNumeroQuiNEnEstPasUnEstRefuse() {
        PhoneDirectory directory = new PhoneDirectory();
        for (String raw : new String[] {null, "", "0711111111", "061111111", "06111111111", "17", "06 11 11 11 1a"}) {
            assertEquals(PhoneDirectory.SaveResult.BAD_NUMBER, directory.saveContact(SIM, raw, "Alice"), "" + raw);
        }
        // Les deux refusés : le numéro est jugé d'abord.
        assertEquals(PhoneDirectory.SaveResult.BAD_NUMBER, directory.saveContact(SIM, "abc", ""));
        assertEquals(List.of(), directory.contacts(SIM));
        assertFalse(directory.isDirty());
    }

    @Test
    void unNomVideOuTropLongEstRefuse() {
        PhoneDirectory directory = new PhoneDirectory();
        for (String raw : new String[] {null, "", "    ", "a".repeat(33)}) {
            assertEquals(PhoneDirectory.SaveResult.BAD_NAME, directory.saveContact(SIM, ALICE, raw), "" + raw);
        }
        assertEquals(List.of(), directory.contacts(SIM));
        assertFalse(directory.isDirty());
        // Un refus ne touche pas au contact déjà là.
        directory.saveContact(SIM, ALICE, "Alice");
        assertEquals(PhoneDirectory.SaveResult.BAD_NAME, directory.saveContact(SIM, ALICE, " "));
        assertEquals(List.of(new Contact(ALICE, "Alice")), directory.contacts(SIM));
    }

    @Test
    void leMemeNumeroRenommeLeContact() {
        PhoneDirectory directory = new PhoneDirectory();
        directory.saveContact(SIM, ALICE, "Alice");
        directory.setDirty(false);
        assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(SIM, "06 11 11 11 11", "Maman"));
        assertEquals(List.of(new Contact(ALICE, "Maman")), directory.contacts(SIM));
        assertTrue(directory.isDirty());
        // Deux numéros peuvent porter le même nom.
        directory.saveContact(SIM, BOB, "Maman");
        assertEquals(2, directory.contacts(SIM).size());
    }

    @Test
    void lesContactsSontTriesParNomSansCasse() {
        PhoneDirectory directory = new PhoneDirectory();
        int n = 0;
        for (String name : new String[] {"charlie", "Zoé", "bob", "Émile", "Alice", "delta", "Fanny"}) {
            directory.saveContact(SIM, number(++n), name);
        }
        List<String> sorted = List.of("Alice", "bob", "charlie", "delta", "Émile", "Fanny", "Zoé");
        assertEquals(sorted, names(directory, SIM));
        assertEquals(sorted, names(reload(directory), SIM));
    }

    @Test
    void aNomEgalLOrdreEstCeluiDesNumeros() {
        PhoneDirectory directory = new PhoneDirectory();
        // Sans la force secondaire du tri, la casse départagerait (« alice », « Alice », « ALICE ») : 3, 2, 1.
        directory.saveContact(SIM, number(3), "alice");
        directory.saveContact(SIM, number(1), "ALICE");
        directory.saveContact(SIM, number(2), "Alice");
        assertEquals(List.of(number(1), number(2), number(3)),
                directory.contacts(SIM).stream().map(Contact::number).toList());
    }

    @Test
    void pasPlusDeCentContactsParCarte() {
        PhoneDirectory directory = new PhoneDirectory();
        for (int i = 0; i < ContactRules.MAX_CONTACTS; i++) {
            assertEquals(PhoneDirectory.SaveResult.SAVED,
                    directory.saveContact(SIM, number(i), "Contact " + i), "" + i);
        }
        assertEquals(100, directory.contacts(SIM).size());
        directory.setDirty(false);
        assertEquals(PhoneDirectory.SaveResult.FULL, directory.saveContact(SIM, number(100), "De trop"));
        assertEquals(100, directory.contacts(SIM).size());
        assertEquals(Optional.empty(), directory.nameOf(SIM, number(100)));
        assertFalse(directory.isDirty());
        // Carnet plein : un contact connu se renomme encore, et une autre carte n'est pas gênée.
        assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(SIM, number(99), "Renommé"));
        assertEquals(Optional.of("Renommé"), directory.nameOf(SIM, number(99)));
        assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(OTHER, number(100), "Ailleurs"));
        // Une place libérée se reprend.
        assertTrue(directory.removeContact(SIM, number(0)));
        assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(SIM, number(100), "Dernier"));
        assertEquals(100, directory.contacts(SIM).size());
        assertEquals(100, reload(directory).contacts(SIM).size());
    }

    @Test
    void unContactSeRetire() {
        PhoneDirectory directory = new PhoneDirectory();
        directory.saveContact(SIM, ALICE, "Alice");
        directory.saveContact(SIM, BOB, "Bob");
        directory.saveContact(OTHER, ALICE, "Alice");
        directory.setDirty(false);
        assertFalse(directory.removeContact(SIM, "0699999999"));
        assertFalse(directory.removeContact(SIM, "abc"));
        assertFalse(directory.removeContact(SIM, null));
        assertFalse(directory.isDirty());
        assertTrue(directory.removeContact(SIM, "06 11 11 11 11"));
        assertTrue(directory.isDirty());
        assertFalse(directory.removeContact(SIM, ALICE));
        assertEquals(List.of(new Contact(BOB, "Bob")), directory.contacts(SIM));
        assertEquals(List.of(new Contact(ALICE, "Alice")), directory.contacts(OTHER));
    }

    @Test
    void unCarnetRedevenuVideNEstPlusEcrit() {
        PhoneDirectory directory = new PhoneDirectory();
        directory.saveContact(SIM, ALICE, "Alice");
        directory.removeContact(SIM, ALICE);
        assertTrue(directory.save(new CompoundTag()).getList("books", Tag.TAG_COMPOUND).isEmpty());
        // Avec des récents, il reste.
        directory.saveContact(SIM, ALICE, "Alice");
        directory.log(SIM, recent(1));
        directory.removeContact(SIM, ALICE);
        assertEquals(List.of(recent(1)), reload(directory).recents(SIM));
    }

    // ---------------------------------------------------------------------------------------------
    // Récents
    // ---------------------------------------------------------------------------------------------

    @Test
    void lePlusRecentEstEnTete() {
        PhoneDirectory directory = new PhoneDirectory();
        // L'ordre est celui des inscriptions, pas celui des dates (un appel s'inscrit à sa fin).
        Recent first = new Recent(ALICE, Recent.Kind.OUTGOING, 5_000L, 12);
        Recent second = new Recent(BOB, Recent.Kind.MISSED, 4_000L, 0);
        Recent third = new Recent(ALICE, Recent.Kind.INCOMING, 6_000L, 3);
        directory.log(SIM, first);
        assertTrue(directory.isDirty());
        directory.log(SIM, second);
        directory.log(SIM, third);
        assertEquals(List.of(third, second, first), directory.recents(SIM));
        assertEquals(List.of(), directory.recents(OTHER));
    }

    @Test
    void auDelaDeCinquanteLePlusAncienTombe() {
        PhoneDirectory directory = new PhoneDirectory();
        for (int i = 0; i < ContactRules.MAX_RECENTS; i++) {
            directory.log(SIM, recent(i));
        }
        assertEquals(50, directory.recents(SIM).size());
        assertEquals(recent(49), directory.recents(SIM).get(0));
        assertEquals(recent(0), directory.recents(SIM).get(49));
        directory.log(OTHER, recent(900));
        directory.setDirty(false);
        directory.log(SIM, recent(50));
        assertTrue(directory.isDirty());
        assertEquals(50, directory.recents(SIM).size());
        assertEquals(recent(50), directory.recents(SIM).get(0));
        assertEquals(recent(1), directory.recents(SIM).get(49));
        assertEquals(List.of(recent(900)), directory.recents(OTHER));
        assertEquals(directory.recents(SIM), reload(directory).recents(SIM));
    }

    @Test
    void unNumeroCourtSInscritUnNumeroIllisibleNon() {
        PhoneDirectory directory = new PhoneDirectory();
        for (String number : new String[] {"", "abc", "06 11 11 11 1", "06111111111", "-17", "1 7"}) {
            directory.log(SIM, new Recent(number, Recent.Kind.OUTGOING, 1L, 1));
        }
        assertEquals(List.of(), directory.recents(SIM));
        assertFalse(directory.isDirty());
        assertTrue(directory.save(new CompoundTag()).getList("books", Tag.TAG_COMPOUND).isEmpty());
        // Les numéros d'urgence (17, 15) sont de vrais numéros d'appel.
        Recent police = new Recent("17", Recent.Kind.OUTGOING, 1L, 1);
        directory.log(SIM, police);
        assertEquals(List.of(police), reload(directory).recents(SIM));
    }

    @Test
    void desChiffresDUneAutreEcritureNeSInscriventPas() {
        PhoneDirectory directory = new PhoneDirectory();
        // « 17 » en chiffres arabes orientaux, puis en pleine chasse ; et un numéro de carte en pleine chasse.
        String arabe = new StringBuilder().appendCodePoint(0x0661).appendCodePoint(0x0667).toString();
        String pleineChasse = new StringBuilder().appendCodePoint(0xFF11).appendCodePoint(0xFF17).toString();
        StringBuilder carte = new StringBuilder();
        for (char c : ALICE.toCharArray()) {
            carte.appendCodePoint(0xFF10 + (c - '0'));
        }
        for (String number : new String[] {arabe, pleineChasse, carte.toString()}) {
            assertFalse(directory.log(SIM, new Recent(number, Recent.Kind.OUTGOING, 1L, 1)), number);
        }
        assertEquals(List.of(), directory.recents(SIM));
        assertFalse(directory.isDirty());
    }

    @Test
    void unNumeroEcritAvecSesEspacesSInscritSansEux() {
        PhoneDirectory directory = new PhoneDirectory();
        assertTrue(directory.log(SIM, new Recent("06 11 11 11 11", Recent.Kind.INCOMING, 3L, 4)));
        assertEquals(List.of(new Recent(ALICE, Recent.Kind.INCOMING, 3L, 4)), directory.recents(SIM));
        assertEquals(List.of(new Recent(ALICE, Recent.Kind.INCOMING, 3L, 4)), reload(directory).recents(SIM));
        // Un numéro court s'inscrit tel quel ; un texte illisible est refusé et le dit.
        assertTrue(directory.log(SIM, new Recent("15", Recent.Kind.OUTGOING, 5L, 0)));
        assertFalse(directory.log(SIM, new Recent("06 11 11 11 1a", Recent.Kind.OUTGOING, 6L, 0)));
        assertEquals(2, directory.recents(SIM).size());
    }

    // ---------------------------------------------------------------------------------------------
    // Nombre de carnets (revue de la tâche 2, I1)
    // ---------------------------------------------------------------------------------------------

    @Test
    void auPlafondLesCarnetsDeCartesMortesSontOubliesPasLesAutres() {
        Set<UUID> dead = new HashSet<>();
        PhoneDirectory directory = new PhoneDirectory(simId -> !dead.contains(simId));
        // Le plafond rempli : un carnet sur deux appartient à une carte qui mourra (désactivée ou inconnue).
        for (int i = 0; i < ContactRules.MAX_BOOKS; i++) {
            assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(uuid(1_000 + i), ALICE, "Alice"));
        }
        for (int i = 0; i < ContactRules.MAX_BOOKS; i += 2) {
            dead.add(uuid(1_000 + i));
        }
        // Rien n'est oublié tant que le plafond n'est pas atteint par un carnet neuf : un carnet connu s'écrit encore.
        assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(uuid(1_000), BOB, "Bob"));
        assertTrue(directory.log(uuid(1_000), recent(1)));
        assertEquals(2, directory.contacts(uuid(1_000)).size());

        assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(SIM, ALICE, "Neuf"));

        assertEquals(List.of(), directory.contacts(uuid(1_000)));
        assertEquals(List.of(), directory.recents(uuid(1_000)));
        assertEquals(List.of(new Contact(ALICE, "Alice")), directory.contacts(uuid(1_001)));
        assertEquals(List.of(new Contact(ALICE, "Neuf")), directory.contacts(SIM));
        assertEquals(ContactRules.MAX_BOOKS / 2 + 1,
                directory.save(new CompoundTag()).getList("books", Tag.TAG_COMPOUND).size());
    }

    @Test
    void auPlafondDeCartesVivantesUnCarnetNeufEstRefuse() {
        PhoneDirectory directory = new PhoneDirectory(simId -> true);
        for (int i = 0; i < ContactRules.MAX_BOOKS; i++) {
            assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(uuid(1_000 + i), ALICE, "Alice"));
        }
        directory.setDirty(false);
        assertEquals(PhoneDirectory.SaveResult.DIRECTORY_FULL, directory.saveContact(SIM, ALICE, "Alice"));
        assertFalse(directory.log(SIM, recent(1)));
        assertEquals(List.of(), directory.contacts(SIM));
        assertEquals(List.of(), directory.recents(SIM));
        assertFalse(directory.isDirty());
        // Les carnets déjà là s'écrivent toujours.
        assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(uuid(1_000), BOB, "Bob"));
        assertTrue(directory.log(uuid(1_001), recent(2)));
        // Une place libérée (carnet redevenu vide) se reprend.
        assertTrue(directory.removeContact(uuid(1_002), ALICE));
        assertTrue(directory.log(SIM, recent(1)));
        assertEquals(List.of(recent(1)), directory.recents(SIM));
        assertEquals(PhoneDirectory.SaveResult.DIRECTORY_FULL, directory.saveContact(OTHER, ALICE, "Alice"));
    }

    @Test
    void uneCarteMorteNeRecoitPasDeCarnetNeuf() {
        UUID mort = uuid(66);
        PhoneDirectory directory = new PhoneDirectory(simId -> !simId.equals(mort));
        assertEquals(PhoneDirectory.SaveResult.DEAD_CARD, directory.saveContact(mort, ALICE, "Alice"));
        assertFalse(directory.log(mort, recent(1)));
        assertEquals(List.of(), directory.contacts(mort));
        assertEquals(List.of(), directory.recents(mort));
        assertFalse(directory.isDirty());
        // Un refus du numéro ou du nom se dit d'abord, comme pour une carte vivante.
        assertEquals(PhoneDirectory.SaveResult.BAD_NUMBER, directory.saveContact(mort, "abc", "Alice"));
        assertEquals(PhoneDirectory.SaveResult.BAD_NAME, directory.saveContact(mort, ALICE, " "));
        // Témoin : une carte vivante en reçoit un.
        assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(SIM, ALICE, "Alice"));
    }

    @Test
    void uneCarteMorteGardeLeCarnetQuElleAvaitSousLePlafond() {
        Set<UUID> dead = new HashSet<>();
        PhoneDirectory directory = new PhoneDirectory(simId -> !dead.contains(simId));
        directory.saveContact(SIM, ALICE, "Alice");
        dead.add(SIM);
        // Les preuves restent pour la fouille tant qu'il y a de la place.
        assertEquals(List.of(new Contact(ALICE, "Alice")), directory.contacts(SIM));
        directory.saveContact(OTHER, BOB, "Bob");
        assertEquals(List.of(new Contact(ALICE, "Alice")), directory.contacts(SIM));
    }

    @Test
    void laLectureBorneLeNombreDeCarnets() {
        CompoundTag[] rows = new CompoundTag[ContactRules.MAX_BOOKS + 5];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = bookRow(uuid(1_000 + i), List.of(contactRow(ALICE, "Alice")), List.of());
        }
        PhoneDirectory back = PhoneDirectory.load(saved(rows));
        assertEquals(ContactRules.MAX_BOOKS, back.save(new CompoundTag()).getList("books", Tag.TAG_COMPOUND).size());
        assertEquals(1, back.contacts(uuid(1_000)).size());
        assertEquals(List.of(), back.contacts(uuid(1_000 + ContactRules.MAX_BOOKS)));
    }

    @Test
    void uneDateOuUneDureeNegativeVautZero() {
        Recent recent = new Recent(ALICE, Recent.Kind.MISSED, -5L, -1);
        assertEquals(0L, recent.atMillis());
        assertEquals(0, recent.seconds());
    }

    @Test
    void unRecentSansNumeroOuSansGenreNExistePas() {
        assertThrows(NullPointerException.class, () -> new Recent(null, Recent.Kind.MISSED, 0L, 0));
        assertThrows(NullPointerException.class, () -> new Recent(ALICE, null, 0L, 0));
    }

    @Test
    void unCarnetSansCarteEstRefuse() {
        PhoneDirectory directory = new PhoneDirectory();
        assertThrows(NullPointerException.class, () -> directory.saveContact(null, ALICE, "Alice"));
        assertThrows(NullPointerException.class, () -> directory.log(null, recent(1)));
        assertFalse(directory.isDirty());
    }

    @Test
    void lesListesRenduesSontDesCopies() {
        PhoneDirectory directory = new PhoneDirectory();
        directory.saveContact(SIM, ALICE, "Alice");
        directory.log(SIM, recent(1));
        List<Contact> contacts = directory.contacts(SIM);
        List<Recent> recents = directory.recents(SIM);
        assertThrows(UnsupportedOperationException.class, () -> contacts.clear());
        assertThrows(UnsupportedOperationException.class, () -> recents.clear());
        directory.saveContact(SIM, BOB, "Bob");
        directory.log(SIM, recent(2));
        assertEquals(1, contacts.size());
        assertEquals(1, recents.size());
    }

    @Test
    void uneLectureOuUnGesteSansEffetNeSalitPas() {
        PhoneDirectory directory = new PhoneDirectory();
        directory.saveContact(SIM, ALICE, "Alice");
        directory.log(SIM, recent(1));
        directory.setDirty(false);
        directory.contacts(SIM);
        directory.recents(SIM);
        directory.nameOf(SIM, ALICE);
        assertEquals(PhoneDirectory.SaveResult.SAVED, directory.saveContact(SIM, ALICE, " Alice "));
        directory.removeContact(SIM, BOB);
        assertFalse(directory.isDirty());
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Test
    void allerRetourComplet() {
        assertEquals("oneforallsurvival_phone_directory", PhoneDirectory.KEY);
        PhoneDirectory directory = new PhoneDirectory();
        directory.saveContact(SIM, ALICE, "Alice");
        directory.saveContact(SIM, BOB, "Bob");
        directory.saveContact(OTHER, ALICE, "Maman");
        directory.log(SIM, new Recent(BOB, Recent.Kind.OUTGOING, 1_700_000_000_000L, 75));
        directory.log(SIM, new Recent(ALICE, Recent.Kind.MISSED, 1_700_000_100_000L, 0));
        directory.log(OTHER, new Recent("15", Recent.Kind.INCOMING, Long.MAX_VALUE, Integer.MAX_VALUE));

        PhoneDirectory back = reload(directory);

        for (UUID sim : List.of(SIM, OTHER)) {
            assertEquals(directory.contacts(sim), back.contacts(sim));
            assertEquals(directory.recents(sim), back.recents(sim));
        }
        assertEquals(2, back.contacts(SIM).size());
        assertEquals(2, back.recents(SIM).size());
        assertFalse(back.isDirty());
        assertEquals(directory.save(new CompoundTag()), back.save(new CompoundTag()));
    }

    @Test
    void uneSauvegardeVideOuDUnAutreTypeSeCharge() {
        assertEquals(List.of(), PhoneDirectory.load(new CompoundTag()).contacts(SIM));
        for (Tag wrong : List.of(IntTag.valueOf(3), StringTag.valueOf("carnet"), new CompoundTag())) {
            CompoundTag tag = new CompoundTag();
            tag.put("books", wrong);
            PhoneDirectory back = PhoneDirectory.load(tag);
            assertEquals(List.of(), back.contacts(SIM));
            // Utilisable ensuite.
            assertEquals(PhoneDirectory.SaveResult.SAVED, back.saveContact(SIM, ALICE, "Alice"));
        }
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("carnet"));
        CompoundTag tag = new CompoundTag();
        tag.put("books", strings);
        assertEquals(List.of(), PhoneDirectory.load(tag).recents(SIM));
    }

    private static CompoundTag contactRow(String number, String name) {
        CompoundTag row = new CompoundTag();
        row.putString("number", number);
        row.putString("name", name);
        return row;
    }

    private static CompoundTag recentRow(String number, String kind, long at, int seconds) {
        CompoundTag row = new CompoundTag();
        row.putString("number", number);
        row.putString("kind", kind);
        row.putLong("at", at);
        row.putInt("seconds", seconds);
        return row;
    }

    private static CompoundTag bookRow(UUID simId, List<CompoundTag> contacts, List<CompoundTag> recents) {
        CompoundTag row = new CompoundTag();
        row.putUUID("sim", simId);
        ListTag contactList = new ListTag();
        contactList.addAll(contacts);
        row.put("contacts", contactList);
        ListTag recentList = new ListTag();
        recentList.addAll(recents);
        row.put("recents", recentList);
        return row;
    }

    private static CompoundTag saved(CompoundTag... books) {
        ListTag list = new ListTag();
        for (CompoundTag book : books) {
            list.add(book);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("books", list);
        return tag;
    }

    @Test
    void leFormatEcritEstCeluiQueLeTestRelit() {
        PhoneDirectory directory = new PhoneDirectory();
        directory.saveContact(SIM, ALICE, "Alice");
        directory.log(SIM, new Recent(BOB, Recent.Kind.MISSED, 12L, 3));
        assertEquals(saved(bookRow(SIM, List.of(contactRow(ALICE, "Alice")),
                List.of(recentRow(BOB, "MISSED", 12L, 3)))), directory.save(new CompoundTag()));
    }

    @Test
    void uneEntreeIllisibleEstIgnoreeSansFairePlanter() {
        CompoundTag wrongTypes = new CompoundTag();
        wrongTypes.putInt("number", 7);
        wrongTypes.putInt("name", 7);
        wrongTypes.putInt("kind", 7);
        wrongTypes.putString("at", "hier");
        wrongTypes.putString("seconds", "dix");
        CompoundTag noDate = recentRow(BOB, "OUTGOING", 1L, 1);
        noDate.remove("at");
        CompoundTag textDate = recentRow(BOB, "OUTGOING", 1L, 1);
        textDate.putString("at", "hier");
        CompoundTag noDuration = recentRow(BOB, "OUTGOING", 1L, 1);
        noDuration.remove("seconds");
        CompoundTag textDuration = recentRow(BOB, "OUTGOING", 1L, 1);
        textDuration.putString("seconds", "dix");

        List<CompoundTag> contacts = List.of(
                new CompoundTag(),
                wrongTypes,
                contactRow("pas un numero", "Alice"),
                contactRow("06 33 33 33 33", "Espacé"),
                contactRow("0644444444", ""),
                contactRow("0655555555", "§§ "),
                contactRow("0666666666", "a".repeat(33)),
                contactRow(ALICE, "Alice"),
                contactRow(ALICE, "Doublon"),
                contactRow(BOB, "  §cBob\n le  fou "));
        List<CompoundTag> recents = List.of(
                new CompoundTag(),
                wrongTypes,
                recentRow(BOB, "TELEPATHIE", 1L, 1),
                recentRow(BOB, "", 1L, 1),
                recentRow("", "OUTGOING", 1L, 1),
                recentRow("pas un numero", "OUTGOING", 1L, 1),
                noDate,
                textDate,
                noDuration,
                textDuration,
                recentRow(ALICE, "INCOMING", 10L, 2),
                recentRow(BOB, "OUTGOING", -10L, -2));

        PhoneDirectory back = PhoneDirectory.load(saved(bookRow(SIM, contacts, recents)));

        // Un nom piégé écrit dans le fichier est nettoyé ; un doublon de numéro est ignoré (le premier gagne).
        assertEquals(List.of(new Contact(ALICE, "Alice"), new Contact(BOB, "cBob le fou")), back.contacts(SIM));
        assertEquals(List.of(new Recent(ALICE, Recent.Kind.INCOMING, 10L, 2),
                new Recent(BOB, Recent.Kind.OUTGOING, 0L, 0)), back.recents(SIM));
        assertFalse(back.isDirty());
    }

    @Test
    void unCarnetSansCarteLisibleOuEnDoubleEstIgnore() {
        CompoundTag noSim = bookRow(SIM, List.of(contactRow(ALICE, "Sans carte")), List.of());
        noSim.remove("sim");
        CompoundTag textSim = bookRow(SIM, List.of(contactRow(ALICE, "Texte")), List.of());
        textSim.putString("sim", SIM.toString());
        CompoundTag shortSim = bookRow(SIM, List.of(contactRow(ALICE, "Court")), List.of());
        shortSim.putIntArray("sim", new int[] {1, 2});
        CompoundTag wrongLists = new CompoundTag();
        wrongLists.putUUID("sim", uuid(7));
        wrongLists.putString("contacts", "x");
        wrongLists.putInt("recents", 4);
        CompoundTag empty = bookRow(uuid(8), List.of(), List.of());

        PhoneDirectory back = PhoneDirectory.load(saved(new CompoundTag(), noSim, textSim, shortSim, wrongLists, empty,
                bookRow(SIM, List.of(contactRow(ALICE, "Alice")), List.of()),
                bookRow(SIM, List.of(contactRow(BOB, "Doublon")), List.of(recentRow(BOB, "MISSED", 1L, 0))),
                bookRow(OTHER, List.of(), List.of(recentRow(BOB, "MISSED", 1L, 0)))));

        assertEquals(List.of(new Contact(ALICE, "Alice")), back.contacts(SIM));
        assertEquals(List.of(), back.recents(SIM));
        assertEquals(1, back.recents(OTHER).size());
        // Les carnets sans rien de lisible ne sont pas gardés.
        assertEquals(2, back.save(new CompoundTag()).getList("books", Tag.TAG_COMPOUND).size());
    }

    @Test
    void lesListesTropLonguesSontBorneesALaLecture() {
        List<CompoundTag> contacts = new ArrayList<>();
        List<CompoundTag> recents = new ArrayList<>();
        // Des lignes illisibles en tête ne prennent pas la place des bonnes.
        for (int i = 0; i < 20; i++) {
            contacts.add(new CompoundTag());
            recents.add(new CompoundTag());
        }
        for (int i = 0; i < 150; i++) {
            contacts.add(contactRow(number(i), "Contact " + i));
            recents.add(recentRow(number(i), "OUTGOING", i, i));
        }

        PhoneDirectory back = PhoneDirectory.load(saved(bookRow(SIM, contacts, recents)));

        assertEquals(100, back.contacts(SIM).size());
        assertEquals(Optional.of("Contact 99"), back.nameOf(SIM, number(99)));
        assertEquals(Optional.empty(), back.nameOf(SIM, number(100)));
        assertEquals(50, back.recents(SIM).size());
        // La liste est écrite la plus récente en tête : ce sont les premières lignes qui restent.
        assertEquals(number(0), back.recents(SIM).get(0).number());
        assertEquals(number(49), back.recents(SIM).get(49).number());
        assertEquals(PhoneDirectory.SaveResult.FULL, back.saveContact(SIM, number(100), "De trop"));
    }
}
