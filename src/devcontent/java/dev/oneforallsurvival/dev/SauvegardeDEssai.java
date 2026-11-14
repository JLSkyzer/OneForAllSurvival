package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Ce qu'un arrêt brutal laisse sur le disque, pour les GameTests de l'économie : écrire comme une sauvegarde
 * automatique, puis relire ce qu'un redémarrage relirait (playerdata d'un joueur et la garde d'échange qu'il porte,
 * attentes, registre des factures). Rien n'est relu de la mémoire du serveur.
 */
final class SauvegardeDEssai {

    private SauvegardeDEssai() {}

    /**
     * Écrit ce qu'écrit une sauvegarde automatique, au même instant : le playerdata de tous les joueurs en ligne, puis
     * les données du monde (dont toutes celles de l'économie) qui ont changé. Les attentes sont notées changées pour que
     * leur fichier existe.
     */
    static void automatique(MinecraftServer serveur) {
        HeldGoods.get(serveur).setDirty();
        serveur.getPlayerList().saveAll();
        serveur.overworld().getDataStorage().save();
    }

    /** Le fichier du playerdata de ce joueur. */
    static Path fichierDuJoueur(MinecraftServer serveur, UUID joueur) {
        return serveur.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(joueur + ".dat");
    }

    /** Le fichier d'une donnée du monde : {@code <monde>/data/<clé>.dat}. */
    static Path fichierDeDonnees(MinecraftServer serveur, String cle) {
        return serveur.getWorldPath(LevelResource.ROOT).resolve("data").resolve(cle + ".dat");
    }

    /** Un fichier tel qu'il est sur le disque à un instant : présence, date et octets. */
    record Instantane(boolean existe, long date, byte[] octets) {
        static Instantane de(Path fichier) {
            if (!Files.exists(fichier)) {
                return new Instantane(false, 0L, new byte[0]);
            }
            try {
                return new Instantane(true, Files.getLastModifiedTime(fichier).toMillis(), Files.readAllBytes(fichier));
            } catch (IOException e) {
                throw new AssertionError("fichier illisible : " + fichier + " : " + e.getMessage());
            }
        }

        /** Vrai si le fichier n'a pas été réécrit : même présence, même date, mêmes octets. */
        boolean pareil(Instantane autre) {
            return existe == autre.existe && date == autre.date && Arrays.equals(octets, autre.octets);
        }
    }

    /** Le playerdata de ce joueur relu du disque. */
    static CompoundTag playerdata(MinecraftServer serveur, UUID joueur) {
        Path fichier = fichierDuJoueur(serveur, joueur);
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
     * La garde d'échange que porte ce playerdata, relu du disque (rendue à ce joueur à sa prochaine connexion). Le
     * chemin est écrit en toutes lettres : c'est le format sur le disque que le test vérifie
     * ({@code ForgeData/PlayerPersisted/oneforallsurvival_escrow}).
     */
    static List<ItemStack> gardeDansLePlayerdata(MinecraftServer serveur, UUID joueur) {
        return garde(playerdata(serveur, joueur).getCompound("ForgeData"));
    }

    /** La garde d'échange que porte ce joueur en mémoire (ses données persistantes de Forge). */
    static List<ItemStack> gardeEnMemoire(ServerPlayer joueur) {
        return garde(joueur.getPersistentData());
    }

    private static List<ItemStack> garde(CompoundTag forgeData) {
        List<ItemStack> out = new ArrayList<>();
        for (Tag brut : forgeData.getCompound("PlayerPersisted").getList("oneforallsurvival_escrow", Tag.TAG_COMPOUND)) {
            ItemStack pile = ItemStack.of((CompoundTag) brut);
            if (!pile.isEmpty()) {
                out.add(pile);
            }
        }
        return out;
    }

    /** Les attentes telles qu'un redémarrage les relirait du disque. */
    static HeldGoods attenteApresRedemarrage(MinecraftServer serveur) {
        return HeldGoods.load(donnees(fichierDeDonnees(serveur, HeldGoods.KEY).toFile()));
    }

    /** Le registre des factures tel qu'un redémarrage le relirait du disque. */
    static InvoiceRegistry facturesApresRedemarrage(MinecraftServer serveur) {
        return InvoiceRegistry.load(donnees(fichierDeDonnees(serveur, InvoiceRegistry.KEY).toFile()));
    }

    private static CompoundTag donnees(File fichier) {
        try {
            return NbtIo.readCompressed(fichier).getCompound("data");
        } catch (IOException e) {
            throw new AssertionError("fichier illisible : " + fichier + " : " + e.getMessage());
        }
    }
}
