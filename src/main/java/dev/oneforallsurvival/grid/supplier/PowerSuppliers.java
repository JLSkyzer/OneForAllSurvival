package dev.oneforallsurvival.grid.supplier;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinStore;
import dev.oneforallsurvival.economy.EconomyConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Le registre des fournisseurs d'électricité, gardé par le serveur (données du monde principal, écrites par le jeu avec
 * le reste du monde) : les fournisseurs et leur caisse, les compteurs jumelés et leur consommation du jour, le plafond
 * du prix et la taxe. Rien de tout cela ne vit dans un bloc : la caisse se vide et la consommation se facture même zone
 * déchargée. Ne se touche que depuis le fil du serveur ; chaque changement marque le fichier sale.
 *
 * <p>Un fournisseur par joueur (son fondateur), un nom unique sans casse. Le registre ne crée ni ne détruit aucune
 * pièce : la caisse ne reçoit que ce qu'un prélèvement a pris à un client, et n'en sort que vers son fournisseur.
 */
public final class PowerSuppliers extends SavedData {

    public static final String KEY = "oneforallsurvival_power";

    /** Longueur la plus grande d'un nom de fournisseur, blancs du bord ôtés. */
    public static final int MAX_NAME = 24;

    /** Issue d'une fondation. */
    public enum Status { FOUNDED, BAD_NAME, NAME_TAKEN, ALREADY_HAS_ONE }

    /** Ce qu'a donné {@link #found} : le fournisseur créé, ou nul si refusé. */
    public record Result(Status status, @Nullable PowerSupplier supplier) {

        public boolean founded() {
            return status == Status.FOUNDED;
        }
    }

    private final Map<String, PowerSupplier> suppliers = new LinkedHashMap<>();
    private final Map<UUID, PowerClient> clients = new HashMap<>();
    /** Clients détachés (compteur démonté) dont la consommation du jour reste à facturer à la clôture. */
    private final List<PowerClient> detached = new ArrayList<>();
    /** Codes d'accord à usage unique : code → numéro du compteur en attente de l'accord du joueur. */
    private final Map<String, UUID> consentCodes = new HashMap<>();
    /** Index (non sauvegardé, refait à la lecture) : disjoncteur jumelé → compteur de son client. */
    private final Map<UUID, UUID> breakerIndex = new HashMap<>();
    /** Où sont les compteurs et disjoncteurs chargés (non sauvegardé, tenu par leurs block entities). */
    private final Map<UUID, GlobalPos> loadedMeters = new HashMap<>();
    private final Map<UUID, GlobalPos> loadedBreakers = new HashMap<>();
    /** Où chaque compteur et disjoncteur jumelé a été jumelé (sauvegardé) : une copie posée ailleurs ne compte pas. */
    private final Map<UUID, GlobalPos> pairedAt = new HashMap<>();
    /** Le relevé de chaque client jumelé (sauvegardé), borné à {@link #STATEMENT_LINES} lignes. */
    private final Map<UUID, List<StatementLine>> statements = new HashMap<>();
    @Nullable
    private Long priceCap;
    @Nullable
    private Long nextPriceCap;
    private int taxPercent;
    @Nullable
    private Integer nextTaxPercent;

    /** Un registre neuf, détaché du monde (essais) ; celui du serveur s'obtient par {@link #get}. */
    public PowerSuppliers() {}

    public static PowerSuppliers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PowerSuppliers::load, PowerSuppliers::new, KEY);
    }

    // ---------------------------------------------------------------------------------------------
    // Fournisseurs
    // ---------------------------------------------------------------------------------------------

    /**
     * Le nom prêt à garder, ou vide s'il n'est pas valable : vide ou blanc, plus long que {@link #MAX_NAME} une fois
     * les blancs du bord ôtés, ou porteur d'un caractère de contrôle ou du signe de mise en forme.
     */
    public static Optional<String> validName(@Nullable String name) {
        if (name == null) {
            return Optional.empty();
        }
        String text = name.strip();
        if (text.isEmpty() || text.length() > MAX_NAME
                || text.chars().anyMatch(c -> Character.isISOControl(c) || c == '§')) {
            return Optional.empty();
        }
        return Optional.of(text);
    }

    private static String fold(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Fonde un fournisseur pour {@code founder}, sans poste noté (l'appelant le note par {@link #setStation}). Refusé
     * si le nom n'est pas valable ou déjà pris (sans casse), ou si ce joueur en a déjà un.
     */
    public Result found(UUID founder, String founderName, String name) {
        Optional<String> valid = validName(name);
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
        } while (suppliers.containsKey(id));
        PowerSupplier supplier = new PowerSupplier(id, valid.get(), founder, founderName, 0L, 0L, null, null, null,
                new EnumMap<>(Coin.class), 0L, 0L, 0L);
        suppliers.put(id, supplier);
        setDirty();
        return new Result(Status.FOUNDED, supplier);
    }

    public Optional<PowerSupplier> byId(@Nullable String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(suppliers.get(id));
    }

    public Optional<PowerSupplier> byName(String name) {
        String wanted = fold(name);
        for (PowerSupplier supplier : suppliers.values()) {
            if (fold(supplier.name()).equals(wanted)) {
                return Optional.of(supplier);
            }
        }
        return Optional.empty();
    }

    public Optional<PowerSupplier> byFounder(UUID founder) {
        for (PowerSupplier supplier : suppliers.values()) {
            if (supplier.founder().equals(founder)) {
                return Optional.of(supplier);
            }
        }
        return Optional.empty();
    }

    /** Tous les fournisseurs, dans l'ordre de fondation. */
    public Collection<PowerSupplier> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(suppliers.values()));
    }

    private void replace(PowerSupplier supplier) {
        suppliers.put(supplier.id(), supplier);
        setDirty();
    }

    /**
     * Retire ce fournisseur et détache ses compteurs, seulement si sa caisse est vide : le registre ne détruit jamais
     * de pièce. (La suppression par le maire, qui verse d'abord la caisse au fondateur, s'appuie sur ce geste.)
     *
     * @return faux, et rien ne bouge, si le fournisseur n'existe pas ou si sa caisse n'est pas vide
     */
    public boolean removeIfEmpty(String id) {
        PowerSupplier supplier = suppliers.get(id);
        if (supplier == null || !supplier.till().isEmpty()) {
            return false;
        }
        suppliers.remove(id);
        clients.values().removeIf(client -> client.supplierId().equals(id));
        statements.keySet().removeIf(meter -> !clients.containsKey(meter));
        consentCodes.values().removeIf(meter -> !clients.containsKey(meter));
        reindexBreakers();
        setDirty();
        return true;
    }

    /** Note l'emplacement du poste de ce fournisseur (rien si le fournisseur n'existe pas). */
    public void setStation(String id, GlobalPos pos) {
        PowerSupplier supplier = suppliers.get(id);
        if (supplier != null && !pos.equals(supplier.station())) {
            replace(supplier.withStation(pos));
        }
    }

    /** Efface l'emplacement seulement s'il est bien celui-ci (un autre poste l'a peut-être pris entre-temps). */
    public void clearStation(String id, GlobalPos pos) {
        PowerSupplier supplier = suppliers.get(id);
        if (supplier != null && pos.equals(supplier.station())) {
            replace(supplier.withStation(null));
        }
    }

    /** Ajoute ce qui est sorti du poste de ce fournisseur aujourd'hui. */
    public void recordOut(String supplierId, int fe) {
        PowerSupplier supplier = suppliers.get(supplierId);
        if (supplier != null && fe > 0) {
            replace(supplier.withOutToday(saturatedAdd(supplier.outToday(), fe)));
        }
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }

    // ---------------------------------------------------------------------------------------------
    // Clients (compteurs jumelés)
    // ---------------------------------------------------------------------------------------------

    public Optional<PowerClient> clientOf(UUID meterSerial) {
        return Optional.ofNullable(clients.get(meterSerial));
    }

    /** Les clients de ce fournisseur. */
    public List<PowerClient> clients(String supplierId) {
        List<PowerClient> out = new ArrayList<>();
        for (PowerClient client : clients.values()) {
            if (client.supplierId().equals(supplierId)) {
                out.add(client);
            }
        }
        return out;
    }

    /** Ajoute ou remplace le client de ce compteur. Un disjoncteur ne sert qu'un client : il quitte l'ancien. */
    public void putClient(PowerClient client) {
        PowerClient before = clients.put(client.meterSerial(), client);
        if (before != null) {
            before.breakers().forEach(breakerIndex::remove);
        }
        for (UUID breaker : client.breakers()) {
            UUID other = breakerIndex.put(breaker, client.meterSerial());
            PowerClient old = other == null || other.equals(client.meterSerial()) ? null : clients.get(other);
            if (old != null) {
                Set<UUID> rest = new HashSet<>(old.breakers());
                rest.remove(breaker);
                clients.put(other, old.withBreakers(rest));
            }
        }
        setDirty();
    }

    /** Retire ce compteur (et son code d'accord), sans rien garder. @return le client retiré, s'il y en avait un */
    public Optional<PowerClient> removeClient(UUID meterSerial) {
        PowerClient removed = clients.remove(meterSerial);
        if (removed != null) {
            removed.breakers().forEach(breakerIndex::remove);
            removed.breakers().forEach(pairedAt::remove);
            pairedAt.remove(meterSerial);
            statements.remove(meterSerial);
            consentCodes.values().removeIf(meterSerial::equals);
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Détache ce compteur (démonté) : il ne laisse plus rien passer et son client quitte la liste. La consommation du
     * jour (et l'abonnement du jour) est gardée à part ({@link #detached()}) pour la clôture de minuit.
     *
     * @return le client détaché, s'il y en avait un
     */
    public Optional<PowerClient> detach(UUID meterSerial) {
        Optional<PowerClient> removed = removeClient(meterSerial);
        // Gardé même sans consommation : l'abonnement du jour reste dû à la clôture (tâche 4).
        if (removed.isPresent()) {
            detached.add(removed.get().withBreakers(Set.of()));
            setDirty();
        }
        return removed;
    }

    /** Les clients détachés dont la consommation du jour attend la clôture (copie). */
    public List<PowerClient> detached() {
        return List.copyOf(detached);
    }

    /** Oublie un client détaché, une fois sa consommation facturée par la clôture. */
    public boolean removeDetached(PowerClient client) {
        boolean removed = detached.remove(client);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Le client dont ce disjoncteur est jumelé, s'il l'est. Une lecture de table. */
    public Optional<PowerClient> clientOfBreaker(UUID breakerSerial) {
        UUID meter = breakerIndex.get(breakerSerial);
        return meter == null ? Optional.empty() : Optional.ofNullable(clients.get(meter));
    }

    private void reindexBreakers() {
        breakerIndex.clear();
        for (PowerClient client : clients.values()) {
            for (UUID breaker : client.breakers()) {
                breakerIndex.put(breaker, client.meterSerial());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Codes d'accord (usage unique)
    // ---------------------------------------------------------------------------------------------

    /** Un code neuf (8 lettres et chiffres) pour l'accord du joueur de ce compteur ; l'ancien code du compteur tombe. */
    public String newConsentCode(UUID meterSerial, RandomSource random) {
        consentCodes.values().removeIf(meterSerial::equals);
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        String code;
        do {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                b.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            code = b.toString();
        } while (consentCodes.containsKey(code));
        consentCodes.put(code, meterSerial);
        setDirty();
        return code;
    }

    /** Le compteur que vise ce code, s'il est encore valable. */
    public Optional<UUID> meterOfCode(String code) {
        return Optional.ofNullable(consentCodes.get(code.toLowerCase(Locale.ROOT)));
    }

    /** Le code en cours pour ce compteur, s'il y en a un. */
    public Optional<String> codeOf(UUID meterSerial) {
        for (Map.Entry<String, UUID> e : consentCodes.entrySet()) {
            if (e.getValue().equals(meterSerial)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    /** Consomme ce code. */
    public void dropCode(String code) {
        if (consentCodes.remove(code.toLowerCase(Locale.ROOT)) != null) {
            setDirty();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Blocs chargés (non sauvegardé, tenu par les block entities)
    // ---------------------------------------------------------------------------------------------

    /** Note où ce compteur ou ce disjoncteur a été jumelé. */
    public void setPairedAt(UUID serial, GlobalPos pos) {
        pairedAt.put(serial, pos);
        setDirty();
    }

    /** Retire la position de jumelage d'un disjoncteur détaché. */
    public void clearPairedAt(UUID serial) {
        if (pairedAt.remove(serial) != null) {
            setDirty();
        }
    }

    /** Ce numéro a-t-il été jumelé à cet endroit ? */
    public boolean isPairedAt(UUID serial, GlobalPos pos) {
        return pos.equals(pairedAt.get(serial));
    }

    public void noteMeterLoaded(UUID serial, GlobalPos pos) {
        loadedMeters.put(serial, pos);
    }

    public void forgetMeterLoaded(UUID serial, GlobalPos pos) {
        loadedMeters.remove(serial, pos);
    }

    public Optional<GlobalPos> loadedMeter(UUID serial) {
        return Optional.ofNullable(loadedMeters.get(serial));
    }

    public void noteBreakerLoaded(UUID serial, GlobalPos pos) {
        loadedBreakers.put(serial, pos);
    }

    public void forgetBreakerLoaded(UUID serial, GlobalPos pos) {
        loadedBreakers.remove(serial, pos);
    }

    public Optional<GlobalPos> loadedBreaker(UUID serial) {
        return Optional.ofNullable(loadedBreakers.get(serial));
    }

    /**
     * Le courant passe-t-il par ce compteur ? Seulement s'il est jumelé à un client accepté (actif ou en résiliation),
     * non coupé à distance (la coupure tient même si un câble contourne le disjoncteur), d'un fournisseur qui existe.
     * Deux lectures de table, sans parcours.
     */
    public boolean passes(UUID meterSerial) {
        PowerClient client = clients.get(meterSerial);
        return client != null && client.state().passes() && !client.cut() && suppliers.containsKey(client.supplierId());
    }

    /** Ajoute ce qui est passé par ce compteur à la consommation du jour de son client (rien s'il n'est pas jumelé). */
    public void recordMeter(UUID meterSerial, int fe) {
        PowerClient client = clients.get(meterSerial);
        if (client != null && fe > 0) {
            clients.put(meterSerial, client.withFeToday(saturatedAdd(client.feToday(), fe)));
            setDirty();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Caisse
    // ---------------------------------------------------------------------------------------------

    /** La caisse de ce fournisseur, coupures nulles omises ; vide s'il n'existe pas. */
    public EnumMap<Coin, Long> till(String id) {
        PowerSupplier supplier = suppliers.get(id);
        return supplier == null ? new EnumMap<>(Coin.class) : supplier.till();
    }

    /** Combien de pièces de cette coupure la caisse peut encore recevoir (0 pour un fournisseur inconnu). */
    public long tillRoom(String id, Coin coin) {
        PowerSupplier supplier = suppliers.get(id);
        return supplier == null ? 0L : Math.max(0L, CoinStore.MAX_PER_COIN - supplier.tillCount(coin));
    }

    /**
     * Met des pièces dans la caisse. L'appelant a vérifié {@link #tillRoom} et les a prises ailleurs : la caisse ne
     * crée rien.
     *
     * @throws IllegalArgumentException si le fournisseur n'existe pas, si le nombre est négatif ou dépasse la place
     *         (rien n'est alors écrit)
     */
    public void deposit(String id, Coin coin, long count) {
        PowerSupplier supplier = suppliers.get(id);
        if (supplier == null || count < 0L || count > tillRoom(id, coin)) {
            throw new IllegalArgumentException("caisse " + id + " : depot de " + count + " " + coin);
        }
        if (count > 0L) {
            EnumMap<Coin, Long> till = supplier.till();
            till.merge(coin, count, Long::sum);
            replace(supplier.withTill(till));
        }
    }

    /** Retire exactement {@code count} pièces de la caisse, ou rien. @return faux si elle en a moins */
    public boolean withdraw(String id, Coin coin, long count) {
        PowerSupplier supplier = suppliers.get(id);
        if (supplier == null || count < 0L || supplier.tillCount(coin) < count) {
            return false;
        }
        if (count > 0L) {
            EnumMap<Coin, Long> till = supplier.till();
            till.put(coin, supplier.tillCount(coin) - count);
            replace(supplier.withTill(till));
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Tarifs du fournisseur (au jour suivant) et clôture du jour
    // ---------------------------------------------------------------------------------------------

    /**
     * Prix du lendemain de ce fournisseur, en cuivres par tranche de {@code powerPriceUnitFe} FE (négatif : 0). Il
     * s'applique à la clôture de minuit, ramené au plafond en vigueur ce jour-là ; la facture qui clôt le jour du
     * changement garde l'ancien prix. @return faux si le fournisseur n'existe pas
     */
    public boolean setNextPrice(String id, long price) {
        PowerSupplier supplier = suppliers.get(id);
        if (supplier == null) {
            return false;
        }
        replace(supplier.withNextTariff(Math.max(0L, price), supplier.nextSubscription()));
        return true;
    }

    /**
     * Abonnement du lendemain de ce fournisseur, en cuivres par jour, borné à 0..{@code powerMaxSubscription}. Il
     * s'applique à la clôture de minuit. @return faux si le fournisseur n'existe pas
     */
    public boolean setNextSubscription(String id, long subscription) {
        PowerSupplier supplier = suppliers.get(id);
        if (supplier == null) {
            return false;
        }
        long bounded = Math.max(0L, Math.min(subscription, EconomyConfig.powerMaxSubscription()));
        replace(supplier.withNextTariff(supplier.nextPrice(), bounded));
        return true;
    }

    /** Clôt le jour de ce client : sa consommation passe à « hier » et repart de zéro. */
    public void closeClientDay(UUID meterSerial) {
        PowerClient client = clients.get(meterSerial);
        if (client != null) {
            clients.put(meterSerial, client.withDayClosed());
            setDirty();
        }
    }

    /** Le plafond et la taxe du lendemain, s'ils ont été changés, entrent en vigueur (clôture de minuit). */
    public void applyNextCapAndTax() {
        if (nextPriceCap != null) {
            priceCap = nextPriceCap;
            nextPriceCap = null;
            setDirty();
        }
        if (nextTaxPercent != null) {
            taxPercent = nextTaxPercent;
            nextTaxPercent = null;
            setDirty();
        }
    }

    /**
     * Ce qu'a donné la clôture du jour d'un fournisseur : ses tarifs avant et après, et si le prix a été ramené au
     * plafond.
     */
    public record DayClosed(long priceBefore, long subscriptionBefore, long price, long subscription, boolean capped) {

        /** Les tarifs ont-ils changé ? */
        public boolean changed() {
            return price != priceBefore || subscription != subscriptionBefore;
        }
    }

    /**
     * Clôt le jour de ce fournisseur, après sa facturation : les tarifs du lendemain entrent en vigueur, le prix est
     * ramené au plafond en vigueur ({@link #priceCap()}, à appeler après {@link #applyNextCapAndTax}), la sortie du
     * poste et la somme des compteurs du jour passent à « hier ».
     *
     * @param meters la somme des compteurs de ce fournisseur aujourd'hui (détachés compris)
     * @return vide si le fournisseur n'existe pas
     */
    public Optional<DayClosed> closeSupplierDay(String id, long meters) {
        PowerSupplier supplier = suppliers.get(id);
        if (supplier == null) {
            return Optional.empty();
        }
        long price = supplier.nextPrice() != null ? supplier.nextPrice() : supplier.price();
        long subscription = supplier.nextSubscription() != null ? supplier.nextSubscription() : supplier.subscription();
        long cap = priceCap();
        boolean capped = price > cap;
        if (capped) {
            price = cap;
        }
        replace(supplier.withDayClosed(price, subscription, Math.max(0L, meters)));
        return Optional.of(new DayClosed(supplier.price(), supplier.subscription(), price, subscription, capped));
    }

    // ---------------------------------------------------------------------------------------------
    // Relevé des clients (factures émises, payées, refusées)
    // ---------------------------------------------------------------------------------------------

    /** Lignes gardées au relevé de chaque client ; les plus anciennes tombent. */
    public static final int STATEMENT_LINES = 16;

    /** Ce qu'une ligne du relevé dit de la facture. */
    public enum StatementKind { BILLED, PAID, REFUSED }

    /**
     * Une ligne du relevé d'un client.
     *
     * @param invoice le numéro court de la facture
     */
    public record StatementLine(long epochMillis, StatementKind kind, long amount, String invoice) {

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("t", epochMillis);
            tag.putString("kind", kind.name());
            tag.putLong("sum", amount);
            tag.putString("invoice", invoice);
            return tag;
        }

        @Nullable
        static StatementLine load(CompoundTag tag) {
            try {
                return new StatementLine(tag.getLong("t"), StatementKind.valueOf(tag.getString("kind")),
                        Math.max(0L, tag.getLong("sum")), tag.getString("invoice"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Ajoute une ligne au relevé de ce compteur, seulement s'il est encore jumelé au même contrat (même fournisseur,
     * même joueur) : le paiement tardif d'un contrat passé ne s'inscrit pas au relevé d'un nouveau contrat du même
     * compteur.
     */
    public void noteStatement(UUID meterSerial, String supplierId, UUID player, StatementLine line) {
        PowerClient client = clients.get(meterSerial);
        if (client == null || !client.supplierId().equals(supplierId) || !client.player().equals(player)) {
            return;
        }
        List<StatementLine> lines = statements.computeIfAbsent(meterSerial, k -> new ArrayList<>());
        lines.add(line);
        while (lines.size() > STATEMENT_LINES) {
            lines.remove(0);
        }
        setDirty();
    }

    /** Le relevé de ce compteur, de la plus ancienne ligne à la plus récente (copie). */
    public List<StatementLine> statement(UUID meterSerial) {
        return List.copyOf(statements.getOrDefault(meterSerial, List.of()));
    }

    // ---------------------------------------------------------------------------------------------
    // Plafond du prix et taxe (réglés par le maire, au jour suivant)
    // ---------------------------------------------------------------------------------------------

    /** Plafond du prix en vigueur, en cuivres par tranche ; celui de la configuration tant que le maire n'a rien fixé. */
    public long priceCap() {
        return priceCap == null ? EconomyConfig.powerDefaultPriceCap() : priceCap;
    }

    /** Plafond du lendemain, s'il a été changé aujourd'hui. */
    public Optional<Long> nextPriceCap() {
        return Optional.ofNullable(nextPriceCap);
    }

    public void setNextPriceCap(long cap) {
        nextPriceCap = Math.max(0L, cap);
        setDirty();
    }

    /** Taxe sur l'électricité en vigueur, en pour cent. */
    public int taxPercent() {
        return taxPercent;
    }

    /** Taxe du lendemain, si elle a été changée aujourd'hui. */
    public Optional<Integer> nextTaxPercent() {
        return Optional.ofNullable(nextTaxPercent);
    }

    public void setNextTaxPercent(int percent) {
        nextTaxPercent = Math.max(0, Math.min(percent, EconomyConfig.powerTaxMaxPercent()));
        setDirty();
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (PowerSupplier supplier : suppliers.values()) {
            list.add(supplier.save());
        }
        tag.put("suppliers", list);
        ListTag clientList = new ListTag();
        for (PowerClient client : clients.values()) {
            clientList.add(client.save());
        }
        tag.put("clients", clientList);
        ListTag detachedList = new ListTag();
        for (PowerClient client : detached) {
            detachedList.add(client.save());
        }
        tag.put("detached", detachedList);
        CompoundTag codes = new CompoundTag();
        consentCodes.forEach(codes::putUUID);
        tag.put("consentCodes", codes);
        ListTag paired = new ListTag();
        pairedAt.forEach((serial, pos) -> GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result().ifPresent(saved -> {
            CompoundTag line = new CompoundTag();
            line.putUUID("serial", serial);
            line.put("pos", saved);
            paired.add(line);
        }));
        tag.put("pairedAt", paired);
        ListTag statementList = new ListTag();
        statements.forEach((meter, lines) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("meter", meter);
            ListTag saved = new ListTag();
            lines.forEach(line -> saved.add(line.save()));
            entry.put("lines", saved);
            statementList.add(entry);
        });
        tag.put("statements", statementList);
        if (priceCap != null) {
            tag.putLong("priceCap", priceCap);
        }
        if (nextPriceCap != null) {
            tag.putLong("nextPriceCap", nextPriceCap);
        }
        tag.putInt("taxPercent", taxPercent);
        if (nextTaxPercent != null) {
            tag.putInt("nextTaxPercent", nextTaxPercent);
        }
        return tag;
    }

    /** Relit le registre ; une ligne abîmée est ignorée, jamais une raison de planter ni de gagner des pièces. */
    public static PowerSuppliers load(CompoundTag tag) {
        PowerSuppliers registry = new PowerSuppliers();
        for (Tag entry : tag.getList("suppliers", Tag.TAG_COMPOUND)) {
            PowerSupplier supplier = PowerSupplier.load((CompoundTag) entry);
            if (supplier != null && !registry.suppliers.containsKey(supplier.id())) {
                registry.suppliers.put(supplier.id(), supplier);
            }
        }
        for (Tag entry : tag.getList("clients", Tag.TAG_COMPOUND)) {
            PowerClient client = PowerClient.load((CompoundTag) entry);
            if (client != null) {
                registry.clients.put(client.meterSerial(), client);
            }
        }
        for (Tag entry : tag.getList("detached", Tag.TAG_COMPOUND)) {
            PowerClient client = PowerClient.load((CompoundTag) entry);
            if (client != null) {
                registry.detached.add(client);
            }
        }
        CompoundTag codes = tag.getCompound("consentCodes");
        for (String code : codes.getAllKeys()) {
            if (codes.hasUUID(code) && registry.clients.containsKey(codes.getUUID(code))) {
                registry.consentCodes.put(code, codes.getUUID(code));
            }
        }
        for (Tag entry : tag.getList("pairedAt", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) entry;
            if (line.hasUUID("serial")) {
                GlobalPos.CODEC.parse(NbtOps.INSTANCE, line.get("pos")).result()
                        .ifPresent(pos -> registry.pairedAt.put(line.getUUID("serial"), pos));
            }
        }
        for (Tag entry : tag.getList("statements", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) entry;
            if (line.hasUUID("meter") && registry.clients.containsKey(line.getUUID("meter"))) {
                List<StatementLine> lines = new ArrayList<>();
                for (Tag saved : line.getList("lines", Tag.TAG_COMPOUND)) {
                    StatementLine read = StatementLine.load((CompoundTag) saved);
                    if (read != null) {
                        lines.add(read);
                    }
                }
                registry.statements.put(line.getUUID("meter"), lines);
            }
        }
        registry.reindexBreakers();
        registry.priceCap = tag.contains("priceCap") ? Math.max(0L, tag.getLong("priceCap")) : null;
        registry.nextPriceCap = tag.contains("nextPriceCap") ? Math.max(0L, tag.getLong("nextPriceCap")) : null;
        registry.taxPercent = Math.max(0, tag.getInt("taxPercent"));
        registry.nextTaxPercent = tag.contains("nextTaxPercent") ? Math.max(0, tag.getInt("nextTaxPercent")) : null;
        return registry;
    }
}
