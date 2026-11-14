package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Ce qu'un arrêt brutal laisse sur le disque, pour les GameTests de l'économie : écrire comme une sauvegarde
 * automatique, puis relire ce qu'un redémarrage relirait (playerdata d'un joueur, garde et attentes, registre des
 * factures). Rien n'est relu de la mémoire du serveur.
 */
final class SauvegardeDEssai {

    private SauvegardeDEssai() {}

    /**
     * Écrit ce qu'écrit une sauvegarde automatique, au même instant : le playerdata de tous les joueurs en ligne, puis
     * les données du monde (dont toutes celles de l'économie) qui ont changé. La garde est notée changée pour que son
     * fichier existe.
     */
    static void automatique(MinecraftServer serveur) {
        HeldGoods.get(serveur).setDirty();
        serveur.getPlayerList().saveAll();
        serveur.overworld().getDataStorage().save();
    }

    /** Le playerdata de ce joueur relu du disque. */
    static CompoundTag playerdata(MinecraftServer serveur, UUID joueur) {
        Path fichier = serveur.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(joueur + ".dat");
        try {
            return NbtIo.readCompressed(fichier.toFile());
        } catch (IOException e) {
            throw new AssertionError("playerdata illisible : " + fichier + " : " + e.getMessage());
        }
    }

    /** Combien d'objets {@code objet} l'inventaire relu du playerdata de ce joueur contient. */
    static int objetsDansLePlayerdata(MinecraftServer serveur, UUID joueur, Item objet) {
        int total = 0;
        for (Tag brut : playerdata(serveur, joueur).getList("Inventory", Tag.TAG_COMPOUND)) {
            ItemStack pile = ItemStack.of((CompoundTag) brut);
            if (pile.is(objet)) {
                total += pile.getCount();
            }
        }
        return total;
    }

    /** La valeur, en cuivres, des pièces et des bourses de l'inventaire relu du playerdata de ce joueur. */
    static long argentDansLePlayerdata(MinecraftServer serveur, UUID joueur) {
        long total = 0L;
        for (Tag brut : playerdata(serveur, joueur).getList("Inventory", Tag.TAG_COMPOUND)) {
            ItemStack pile = ItemStack.of((CompoundTag) brut);
            Optional<Coin> piece = Coin.fromItem(pile.getItem());
            if (piece.isPresent()) {
                total += piece.get().value() * pile.getCount();
            } else if (Purse.isPurse(pile)) {
                total += Purse.total(pile);
            }
        }
        return total;
    }

    /**
     * La garde et les attentes telles qu'un redémarrage les relirait du disque : chaque garde y est déjà devenue
     * l'attente de son propriétaire ({@link HeldGoods#load}).
     */
    static HeldGoods gardeApresRedemarrage(MinecraftServer serveur) {
        return HeldGoods.load(donnees(HeldGoods.file(serveur).toFile()));
    }

    /** Le registre des factures tel qu'un redémarrage le relirait du disque. */
    static InvoiceRegistry facturesApresRedemarrage(MinecraftServer serveur) {
        Path fichier = serveur.getWorldPath(LevelResource.ROOT).resolve("data").resolve(InvoiceRegistry.KEY + ".dat");
        return InvoiceRegistry.load(donnees(fichier.toFile()));
    }

    private static CompoundTag donnees(File fichier) {
        try {
            return NbtIo.readCompressed(fichier).getCompound("data");
        } catch (IOException e) {
            throw new AssertionError("fichier illisible : " + fichier + " : " + e.getMessage());
        }
    }
}
