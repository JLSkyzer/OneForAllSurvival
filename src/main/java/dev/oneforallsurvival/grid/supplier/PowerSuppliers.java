package dev.oneforallsurvival.grid.supplier;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinStore;
import dev.oneforallsurvival.economy.EconomyConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
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

    /** Ajoute ou remplace le client de ce compteur. */
    public void putClient(PowerClient client) {
        clients.put(client.meterSerial(), client);
        setDirty();
    }

    /** Détache ce compteur. @return le client retiré, s'il y en avait un */
    public Optional<PowerClient> removeClient(UUID meterSerial) {
        PowerClient removed = clients.remove(meterSerial);
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Le courant passe-t-il par ce compteur ? Seulement s'il est jumelé à un client accepté (actif ou en résiliation)
     * d'un fournisseur qui existe. Deux lectures de table, sans parcours.
     */
    public boolean passes(UUID meterSerial) {
        PowerClient client = clients.get(meterSerial);
        return client != null && client.state().passes() && suppliers.containsKey(client.supplierId());
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
        registry.priceCap = tag.contains("priceCap") ? Math.max(0L, tag.getLong("priceCap")) : null;
        registry.nextPriceCap = tag.contains("nextPriceCap") ? Math.max(0L, tag.getLong("nextPriceCap")) : null;
        registry.taxPercent = Math.max(0, tag.getInt("taxPercent"));
        registry.nextTaxPercent = tag.contains("nextTaxPercent") ? Math.max(0, tag.getInt("nextTaxPercent")) : null;
        return registry;
    }
}
