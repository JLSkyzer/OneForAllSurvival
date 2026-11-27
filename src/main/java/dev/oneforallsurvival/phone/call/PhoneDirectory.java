package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.phone.net.SimNumbers;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Le carnet de chaque carte SIM : ses contacts et ses appels récents, gardés par le serveur (données du monde
 * principal, écrites par le jeu avec le reste du monde). Tout est rangé par identifiant de carte ({@code simId}) : le
 * carnet suit la carte d'un téléphone à l'autre, et une carte remplacée le garde. Au plus
 * {@link ContactRules#MAX_CONTACTS} contacts et {@link ContactRules#MAX_RECENTS} appels par carte, à l'écriture comme
 * à la lecture.
 *
 * <p>Rien ici ne vérifie qu'une carte existe ni qu'un joueur a le droit d'y toucher : l'appelant passe le {@code simId}
 * de la ligne qu'il a vérifiée ({@code PhoneSims.line}). Ne se touche que depuis le fil du serveur.
 */
public final class PhoneDirectory extends SavedData {

    public static final String KEY = "oneforallsurvival_phone_directory";

    /** Issue d'un enregistrement de contact. */
    public enum SaveResult { SAVED, BAD_NUMBER, BAD_NAME, FULL }

    /** Le carnet d'une carte : ses contacts par numéro, ses appels du plus récent au plus ancien. */
    private static final class Book {
        private final Map<String, Contact> contacts = new LinkedHashMap<>();
        private final List<Recent> recents = new ArrayList<>();

        private boolean isEmpty() {
            return contacts.isEmpty() && recents.isEmpty();
        }
    }

    /** Identifiant de carte → carnet ; un carnet n'est gardé que s'il contient quelque chose. */
    private final Map<UUID, Book> books = new LinkedHashMap<>();

    /** Un registre neuf, détaché du monde (essais) ; celui du serveur s'obtient par {@link #get}. */
    public PhoneDirectory() {}

    public static PhoneDirectory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PhoneDirectory::load, PhoneDirectory::new, KEY);
    }

    // ---------------------------------------------------------------------------------------------
    // Contacts
    // ---------------------------------------------------------------------------------------------

    /**
     * Les contacts de cette carte, triés par nom sans tenir compte de la casse, les lettres accentuées à leur place
     * (« Émile » entre « delta » et « Fanny ») ; à nom égal, par numéro. Copie non modifiable.
     */
    public List<Contact> contacts(UUID simId) {
        Book book = books.get(simId);
        if (book == null) {
            return List.of();
        }
        Collator collator = Collator.getInstance(Locale.ROOT);
        collator.setStrength(Collator.SECONDARY);
        Comparator<Contact> byName = (a, b) -> collator.compare(a.name(), b.name());
        return book.contacts.values().stream().sorted(byName.thenComparing(Contact::number)).toList();
    }

    /**
     * Enregistre un contact, ou le renomme si ce numéro est déjà au carnet. Le numéro passe par
     * {@link SimNumbers#parse} et le nom par {@link ContactRules#cleanName} ; un refus ne change rien.
     *
     * @return {@code BAD_NUMBER} (jugé d'abord), {@code BAD_NAME}, {@code FULL} pour un numéro nouveau dans un carnet
     *         qui a déjà {@link ContactRules#MAX_CONTACTS} contacts, sinon {@code SAVED}
     */
    public SaveResult saveContact(UUID simId, String rawNumber, String rawName) {
        Objects.requireNonNull(simId, "simId");
        Optional<String> number = SimNumbers.parse(rawNumber);
        if (number.isEmpty()) {
            return SaveResult.BAD_NUMBER;
        }
        Optional<String> name = ContactRules.cleanName(rawName);
        if (name.isEmpty()) {
            return SaveResult.BAD_NAME;
        }
        Book book = books.get(simId);
        if (book != null && book.contacts.size() >= ContactRules.MAX_CONTACTS
                && !book.contacts.containsKey(number.get())) {
            return SaveResult.FULL;
        }
        Contact contact = new Contact(number.get(), name.get());
        Contact before = books.computeIfAbsent(simId, id -> new Book()).contacts.put(contact.number(), contact);
        if (!contact.equals(before)) {
            setDirty();
        }
        return SaveResult.SAVED;
    }

    /**
     * Retire le contact de ce numéro (écrit avec ou sans espaces).
     *
     * @return faux, et rien ne change, si ce numéro n'est pas au carnet de cette carte
     */
    public boolean removeContact(UUID simId, String number) {
        Book book = books.get(simId);
        Optional<String> parsed = SimNumbers.parse(number);
        if (book == null || parsed.isEmpty() || book.contacts.remove(parsed.get()) == null) {
            return false;
        }
        if (book.isEmpty()) {
            books.remove(simId);
        }
        setDirty();
        return true;
    }

    /** Le nom sous lequel cette carte connaît ce numéro (écrit avec ou sans espaces), s'il est à son carnet. */
    public Optional<String> nameOf(UUID simId, String number) {
        Book book = books.get(simId);
        if (book == null) {
            return Optional.empty();
        }
        return SimNumbers.parse(number).map(book.contacts::get).map(Contact::name);
    }

    // ---------------------------------------------------------------------------------------------
    // Récents
    // ---------------------------------------------------------------------------------------------

    /**
     * Inscrit un appel en tête des récents de cette carte (l'ordre est celui des inscriptions) ; au-delà de
     * {@link ContactRules#MAX_RECENTS}, le plus ancien tombe. Un appel dont le numéro ne se garde pas (autre chose que
     * un à dix chiffres, voir {@link Recent}) n'est pas inscrit.
     */
    public void log(UUID simId, Recent entry) {
        Objects.requireNonNull(simId, "simId");
        if (!entry.hasStorableNumber()) {
            return;
        }
        List<Recent> recents = books.computeIfAbsent(simId, id -> new Book()).recents;
        recents.add(0, entry);
        while (recents.size() > ContactRules.MAX_RECENTS) {
            recents.remove(recents.size() - 1);
        }
        setDirty();
    }

    /** Les appels de cette carte, du plus récent au plus ancien. Copie non modifiable. */
    public List<Recent> recents(UUID simId) {
        Book book = books.get(simId);
        return book == null ? List.of() : List.copyOf(book.recents);
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag bookList = new ListTag();
        books.forEach((simId, book) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("sim", simId);
            ListTag contacts = new ListTag();
            book.contacts.values().forEach(contact -> contacts.add(contact.save()));
            row.put("contacts", contacts);
            ListTag recents = new ListTag();
            book.recents.forEach(recent -> recents.add(recent.save()));
            row.put("recents", recents);
            bookList.add(row);
        });
        tag.put("books", bookList);
        return tag;
    }

    /**
     * Relit les carnets, sans jamais planter : une entrée illisible est ignorée ({@link Contact#load},
     * {@link Recent#load}), un doublon aussi (carnet d'une carte déjà relue, contact d'un numéro déjà relu : le premier
     * gagne), et chaque liste s'arrête à son plafond. Les récents sont écrits le plus récent en tête : ce sont les plus
     * anciens qui tombent.
     */
    public static PhoneDirectory load(CompoundTag tag) {
        PhoneDirectory directory = new PhoneDirectory();
        for (Tag entry : tag.getList("books", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            if (!row.hasUUID("sim") || directory.books.containsKey(row.getUUID("sim"))) {
                continue;
            }
            Book book = readBook(row);
            if (!book.isEmpty()) {
                directory.books.put(row.getUUID("sim"), book);
            }
        }
        return directory;
    }

    private static Book readBook(CompoundTag row) {
        Book book = new Book();
        for (Tag entry : row.getList("contacts", Tag.TAG_COMPOUND)) {
            if (book.contacts.size() >= ContactRules.MAX_CONTACTS) {
                break;
            }
            Contact contact = Contact.load((CompoundTag) entry);
            if (contact != null) {
                book.contacts.putIfAbsent(contact.number(), contact);
            }
        }
        for (Tag entry : row.getList("recents", Tag.TAG_COMPOUND)) {
            if (book.recents.size() >= ContactRules.MAX_RECENTS) {
                break;
            }
            Recent recent = Recent.load((CompoundTag) entry);
            if (recent != null) {
                book.recents.add(recent);
            }
        }
        return book;
    }
}
