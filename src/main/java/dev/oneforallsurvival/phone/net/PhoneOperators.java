package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinStore;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.phone.PhoneConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Le registre du réseau téléphonique, gardé par le serveur (données du monde principal, écrites par le jeu avec le
 * reste du monde) : les opérateurs et leur caisse, les cartes SIM, les demandes de carte, les antennes, le plafond des
 * tarifs et les avis aux absents. Rien de tout cela ne vit dans un bloc ni dans un téléphone. Ne se touche que depuis
 * le fil du serveur ; chaque changement marque le fichier sale, sauf {@link #noteTick} (voir sa documentation).
 *
 * <p>Un opérateur par joueur (son fondateur), un nom unique sans casse. Un numéro de carte n'est jamais réattribué :
 * {@code usedNumbers} garde tous les numéros jamais émis, cartes désactivées comprises, et se sauvegarde. Le registre
 * ne crée ni ne détruit aucune pièce : la caisse ne reçoit que ce que l'appelant a pris ailleurs.
 *
 * <p>Le jeton d'une carte ({@link SimCard#token()}) est un secret : rien ici ne l'écrit dans un message d'erreur.
 */
public final class PhoneOperators extends SavedData {

    public static final String KEY = "oneforallsurvival_phone_operators";

    /** Avis gardés par joueur absent ; les plus anciens tombent. */
    public static final int MAX_NOTICES = 16;

    /** Tics entre deux marquages « sale » dus à {@link #noteTick} (hors changement d'état de marche). */
    public static final int FLUSH_TICKS = 20;

    private static final long NEVER = Long.MIN_VALUE;

    /** Issue d'une fondation. */
    public enum Status { FOUNDED, BAD_NAME, NAME_TAKEN, ALREADY_HAS_ONE }

    /** Ce qu'a donné {@link #found} : l'opérateur créé, ou nul si refusé. */
    public record Result(Status status, @Nullable PhoneOperator operator) {

        public boolean founded() {
            return status == Status.FOUNDED;
        }
    }

    /**
     * Ce qu'a changé l'entrée en vigueur des tarifs d'un opérateur.
     *
     * @param before le tarif d'hier ; @param after celui d'aujourd'hui ; @param capped vrai si le plafond l'a ramené
     *        en dessous de ce qui était demandé (ou en vigueur)
     */
    public record DayChange(String operatorId, long before, long after, boolean capped) {}

    private final Map<String, PhoneOperator> operators = new LinkedHashMap<>();
    private final Map<UUID, SimCard> cards = new LinkedHashMap<>();
    /** Index (non sauvegardé, refait à la lecture) : numéro de carte → carte. */
    private final Map<String, UUID> cardByNumber = new HashMap<>();
    /** Tous les numéros jamais émis : un numéro n'est jamais réattribué, même celui d'une carte désactivée. */
    private final Set<String> usedNumbers = new LinkedHashSet<>();
    private final Map<UUID, SimRequest> requests = new LinkedHashMap<>();
    private final Map<UUID, AntennaRecord> antennas = new LinkedHashMap<>();
    /** Avis à donner à un joueur absent (texte sérialisé), lus à sa connexion ; au plus {@link #MAX_NOTICES} chacun. */
    private final Map<UUID, List<String>> notices = new HashMap<>();
    @Nullable
    private Long tariffCap;
    @Nullable
    private Long nextTariffCap;
    /** Dernier temps de jeu où {@link #noteTick} a marqué le registre sale (non sauvegardé). */
    private long lastFlushTick = NEVER;
    /** Monte à chaque changement de l'état d'une carte (non sauvegardé) : voir {@link #cardRevision()}. */
    private int cardRevision;
    /** Le trafic ouvert par antenne, appels et visios (transitoire, jamais sauvegardé) : voir {@link #traffic}. */
    private final Map<UUID, int[]> traffic = new HashMap<>();

    /** Un registre neuf, détaché du monde (essais) ; celui du serveur s'obtient par {@link #get}. */
    public PhoneOperators() {}

    public static PhoneOperators get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PhoneOperators::load, PhoneOperators::new, KEY);
    }

    // ---------------------------------------------------------------------------------------------
    // Opérateurs
    // ---------------------------------------------------------------------------------------------

    private static String fold(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Fonde un opérateur pour {@code founder}, sans antenne ni tarif (le tarif est 0 tant que le fondateur n'en a pas
     * demandé un). Refusé si le nom n'est pas valable ({@link PowerSuppliers#validName}) ou déjà pris (sans casse), ou
     * si ce joueur en a déjà un.
     */
    public Result found(UUID founder, String founderName, String name, long now) {
        Optional<String> valid = PowerSuppliers.validName(name);
        if (valid.isEmpty()) {
            return new Result(Status.BAD_NAME, null);
        }
        if (byFounder(founder).isPresent()) {
            return new Result(Status.ALREADY_HAS_ONE, null);
        }
        if (byName(valid.get()).isPresent()) {
            return new Result(Status.NAME_TAKEN, null);
        }
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (operators.containsKey(id));
        PhoneOperator operator = new PhoneOperator(id, valid.get(), founder, founderName, 0L, null,
                new EnumMap<>(Coin.class), null, Math.max(0L, now));
        operators.put(id, operator);
        setDirty();
        return new Result(Status.FOUNDED, operator);
    }

    public Optional<PhoneOperator> byId(@Nullable String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(operators.get(id));
    }

    public Optional<PhoneOperator> byName(String name) {
        String wanted = fold(name);
        for (PhoneOperator operator : operators.values()) {
            if (fold(operator.name()).equals(wanted)) {
                return Optional.of(operator);
            }
        }
        return Optional.empty();
    }

    public Optional<PhoneOperator> byFounder(UUID founder) {
        for (PhoneOperator operator : operators.values()) {
            if (operator.founder().equals(founder)) {
                return Optional.of(operator);
            }
        }
        return Optional.empty();
    }

    /** Tous les opérateurs, dans l'ordre de fondation (copie). */
    public Collection<PhoneOperator> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(operators.values()));
    }

    private void replace(PhoneOperator operator) {
        operators.put(operator.id(), operator);
        setDirty();
    }

    /**
     * Retire cet opérateur du registre et désactive aussitôt toutes ses cartes (leurs numéros restent réservés pour
     * toujours : aucune carte active ne survit à son opérateur). Rien d'autre ne bouge : ses antennes et ses demandes
     * restent là où elles sont, à la charge de l'appelant.
     *
     * <p>La caisse n'est pas protégée : l'appelant doit la verser avant (au fondateur) ; une caisse non vide est perdue
     * avec l'opérateur. Le refus de retirer un opérateur dont la caisse n'est pas vide viendra avec la suite de la
     * suppression (tâche 11).
     *
     * @return faux, et rien ne bouge, si l'opérateur n'existe pas
     */
    public boolean remove(String id) {
        if (operators.remove(id) == null) {
            return false;
        }
        cards.replaceAll((simId, card) -> card.operatorId().equals(id) ? card.withState(SimCard.State.DEACTIVATED) : card);
        cardRevision++;
        setDirty();
        return true;
    }

    /**
     * L'opérateur passe à un autre fondateur (geste du maire ou de l'équipe) : caisse, cartes et antennes le suivent.
     *
     * @return faux, et rien ne bouge, si l'opérateur n'existe pas ou si ce joueur en a déjà un (un par joueur)
     */
    public boolean setFounder(String id, UUID founder, String founderName) {
        PhoneOperator operator = operators.get(id);
        if (operator == null || byFounder(founder).isPresent()) {
            return false;
        }
        replace(operator.withFounder(founder, founderName));
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Cartes SIM
    // ---------------------------------------------------------------------------------------------

    /**
     * Émet une carte active pour cet opérateur : numéro tiré parmi ceux jamais émis, jeton neuf, aucun téléphone.
     * {@code holder} nul : carte sans nom. Le {@code RandomSource} ne sert qu'au numéro : l'identifiant et le jeton de la
     * carte viennent de {@link UUID#randomUUID()} (un jeton tiré d'un générateur congruentiel se déduit de
     * l'identifiant, que le client voit).
     *
     * @throws IllegalArgumentException si l'opérateur n'existe pas (rien n'est tiré ni réservé)
     * @throws IllegalStateException si le plan de numérotation est saturé ({@link SimNumbers#draw})
     */
    public SimCard issue(String operatorId, @Nullable UUID holder, String holderName, RandomSource random, long now) {
        if (!operators.containsKey(operatorId)) {
            throw new IllegalArgumentException("opérateur inconnu " + operatorId);
        }
        String number = SimNumbers.draw(random, usedNumbers::contains);
        UUID simId;
        do {
            simId = UUID.randomUUID();
        } while (cards.containsKey(simId));
        SimCard card = new SimCard(simId, number, operatorId, holder, holderName == null ? "" : holderName,
                SimCard.State.ACTIVE, UUID.randomUUID(), null, Math.max(0L, now));
        cards.put(simId, card);
        cardByNumber.put(number, simId);
        usedNumbers.add(number);
        cardRevision++;
        setDirty();
        return card;
    }

    /**
     * Change à chaque émission, à chaque changement d'état d'une carte et à chaque retrait d'opérateur : tant qu'elle
     * ne bouge pas, {@link #activeCards} rend la même chose pour chaque opérateur. Une antenne y garde son décompte
     * ({@link ActiveCardCache}) au lieu de parcourir toutes les cartes à chaque tic. Non sauvegardée : un registre relu
     * repart de zéro, et le cache se rafraîchit aussi quand le registre n'est plus le même objet.
     */
    public int cardRevision() {
        return cardRevision;
    }

    /**
     * Le trafic ouvert sur cette antenne ({@link PhoneNetwork#openTraffic}) : combien d'appels ou de visios passent
     * par elle maintenant. Transitoire : rien n'est sauvegardé, un registre relu (monde rechargé) repart de zéro.
     */
    public int traffic(UUID antennaId, TrafficKind kind) {
        int[] counts = traffic.get(antennaId);
        return counts == null ? 0 : counts[kind.ordinal()];
    }

    /**
     * Ajoute {@code delta} au trafic de cette antenne (jamais sous zéro). Ne marque pas le registre sale : rien de ce
     * trafic n'est écrit sur le disque.
     */
    void addTraffic(UUID antennaId, TrafficKind kind, int delta) {
        int[] counts = traffic.computeIfAbsent(antennaId, id -> new int[TrafficKind.values().length]);
        counts[kind.ordinal()] = Math.max(0, counts[kind.ordinal()] + delta);
        for (int count : counts) {
            if (count > 0) {
                return;
            }
        }
        traffic.remove(antennaId);
    }

    public Optional<SimCard> card(UUID simId) {
        return Optional.ofNullable(cards.get(simId));
    }

    /** La carte de ce numéro (écrit avec ou sans espaces) ; vide si aucune carte ne l'a, désactivée ou non. */
    public Optional<SimCard> cardByNumber(String number) {
        return SimNumbers.parse(number).map(cardByNumber::get).map(cards::get);
    }

    /** Les cartes de cet opérateur, dans l'ordre d'émission. */
    public List<SimCard> cardsOf(String operatorId) {
        List<SimCard> out = new ArrayList<>();
        for (SimCard card : cards.values()) {
            if (card.operatorId().equals(operatorId)) {
                out.add(card);
            }
        }
        return out;
    }

    /** Combien de cartes actives (ni suspendues ni désactivées) cet opérateur a-t-il ? */
    public int activeCards(String operatorId) {
        int count = 0;
        for (SimCard card : cards.values()) {
            if (card.state() == SimCard.State.ACTIVE && card.operatorId().equals(operatorId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Change l'état d'une carte. Le numéro reste à elle, ou réservé, dans tous les cas. Une carte désactivée l'est
     * pour toujours, et une carte ne redevient active que si son opérateur existe encore.
     *
     * @return faux, et rien ne bouge, si la carte n'existe pas, si elle est désactivée et la cible autre chose, ou si
     *         la cible est {@code ACTIVE} et que l'opérateur de la carte n'existe plus
     */
    public boolean setState(UUID simId, SimCard.State state) {
        SimCard card = cards.get(simId);
        if (card == null) {
            return false;
        }
        if (card.state() == SimCard.State.DEACTIVATED && state != SimCard.State.DEACTIVATED) {
            return false;
        }
        if (state == SimCard.State.ACTIVE && !operators.containsKey(card.operatorId())) {
            return false;
        }
        if (card.state() != state) {
            cards.put(simId, card.withState(state));
            cardRevision++;
            setDirty();
        }
        return true;
    }

    /**
     * La carte est insérée dans un téléphone : son jeton change (l'ancien ne vaut plus rien) et le téléphone est noté.
     *
     * @param random n'est plus utilisé : le jeton vient de {@link UUID#randomUUID()} (voir {@link #issue}) ; le
     *        paramètre reste pour ne pas changer la signature des appelants
     * @return le nouveau jeton, toujours différent de l'ancien
     * @throws IllegalArgumentException si la carte n'existe pas
     */
    public UUID rotateToken(UUID simId, UUID phoneId, RandomSource random) {
        SimCard card = cards.get(simId);
        if (card == null) {
            throw new IllegalArgumentException("carte inconnue " + simId);
        }
        UUID token;
        do {
            token = UUID.randomUUID();
        } while (token.equals(card.token()));
        cards.put(simId, card.withToken(token, phoneId));
        setDirty();
        return token;
    }

    /** La carte n'est plus dans un téléphone (rien si elle n'existe pas ou n'en avait pas). */
    public void unbind(UUID simId) {
        SimCard card = cards.get(simId);
        if (card != null && card.phoneId() != null) {
            cards.put(simId, card.withPhone(null));
            setDirty();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Demandes de carte
    // ---------------------------------------------------------------------------------------------

    /**
     * Le joueur demande une carte à cet opérateur. Une demande par joueur et par opérateur, au plus
     * {@code simRequestsMax} par opérateur.
     *
     * @return la demande, ou vide si l'opérateur n'existe pas, si ce joueur a déjà demandé, ou si la file est pleine
     */
    public Optional<SimRequest> request(String operatorId, UUID player, String name, long now) {
        if (!operators.containsKey(operatorId)) {
            return Optional.empty();
        }
        int waiting = 0;
        for (SimRequest request : requests.values()) {
            if (request.operatorId().equals(operatorId)) {
                if (request.player().equals(player)) {
                    return Optional.empty();
                }
                waiting++;
            }
        }
        if (waiting >= PhoneConfig.simRequestsMax()) {
            return Optional.empty();
        }
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (requests.containsKey(id));
        SimRequest request = new SimRequest(id, operatorId, player, name, Math.max(0L, now));
        requests.put(id, request);
        setDirty();
        return Optional.of(request);
    }

    /** Les demandes en attente chez cet opérateur, de la plus ancienne à la plus récente. */
    public List<SimRequest> requestsOf(String operatorId) {
        List<SimRequest> out = new ArrayList<>();
        for (SimRequest request : requests.values()) {
            if (request.operatorId().equals(operatorId)) {
                out.add(request);
            }
        }
        return out;
    }

    /** Retire cette demande (acceptée ou refusée) et la rend ; vide si elle n'existe pas ou a déjà été prise. */
    public Optional<SimRequest> takeRequest(UUID id) {
        SimRequest removed = requests.remove(id);
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------------------------------------
    // Antennes
    // ---------------------------------------------------------------------------------------------

    /**
     * Ajoute ou remplace l'antenne de ce numéro. Si son opérateur n'avait pas d'antenne principale, elle le devient.
     */
    public void putAntenna(AntennaRecord antenna) {
        AntennaRecord before = antennas.put(antenna.id(), antenna);
        setDirty();
        if (before != null && !before.operatorId().equals(antenna.operatorId())) {
            electMain(before.operatorId());
        }
        electMain(antenna.operatorId());
    }

    public Optional<AntennaRecord> antenna(UUID id) {
        return Optional.ofNullable(antennas.get(id));
    }

    /** Les antennes de cet opérateur, dans l'ordre où le registre les a reçues. */
    public List<AntennaRecord> antennasOf(String operatorId) {
        List<AntennaRecord> out = new ArrayList<>();
        for (AntennaRecord antenna : antennas.values()) {
            if (antenna.operatorId().equals(operatorId)) {
                out.add(antenna);
            }
        }
        return out;
    }

    /** Toutes les antennes (copie). */
    public Collection<AntennaRecord> antennas() {
        return Collections.unmodifiableCollection(new ArrayList<>(antennas.values()));
    }

    /**
     * Retire cette antenne. Si c'était la principale de son opérateur, la plus ancienne qui reste (la plus petite
     * {@link AntennaRecord#placedAt()}) la remplace ; sinon l'opérateur n'a plus de principale.
     */
    public void removeAntenna(UUID id) {
        AntennaRecord removed = antennas.remove(id);
        if (removed == null) {
            return;
        }
        setDirty();
        PhoneOperator operator = operators.get(removed.operatorId());
        if (operator != null && id.equals(operator.mainAntenna())) {
            electMain(removed.operatorId());
        }
    }

    /**
     * Désigne l'antenne principale de cet opérateur (geste du fondateur).
     *
     * @return faux, et rien ne bouge, si l'opérateur ou l'antenne n'existe pas ou si l'antenne est celle d'un autre
     *         opérateur ; vrai sinon (le registre n'est marqué sale que si la principale change)
     */
    public boolean setMainAntenna(String operatorId, UUID antennaId) {
        PhoneOperator operator = operators.get(operatorId);
        AntennaRecord antenna = antennas.get(antennaId);
        if (operator == null || antenna == null || !antenna.operatorId().equals(operatorId)) {
            return false;
        }
        if (!antennaId.equals(operator.mainAntenna())) {
            replace(operator.withMainAntenna(antennaId));
        }
        return true;
    }

    /**
     * Si l'antenne principale de cet opérateur n'existe plus (ou n'est pas à lui), la plus ancienne de ses antennes la
     * remplace ; à égalité de pose, la première reçue. Nulle s'il n'en a plus.
     */
    private void electMain(String operatorId) {
        PhoneOperator operator = operators.get(operatorId);
        if (operator == null) {
            return;
        }
        AntennaRecord current = operator.mainAntenna() == null ? null : antennas.get(operator.mainAntenna());
        if (current != null && current.operatorId().equals(operatorId)) {
            return;
        }
        AntennaRecord oldest = null;
        for (AntennaRecord antenna : antennas.values()) {
            if (antenna.operatorId().equals(operatorId) && (oldest == null || antenna.placedAt() < oldest.placedAt())) {
                oldest = antenna;
            }
        }
        UUID next = oldest == null ? null : oldest.id();
        if (!Objects.equals(next, operator.mainAntenna())) {
            replace(operator.withMainAntenna(next));
        }
    }

    /**
     * Note ce que l'antenne a fait à ce tic : en marche ou non, vue à ce temps de jeu, {@code fe} consommés (ajoutés à
     * la consommation du jour) et le détail du tic. Cela se passe à chaque tic de chaque antenne : le changement vit en
     * mémoire, et le registre n'est marqué sale qu'au plus tous les {@value #FLUSH_TICKS} tics, ou dès que
     * {@code running} change (une panne ou un retour ne doit pas attendre). Ce que le jeu écrit à la sauvegarde est
     * l'état du moment. Rien ne se passe pour une antenne inconnue.
     */
    public void noteTick(UUID id, boolean running, long gameTime, int fe, AntennaLoad.Breakdown breakdown) {
        AntennaRecord antenna = antennas.get(id);
        if (antenna == null) {
            return;
        }
        antennas.put(id, antenna.withTick(running, gameTime, saturatedAdd(antenna.feToday(), Math.max(0, fe)),
                breakdown));
        boolean due = lastFlushTick == NEVER || gameTime < lastFlushTick || gameTime - lastFlushTick >= FLUSH_TICKS;
        if (running != antenna.running() || due) {
            lastFlushTick = gameTime;
            setDirty();
        }
    }

    /** Clôt le jour des antennes : la consommation du jour passe à « hier » et repart de zéro. */
    public void closeAntennaDay() {
        if (antennas.isEmpty()) {
            return;
        }
        antennas.replaceAll((id, antenna) -> antenna.withDayClosed());
        setDirty();
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }

    // ---------------------------------------------------------------------------------------------
    // Tarifs (au jour suivant)
    // ---------------------------------------------------------------------------------------------

    /**
     * Tarif du lendemain de cet opérateur, en cuivres. Il entre en vigueur à la clôture de minuit
     * ({@link #applyNextTariffs}), ramené au plafond de ce jour-là.
     *
     * @return faux, et rien ne bouge, si l'opérateur n'existe pas ou si le tarif est négatif ou dépasse
     *         {@code phoneTariffMax}
     */
    public boolean setNextTariff(String id, long tariff) {
        PhoneOperator operator = operators.get(id);
        if (operator == null || tariff < 0L || tariff > PhoneConfig.phoneTariffMax()) {
            return false;
        }
        replace(operator.withNextTariff(tariff));
        return true;
    }

    /** Plafond des tarifs en vigueur ; celui de la configuration tant que le maire n'a rien fixé. */
    public long tariffCap() {
        return tariffCap == null ? PhoneConfig.phoneDefaultTariffCap() : tariffCap;
    }

    /** Plafond du lendemain, s'il a été changé aujourd'hui. */
    public Optional<Long> nextTariffCap() {
        return Optional.ofNullable(nextTariffCap);
    }

    /** Fixe le plafond du lendemain (négatif : 0). Il entre en vigueur à la clôture de minuit. */
    public void setNextTariffCap(long cap) {
        nextTariffCap = Math.max(0L, cap);
        setDirty();
    }

    /**
     * Clôture de minuit : le plafond du lendemain, s'il y en a un, entre en vigueur ; puis chaque opérateur prend le
     * tarif qu'il a demandé pour aujourd'hui (le sien s'il n'a rien demandé), ramené au plafond en vigueur (même s'il
     * n'a rien demandé : un plafond abaissé s'applique à tous).
     *
     * @return un changement par opérateur dont le tarif a bougé ou a été ramené au plafond ; vide si rien de nouveau
     */
    public List<DayChange> applyNextTariffs() {
        if (nextTariffCap != null) {
            tariffCap = nextTariffCap;
            nextTariffCap = null;
            setDirty();
        }
        long cap = tariffCap();
        List<DayChange> changes = new ArrayList<>();
        for (PhoneOperator operator : List.copyOf(operators.values())) {
            long wanted = operator.nextTariff() != null ? operator.nextTariff() : operator.tariff();
            boolean capped = wanted > cap;
            long after = capped ? cap : wanted;
            if (operator.nextTariff() != null || after != operator.tariff()) {
                replace(operator.withDayClosed(after));
            }
            if (after != operator.tariff() || capped) {
                changes.add(new DayChange(operator.id(), operator.tariff(), after, capped));
            }
        }
        return changes;
    }

    // ---------------------------------------------------------------------------------------------
    // Caisse
    // ---------------------------------------------------------------------------------------------

    /** La caisse de cet opérateur, coupures nulles omises ; vide s'il n'existe pas. */
    public EnumMap<Coin, Long> till(String id) {
        PhoneOperator operator = operators.get(id);
        return operator == null ? new EnumMap<>(Coin.class) : operator.till();
    }

    /** Combien de pièces de cette coupure la caisse peut encore recevoir (0 pour un opérateur inconnu). */
    public long tillRoom(String id, Coin coin) {
        PhoneOperator operator = operators.get(id);
        return operator == null ? 0L : Math.max(0L, CoinStore.MAX_PER_COIN - operator.tillCount(coin));
    }

    /**
     * Met des pièces dans la caisse. L'appelant a vérifié {@link #tillRoom} et les a prises ailleurs : la caisse ne
     * crée rien.
     *
     * @throws IllegalArgumentException si l'opérateur n'existe pas, si le nombre est négatif ou dépasse la place (rien
     *         n'est alors écrit)
     */
    public void deposit(String id, Coin coin, long count) {
        PhoneOperator operator = operators.get(id);
        if (operator == null || count < 0L || count > tillRoom(id, coin)) {
            throw new IllegalArgumentException("caisse " + id + " : depot de " + count + " " + coin);
        }
        if (count > 0L) {
            EnumMap<Coin, Long> till = operator.till();
            till.merge(coin, count, Long::sum);
            replace(operator.withTill(till));
        }
    }

    /** Retire exactement {@code count} pièces de la caisse, ou rien. @return faux s'il y en a moins */
    public boolean withdraw(String id, Coin coin, long count) {
        PhoneOperator operator = operators.get(id);
        if (operator == null || count < 0L || operator.tillCount(coin) < count) {
            return false;
        }
        if (count > 0L) {
            EnumMap<Coin, Long> till = operator.till();
            till.put(coin, operator.tillCount(coin) - count);
            replace(operator.withTill(till));
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Avis aux absents
    // ---------------------------------------------------------------------------------------------

    /** Garde un avis pour ce joueur, à lui donner à sa connexion ({@link #takeNotices}). */
    public void addNotice(UUID player, Component message) {
        List<String> list = notices.computeIfAbsent(player, k -> new ArrayList<>());
        list.add(Component.Serializer.toJson(message));
        while (list.size() > MAX_NOTICES) {
            list.remove(0);
        }
        setDirty();
    }

    /** Les avis gardés pour ce joueur, retirés (un avis illisible est ignoré). */
    public List<Component> takeNotices(UUID player) {
        List<String> list = notices.remove(player);
        if (list == null) {
            return List.of();
        }
        setDirty();
        List<Component> out = new ArrayList<>();
        for (String json : list) {
            try {
                Component c = Component.Serializer.fromJson(json);
                if (c != null) {
                    out.add(c);
                }
            } catch (RuntimeException e) {
                // avis abîmé : ignoré
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag operatorList = new ListTag();
        operators.values().forEach(operator -> operatorList.add(operator.save()));
        tag.put("operators", operatorList);
        ListTag cardList = new ListTag();
        cards.values().forEach(card -> cardList.add(card.save()));
        tag.put("cards", cardList);
        ListTag numberList = new ListTag();
        usedNumbers.forEach(number -> numberList.add(StringTag.valueOf(number)));
        tag.put("usedNumbers", numberList);
        ListTag requestList = new ListTag();
        requests.values().forEach(request -> requestList.add(request.save()));
        tag.put("requests", requestList);
        ListTag antennaList = new ListTag();
        antennas.values().forEach(antenna -> antennaList.add(antenna.save()));
        tag.put("antennas", antennaList);
        ListTag noticeList = new ListTag();
        notices.forEach((player, lines) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            ListTag saved = new ListTag();
            lines.forEach(line -> saved.add(StringTag.valueOf(line)));
            entry.put("lines", saved);
            noticeList.add(entry);
        });
        tag.put("notices", noticeList);
        if (tariffCap != null) {
            tag.putLong("tariffCap", tariffCap);
        }
        if (nextTariffCap != null) {
            tag.putLong("nextTariffCap", nextTariffCap);
        }
        return tag;
    }

    /** Relit le registre (dimensions du jeu) ; une ligne abîmée est ignorée, jamais une raison de planter. */
    public static PhoneOperators load(CompoundTag tag) {
        return load(tag, location -> ResourceKey.create(Registries.DIMENSION, location));
    }

    /**
     * Relit le registre. Une ligne abîmée (sans identifiant, champ du mauvais type, énumération inconnue, numéro qui
     * n'en est pas un, dimension absente ou illisible) est ignorée, et un doublon aussi (identifiant, nom ou fondateur
     * d'opérateur, identifiant ou numéro de carte, demande, antenne) ; les numéros des cartes relues sont
     * toujours tenus pour pris, même si la liste sauvegardée est abîmée.
     *
     * @param dimensions fabrique des clés de dimension : celle du jeu, ou celle d'un essai ({@link
     *        ResourceKey#create(ResourceKey, ResourceLocation)} exige les registres du jeu)
     */
    static PhoneOperators load(CompoundTag tag, Function<ResourceLocation, ResourceKey<Level>> dimensions) {
        PhoneOperators registry = new PhoneOperators();
        for (CompoundTag row : rows(tag, "operators")) {
            PhoneOperator operator = safely(() -> PhoneOperator.load(row));
            // Identifiant, nom (sans casse) et fondateur sont uniques : la ligne qui en répète un déjà vu est ignorée.
            if (operator != null && !registry.operators.containsKey(operator.id())
                    && registry.byName(operator.name()).isEmpty() && registry.byFounder(operator.founder()).isEmpty()) {
                registry.operators.put(operator.id(), operator);
            }
        }
        for (CompoundTag row : rows(tag, "cards")) {
            SimCard card = safely(() -> SimCard.load(row));
            if (card != null && !registry.cards.containsKey(card.simId())
                    && !registry.cardByNumber.containsKey(card.number())) {
                registry.cards.put(card.simId(), card);
                registry.cardByNumber.put(card.number(), card.simId());
            }
        }
        for (Tag entry : tag.getList("usedNumbers", Tag.TAG_STRING)) {
            String number = entry.getAsString();
            if (SimNumbers.parse(number).filter(number::equals).isPresent()) {
                registry.usedNumbers.add(number);
            }
        }
        registry.usedNumbers.addAll(registry.cardByNumber.keySet());
        Set<String> asked = new HashSet<>();
        for (CompoundTag row : rows(tag, "requests")) {
            SimRequest request = safely(() -> SimRequest.load(row));
            if (request != null && !registry.requests.containsKey(request.id())
                    && asked.add(request.operatorId() + "/" + request.player())) {
                registry.requests.put(request.id(), request);
            }
        }
        for (CompoundTag row : rows(tag, "antennas")) {
            AntennaRecord antenna = safely(() -> AntennaRecord.load(row, dimensions));
            if (antenna != null && !registry.antennas.containsKey(antenna.id())) {
                registry.antennas.put(antenna.id(), antenna);
            }
        }
        for (CompoundTag row : rows(tag, "notices")) {
            if (row.hasUUID("player")) {
                List<String> lines = new ArrayList<>();
                for (Tag saved : row.getList("lines", Tag.TAG_STRING)) {
                    lines.add(saved.getAsString());
                }
                if (!lines.isEmpty()) {
                    registry.notices.put(row.getUUID("player"), lines);
                }
            }
        }
        registry.tariffCap = tag.contains("tariffCap", Tag.TAG_ANY_NUMERIC)
                ? Math.max(0L, tag.getLong("tariffCap")) : null;
        registry.nextTariffCap = tag.contains("nextTariffCap", Tag.TAG_ANY_NUMERIC)
                ? Math.max(0L, tag.getLong("nextTariffCap")) : null;
        // Une principale perdue (ou jamais notée) est réélue à la plus ancienne antenne de l'opérateur.
        for (String id : List.copyOf(registry.operators.keySet())) {
            registry.electMain(id);
        }
        return registry;
    }

    private static List<CompoundTag> rows(CompoundTag tag, String key) {
        List<CompoundTag> out = new ArrayList<>();
        for (Tag entry : tag.getList(key, Tag.TAG_COMPOUND)) {
            out.add((CompoundTag) entry);
        }
        return out;
    }

    @Nullable
    private static <T> T safely(java.util.function.Supplier<T> reader) {
        try {
            return reader.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
