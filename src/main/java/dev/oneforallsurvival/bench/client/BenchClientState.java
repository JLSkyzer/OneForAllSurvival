package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.QueuePacket;
import dev.oneforallsurvival.bench.RecipeListPacket;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Ce que le client sait de l'établi : sa propre copie du catalogue, reçue par paquets, et la dernière file
 * reçue. Ne touche à aucune classe client de Minecraft : le serveur peut la charger. En solo, client et
 * serveur partagent la JVM et {@code BenchCatalog} ; ce catalogue-ci n'en est jamais une référence, mais
 * toujours la copie décodée d'un paquet, comme en multijoueur.
 */
public final class BenchClientState {

    private static volatile List<BenchRecipe> catalog = List.of();
    private static volatile int generation;
    private static volatile List<QueuePacket.Row> queue = List.of();
    private static volatile int stockItems;
    /** Réglages du serveur reçus avec la file ; 0 tant qu'aucun paquet n'est arrivé (inconnu). */
    private static volatile double manualSpeed;
    private static volatile double poweredSpeed;
    private static volatile int maxQueue;

    /** Le catalogue en cours de réception. */
    private static List<BenchRecipe> pending;
    private static int pendingGeneration;
    private static int nextPart;

    /** Dernier refus de commande reçu du serveur, avec l'instant de sa réception (nanosecondes). */
    private static volatile String lastRefusal = "";
    private static volatile long lastRefusalNs;
    /** Instant (nanosecondes) où la dernière file est arrivée : l'écran en tire une progression fluide. */
    private static volatile long queueReceivedNs;

    /** Texte de recherche déjà calculé par recette (par identité) ; vidé quand le catalogue change. */
    private static final Map<BenchRecipe, String> SEARCH_TEXT = new IdentityHashMap<>();
    private static final Map<BenchRecipe, String> MOD_OF = new IdentityHashMap<>();
    private static final Map<BenchRecipe, String> NAME_OF = new IdentityHashMap<>();
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern BLANKS = Pattern.compile("\\s+");

    /**
     * Ce que la recherche et les onglets lisent d'une recette : nom affiché et mod. Le client réel en
     * installe un qui traduit les noms ; sans lui, l'identifiant en tient lieu, ce qui suffit aux essais.
     */
    public interface Describer {
        /** Nom affiché de la recette (celui de son résultat). */
        String name(BenchRecipe recipe);

        /** Identifiant du mod auquel la recette est rangée. */
        String mod(BenchRecipe recipe);
    }

    private static final Describer BY_ID = new Describer() {
        @Override
        public String name(BenchRecipe recipe) {
            return recipe.id().getPath();
        }

        @Override
        public String mod(BenchRecipe recipe) {
            return recipe.id().getNamespace();
        }
    };

    private static volatile Describer describer = BY_ID;

    /** Appelés (sur le thread qui reçoit le paquet) chaque fois qu'un catalogue complet est installé. */
    private static final List<Runnable> CATALOG_LISTENERS = new CopyOnWriteArrayList<>();

    private BenchClientState() {}

    /** Installe le descripteur ; {@code null} revient à celui par identifiant. Vide les textes calculés. */
    public static void useDescriber(Describer newDescriber) {
        describer = newDescriber == null ? BY_ID : newDescriber;
        clearSearchCache();
    }

    /** Abonne {@code listener} aux nouveaux catalogues (JEI s'en sert pour rafraîchir ses recettes). */
    public static void addCatalogListener(Runnable listener) {
        CATALOG_LISTENERS.add(listener);
    }

    public static void removeCatalogListener(Runnable listener) {
        CATALOG_LISTENERS.remove(listener);
    }

    public static Describer describer() {
        return describer;
    }

    private static void clearSearchCache() {
        synchronized (SEARCH_TEXT) {
            SEARCH_TEXT.clear();
            MOD_OF.clear();
            NAME_OF.clear();
        }
    }

    /** Minuscules, sans accents, espaces réduits : la forme sous laquelle on compare. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String bare = MARKS.matcher(decomposed).replaceAll("");
        return BLANKS.matcher(bare.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** Nom affiché et identifiant, normalisés ; calculé une fois par recette et par catalogue. */
    private static String searchText(BenchRecipe recipe) {
        synchronized (SEARCH_TEXT) {
            String known = SEARCH_TEXT.get(recipe);
            if (known == null) {
                known = normalize(displayName(recipe) + " " + recipe.id());
                SEARCH_TEXT.put(recipe, known);
            }
            return known;
        }
    }

    /** Nom affiché de la recette, demandé une seule fois au descripteur par catalogue. */
    public static String displayName(BenchRecipe recipe) {
        synchronized (SEARCH_TEXT) {
            String known = NAME_OF.get(recipe);
            if (known == null) {
                known = cleanName(describer.name(recipe));
                NAME_OF.put(recipe, known);
            }
            return known;
        }
    }

    /** Nom affiché d'une pile, sans marqueur de traduction manquante. */
    public static String nameOf(net.minecraft.world.item.ItemStack stack) {
        return cleanName(stack.getHoverName().getString());
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\s*\\[NEEDS_TRANSLATION\\]\\s*");

    /**
     * Retire le marqueur « [NEEDS_TRANSLATION] » que certains mods laissent devant un nom non traduit : il
     * ferait trier ces objets en tête de liste et mange la place du vrai nom.
     */
    public static String cleanName(String name) {
        return name == null ? "" : PLACEHOLDER.matcher(name).replaceFirst("");
    }

    /** Oublie les noms et textes calculés : à la réouverture de l'écran, la langue a pu changer. */
    public static void invalidateNames() {
        clearSearchCache();
    }

    /** Mod de la recette, mémorisé comme son texte de recherche. */
    public static String modOf(BenchRecipe recipe) {
        synchronized (SEARCH_TEXT) {
            String known = MOD_OF.get(recipe);
            if (known == null) {
                known = describer.mod(recipe);
                MOD_OF.put(recipe, known);
            }
            return known;
        }
    }

    /**
     * Les recettes de {@code all} qui sont du mod {@code category} (vide ou null : tous) et dont le nom
     * affiché ou l'identifiant contient tous les mots de {@code query}, sans égard à la casse ni aux
     * accents. L'ordre de {@code all} est conservé. Pure : ne lit que le descripteur installé.
     */
    public static List<BenchRecipe> filter(List<BenchRecipe> all, String category, String query) {
        String mod = category == null ? "" : category.trim();
        String[] words = normalize(query).isEmpty() ? new String[0] : normalize(query).split(" ");
        if (mod.isEmpty() && words.length == 0) {
            return all;
        }
        List<BenchRecipe> out = new ArrayList<>();
        for (BenchRecipe recipe : all) {
            if (!mod.isEmpty() && !mod.equals(modOf(recipe))) {
                continue;
            }
            String text = words.length == 0 ? "" : searchText(recipe);
            boolean matches = true;
            for (String word : words) {
                if (!text.contains(word)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                out.add(recipe);
            }
        }
        return out;
    }

    public static String lastRefusal() {
        return lastRefusal;
    }

    public static long lastRefusalNs() {
        return lastRefusalNs;
    }

    /** Mémorise le texte d'un refus du serveur, que l'écran affiche un moment. */
    public static void acceptRefusal(String text) {
        lastRefusal = text;
        lastRefusalNs = System.nanoTime();
    }

    public static long queueReceivedNs() {
        return queueReceivedNs;
    }

    public static List<BenchRecipe> catalog() {
        return catalog;
    }

    /** Génération du catalogue reçu ; 0 tant qu'aucun n'est arrivé. */
    public static int generation() {
        return generation;
    }

    public static List<QueuePacket.Row> queue() {
        return queue;
    }

    /** Vitesse à la main du serveur, ou 0 si inconnue. */
    public static double manualSpeed() {
        return manualSpeed;
    }

    /** Vitesse alimentée du serveur, ou 0 si inconnue. */
    public static double poweredSpeed() {
        return poweredSpeed;
    }

    /** Taille maximale de la file selon le serveur, ou 0 si inconnue : le client ne s'oppose alors à rien. */
    public static int maxQueue() {
        return maxQueue;
    }

    public static int stockItems() {
        return stockItems;
    }

    /**
     * Reçoit un fragment du catalogue. Le catalogue précédent reste en place jusqu'au dernier fragment ;
     * un fragment hors séquence abandonne la réception en cours.
     */
    public static void acceptFragment(RecipeListPacket fragment) {
        if (fragment.part() == 0) {
            pending = new ArrayList<>();
            pendingGeneration = fragment.generation();
            nextPart = 0;
        }
        if (pending == null || fragment.generation() != pendingGeneration || fragment.part() != nextPart) {
            pending = null;
            return;
        }
        pending.addAll(fragment.recipes());
        nextPart++;
        if (nextPart >= fragment.parts()) {
            catalog = List.copyOf(pending);
            clearSearchCache();
            generation = pendingGeneration;
            pending = null;
            for (Runnable listener : CATALOG_LISTENERS) {
                listener.run();
            }
        }
    }

    public static void acceptQueue(QueuePacket packet) {
        queue = List.copyOf(packet.rows());
        stockItems = packet.stockItems();
        manualSpeed = packet.manualSpeed();
        poweredSpeed = packet.poweredSpeed();
        maxQueue = packet.maxQueue();
        queueReceivedNs = System.nanoTime();
    }

    /** Oublie tout : à la déconnexion, ou entre deux essais. */
    public static void reset() {
        catalog = List.of();
        generation = 0;
        queue = List.of();
        stockItems = 0;
        manualSpeed = 0;
        poweredSpeed = 0;
        maxQueue = 0;
        pending = null;
        nextPart = 0;
        lastRefusal = "";
        clearSearchCache();
    }
}
