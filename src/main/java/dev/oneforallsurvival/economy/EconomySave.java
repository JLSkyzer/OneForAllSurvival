package dev.oneforallsurvival.economy;

import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * L'écriture cohérente de l'économie : partout où le mod écrit sur le disque hors de la sauvegarde automatique du
 * monde (aujourd'hui : à chaque déconnexion), il écrit <b>ensemble</b> le playerdata de tous les joueurs en ligne et
 * toutes les données de l'économie qui peuvent avoir bougé avec eux. Après un arrêt brutal, le disque montre alors un
 * seul instant : une pièce posée dans une garde n'est pas aussi dans le vieux playerdata de son propriétaire, une
 * facture payée n'est pas encore due quand son émetteur a déjà reçu les pièces.
 *
 * <p>Les étals vivent dans les chunks, comme les coffres de Minecraft : leur écriture n'est pas forcée ici (même
 * limite que le jeu pour tout conteneur).
 */
public final class EconomySave {

    private EconomySave() {}

    /**
     * Écrit, dans cet ordre : le playerdata de tous les joueurs en ligne (celui qui part compris, s'il est encore dans
     * la liste), puis chaque donnée de l'économie qui a changé depuis sa dernière écriture ({@link HeldGoods},
     * {@link InvoiceRegistry}, {@link EconomyLedger}, {@link SafeAccounts}, {@link TreasuryAccount}). Même ordre que la
     * sauvegarde automatique du jeu (joueurs, puis données du monde). Sur le fil du serveur seulement.
     */
    public static void playersThenEconomy(MinecraftServer server) {
        server.getPlayerList().saveAll();
        write(server, HeldGoods.get(server), HeldGoods.KEY);
        write(server, InvoiceRegistry.get(server), InvoiceRegistry.KEY);
        write(server, EconomyLedger.get(server), EconomyLedger.KEY);
        write(server, SafeAccounts.get(server), SafeAccounts.KEY);
        write(server, TreasuryAccount.get(server), TreasuryAccount.KEY);
    }

    /** Le fichier d'une donnée du monde : {@code <monde>/data/<clé>.dat} (stockage de l'Overworld). */
    public static Path file(MinecraftServer server, String key) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(key + ".dat");
    }

    /** Écrit cette donnée si elle a changé ({@link SavedData#save(java.io.File)} ne fait rien sinon). */
    private static void write(MinecraftServer server, SavedData data, String key) {
        if (data.isDirty()) {
            data.save(file(server, key).toFile());
        }
    }
}
