package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.SimCard;
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
import java.util.function.Predicate;
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
 * <p>Au plus {@link ContactRules#MAX_BOOKS} carnets sur le serveur : une carte désactivée ou inconnue du registre ne
 * reçoit pas de carnet neuf, et au plafond ce sont les carnets de ces cartes mortes qui sont oubliés (ceux des cartes
 * actives ou suspendues, jamais ; sous le plafond, une carte morte garde le sien pour la fouille). Rien ici ne vérifie
 * qu'un joueur a le droit de toucher à une carte : l'appelant passe le {@code simId} de la ligne qu'il a vérifiée
 * ({@code PhoneSims.line}). Ne se touche que depuis le fil du serveur.
 */
public final class PhoneDirectory extends SavedData {

    public static final String KEY = "oneforallsurvival_phone_directory";

    /** Issue d'un enregistrement de contact. */
    public enum SaveResult { SAVED, BAD_NUMBER, BAD_NAME, FULL, DEAD_CARD, DIRECTORY_FULL }

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

    /**
     * La carte est-elle vivante (connue du registre et non désactivée) ? Seule une carte vivante reçoit un carnet neuf,
     * et seuls les carnets de cartes mortes sont oubliés au plafond ({@link ContactRules#MAX_BOOKS}).
     */
    private Predicate<UUID> liveCard;

    /** Un registre neuf, détaché du monde (essais), où toute carte est vivante ; celui du serveur : {@link #get}. */
    public PhoneDirectory() {
        this(simId -> true);
    }

    /** Un registre neuf, détaché du monde (essais), qui juge les cartes par {@code liveCard}. */
    public PhoneDirectory(Predicate<UUID> liveCard) {
        this.liveCard = Objects.requireNonNull(liveCard, "liveCard");
    }

    /** Le carnet du serveur ; une carte y est vivante si le registre la connaît et qu'elle n'est pas désactivée. */
    public static PhoneDirectory get(MinecraftServer server) {
        PhoneDirectory directory = server.overworld().getDataStorage()
                .computeIfAbsent(PhoneDirectory::load, PhoneDirectory::new, KEY);
        directory.liveCard = simId -> PhoneOperators.get(server).card(simId)
                .filter(card -> card.state() != SimCard.State.DEACTIVATED).isPresent();
        return directory;
    }

    /** Peut-on écrire au carnet de cette carte ? */
    private enum Room { YES, DEAD_CARD, FULL }

    /**
     * Un carnet existant s'écrit toujours. Un carnet neuf n'est ouvert qu'à une carte vivante, et seulement sous
     * {@link ContactRules#MAX_BOOKS} : au plafond, les carnets des cartes mortes sont oubliés d'abord, ceux des cartes
     * vivantes jamais. Ne crée rien.
     */
    private Room roomFor(UUID simId) {
        if (books.containsKey(simId)) {
            return Room.YES;
        }
        if (!liveCard.test(simId)) {
            return Room.DEAD_CARD;
        }
        if (books.size() >= ContactRules.MAX_BOOKS && books.keySet().removeIf(id -> !liveCard.test(id))) {
            setDirty();
        }
        return books.size() >= ContactRules.MAX_BOOKS ? Room.FULL : Room.YES;
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
     *         qui a déjà {@link ContactRules#MAX_CONTACTS} contacts, {@code DEAD_CARD} pour un carnet neuf d'une carte
     *         désactivée ou inconnue, {@code DIRECTORY_FULL} pour un carnet neuf quand le serveur garde déjà
     *         {@link ContactRules#MAX_BOOKS} carnets de cartes vivantes, sinon {@code SAVED}
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
        switch (roomFor(simId)) {
            case DEAD_CARD -> {
                return SaveResult.DEAD_CARD;
            }
            case FULL -> {
                return SaveResult.DIRECTORY_FULL;
            }
            case YES -> { }
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
     * {@link ContactRules#MAX_RECENTS}, le plus ancien tombe. Un numéro de carte écrit avec ses espaces est inscrit
     * sans elles ({@link SimNumbers#parse}), comme dans {@link #saveContact}.
     *
     * @return vrai si l'appel est inscrit ; faux, sans rien changer, si son numéro ne se garde pas (autre chose que un à
     *         dix chiffres ASCII, voir {@link Recent}), ou s'il faudrait un carnet neuf à une carte morte ou au-delà du
     *         plafond des carnets ({@link #saveContact})
     */
    public boolean log(UUID simId, Recent entry) {
        Objects.requireNonNull(simId, "simId");
        Optional<String> card = SimNumbers.parse(entry.number());
        if (card.isPresent() && !card.get().equals(entry.number())) {
            entry = new Recent(card.get(), entry.kind(), entry.atMillis(), entry.seconds());
        }
        if (!entry.hasStorableNumber() || roomFor(simId) != Room.YES) {
            return false;
        }
        List<Recent> recents = books.computeIfAbsent(simId, id -> new Book()).recents;
        recents.add(0, entry);
        while (recents.size() > ContactRules.MAX_RECENTS) {
            recents.remove(recents.size() - 1);
        }
        setDirty();
        return true;
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
     * gagne), et chaque liste s'arrête à son plafond, comme le nombre de carnets ({@link ContactRules#MAX_BOOKS}, les
     * premiers lus restent). Les récents sont écrits le plus récent en tête : ce sont les plus anciens qui tombent.
     */
    public static PhoneDirectory load(CompoundTag tag) {
        PhoneDirectory directory = new PhoneDirectory();
        for (Tag entry : tag.getList("books", Tag.TAG_COMPOUND)) {
            if (directory.books.size() >= ContactRules.MAX_BOOKS) {
                break;
            }
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
