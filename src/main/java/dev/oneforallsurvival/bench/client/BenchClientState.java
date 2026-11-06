package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.QueuePacket;
import dev.oneforallsurvival.bench.RecipeListPacket;
import java.util.ArrayList;
import java.util.List;

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

    /** Le catalogue en cours de réception. */
    private static List<BenchRecipe> pending;
    private static int pendingGeneration;
    private static int nextPart;

    private BenchClientState() {}

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
            generation = pendingGeneration;
            pending = null;
        }
    }

    public static void acceptQueue(QueuePacket packet) {
        queue = List.copyOf(packet.rows());
        stockItems = packet.stockItems();
    }

    /** Oublie tout : à la déconnexion, ou entre deux essais. */
    public static void reset() {
        catalog = List.of();
        generation = 0;
        queue = List.of();
        stockItems = 0;
        pending = null;
        nextPart = 0;
    }
}
