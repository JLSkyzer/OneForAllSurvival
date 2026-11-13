package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneySink;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.SafeMenu;
import dev.oneforallsurvival.economy.PurseMenu;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Étape « monnaie » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot E1 vu par un vrai client contre
 * un serveur dédié avec le pack complet.
 *
 * <p>Le joueur n'est <b>pas opérateur</b> (console RCON : {@code deop}) pendant toute la séquence. Tout ce qui change le
 * monde passe par la console RCON ; ses gestes sont de vrais clics (table de fabrication, cases des écrans, boutons de
 * l'écran de la bourse et du coffre-fort, pose du coffre-fort). Ce qu'un client ne peut pas faire seul passe par
 * {@link E2eMonnaieServeur} : les tapis de Create, des pièces posées au sol, et un second joueur sans droits (connexion
 * simulée, car un seul vrai client peut tourner à la fois).
 *
 * <p>Deux morceaux ne sont pas dans la liste des pas : {@link #depart} (juste après la connexion : somme de départ, vraie
 * déconnexion et reconnexion du client, avant que les autres lots ne vident l'inventaire) et {@link #relance}.
 *
 * <p>Les montants attendus sont absolus : l'essai exige un registre neuf (le premier contrôle échoue clairement sinon).
 */
final class E2eMonnaie {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-monnaie.txt";
    private static final double DELAI = 25;
    private static final int ECHECS_MAX = 6;
    private static final int Y_DALLE = 150;
    private static final String CREATIVE = "createaddition:creative_energy";
    private static final long DEPART = 500;

    // Attendus du registre à la fin du premier passage (cuivres).
    private static final long CREE_DEPART = 1000; // Dev et Autre
    private static final long CREE_EQUIPE = 500 + 300 + 10000; // change, pièces posées au sol, rubis
    private static final long DETRUIT_EQUIPE = 500 + 500 + 50; // avant les autres lots, reprise avant le coffre-fort, retrait
    private static final long COFFRE_FINAL = 300 + 9950;
    private static final long AUTRE_PORTE = 500;

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai, boolean critique) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static final ArrayDeque<Runnable> CLICS = new ArrayDeque<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;
    private static int echecsDuPas;

    private static Minecraft mc;
    private static BlockPos depart;
    private static BlockPos o;
    private static boolean airePrete;

    // Mémoire d'un pas à l'autre.
    private static long valeurAvant;
    private static int origine;
    private static String reponse = "";
    private static Vec3 posPiece;
    private static long avantBelt;

    // Départ (somme de départ, reconnexion) : 0 attente de l'inventaire, 1 après la reconnexion, 2 fini.
    private static int departEtat;
    private static long debutDepart = System.nanoTime();

    private E2eMonnaie() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape de la monnaie";
    }

    // ================================================================================================
    // Disposition (relative à l'origine O : le coin ouest de la dalle, au niveau du sol)
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static BlockPos table() {
        return p(4, 0, 0);
    }

    private static BlockPos coffreVanilla() {
        return p(10, 0, 0);
    }

    private static BlockPos boiteCreate() {
        return p(14, 0, 0);
    }

    private static BlockPos panier() {
        return p(18, 0, 0);
    }

    private static BlockPos depot() {
        return p(22, 0, 0);
    }

    private static BlockPos source() {
        return p(26, 0, 0);
    }

    private static BlockPos cadre() {
        return p(30, 0, 0);
    }

    private static BlockPos tapis() {
        return p(10, 0, 10);
    }

    private static BlockPos tapisMilieu() {
        return tapis().east();
    }

    private static BlockPos coffreFort() {
        return p(30, 0, 10);
    }

    private static BlockPos secondCoffreFort() {
        return p(34, 0, 10);
    }

    // ================================================================================================
    // Déroulement
    // ================================================================================================

    /** Avance d'un pas ; vrai quand la séquence est finie. */
    static boolean avancer(Minecraft minecraft) {
        mc = minecraft;
        if (index >= PAS.size() && CLICS.isEmpty()) {
            return true;
        }
        if (airePrete && (mc.player.isDeadOrDying() || mc.player.getY() < o.getY() - 3)) {
            E2eClient.fail("le joueur est mort ou tombe dans le vide : vie " + mc.player.getHealth() + ", position " + mc.player.position()
                    + ", pas " + (index < PAS.size() ? PAS.get(index).legende() : "?"));
            return false;
        }
        if (!CLICS.isEmpty()) {
            try {
                CLICS.poll().run();
            } catch (RuntimeException e) {
                CLICS.clear();
                E2eClient.fail("clic impossible : " + e.getMessage());
            }
            return false;
        }
        Pas pas = PAS.get(index);
        if (!actionFaite) {
            LOG.info("[e2e] pas : {}", pas.legende());
            try {
                pas.action().run();
            } catch (RuntimeException e) {
                E2eClient.fail(pas.legende() + " : " + e.getMessage());
                return false;
            }
            actionFaite = true;
            debut = System.nanoTime();
            return false;
        }
        boolean pret;
        try {
            pret = pas.pret().getAsBoolean();
        } catch (RuntimeException e) {
            pret = false;
            if (ecoule() > pas.delai()) {
                LOG.error("[e2e] exception en attendant : {}", pas.legende(), e);
            }
        }
        if (pret) {
            if (pas.succes() != null) {
                String texte;
                try {
                    texte = pas.succes().get();
                } catch (RuntimeException e) {
                    E2eClient.fail(pas.legende() + " : " + e.getMessage());
                    return false;
                }
                E2eClient.pass(texte);
            }
            index++;
            actionFaite = false;
        } else if (ecoule() > pas.delai()) {
            String detail = pas.legende() + " : " + pas.echec().get();
            if (pas.critique()) {
                E2eClient.fail(detail);
            } else {
                E2eClient.failSoft(detail);
                echecsDuPas++;
                index++;
                actionFaite = false;
                if (echecsDuPas >= ECHECS_MAX) {
                    E2eClient.fail("trop d'echecs dans l'etape de la monnaie : arret");
                }
            }
        }
        return false;
    }

    private static double ecoule() {
        return (System.nanoTime() - debut) / 1e9;
    }

    private static void pasC(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, DELAI, true));
    }

    private static void pasCD(double delai, String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec,
            Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, delai, true));
    }

    // ================================================================================================
    // Outils
    // ================================================================================================

    private static String nom() {
        return mc.player.getGameProfile().getName();
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static boolean operateur() {
        return mc.player.hasPermissions(2);
    }

    private static String rcon(String commandeConsole) {
        return E2eReseau.rcon(commandeConsole);
    }

    private static Path fichier() {
        return mc.gameDirectory.toPath().resolve(FICHIER);
    }

    private static void placerJoueur(double x, double z, float yaw, float pitch) {
        rcon("tp " + nom() + " " + x + " " + o.getY() + " " + z + " " + yaw + " " + pitch);
    }

    private static boolean pres(double x, double z) {
        return Math.abs(mc.player.getX() - x) < 1.0 && Math.abs(mc.player.getZ() - z) < 1.0 && Math.abs(mc.player.getY() - o.getY()) < 2.5;
    }

    /** Le joueur devant un bloc : deux blocs au sud, regard vers le nord. */
    private static void devant(BlockPos bloc) {
        placerJoueur(bloc.getX() + 0.5, bloc.getZ() + 2.5, 180, 25);
    }

    private static boolean devantPret(BlockPos bloc) {
        return pres(bloc.getX() + 0.5, bloc.getZ() + 2.5);
    }

    private static String fmt(long cuivres) {
        return CoinMath.format(cuivres).getString();
    }

    private static long valeurLibre() {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s);
        }
        n += CoinMath.valueOf(mc.player.getOffhandItem());
        return n;
    }

    private static long valeurBourses() {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += Purse.total(s);
        }
        return n;
    }

    private static long valeurTotale() {
        return valeurLibre() + valeurBourses();
    }

    private static long pieces(Coin coin) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(coin.item())) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static ItemStack bourse() {
        for (ItemStack s : mc.player.getInventory().items) {
            if (Purse.isPurse(s)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Item objet(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
    }

    /** La case de la barre d'action qui porte l'objet (un échange depuis le sac si besoin) ; -1 s'il n'est pas là. */
    private static int fente(Predicate<ItemStack> voulu) {
        for (int i = 0; i < 9; i++) {
            if (voulu.test(mc.player.getInventory().getItem(i))) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (voulu.test(mc.player.getInventory().getItem(i))) {
                int cible = 8;
                for (int h = 8; h >= 0; h--) {
                    if (mc.player.getInventory().getItem(h).isEmpty()) {
                        cible = h;
                        break;
                    }
                }
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, cible, ClickType.SWAP, mc.player);
                return mc.player.getInventory().getItem(cible).isEmpty() ? -1 : cible;
            }
        }
        return -1;
    }

    private static void tenir(Predicate<ItemStack> voulu) {
        int f = fente(voulu);
        if (f < 0) {
            throw new IllegalStateException("objet absent de l'inventaire");
        }
        mc.player.getInventory().selected = f;
    }

    private static void tenirVide() {
        for (int i = 8; i >= 0; i--) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        // Les étapes des lots précédents peuvent avoir rempli la barre : un clic droit sur un bloc à écran ouvre l'écran quelle que soit la main.
        mc.player.getInventory().selected = 8;
    }

    private static void clic(BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        LOG.info("[e2e] clic droit en {} face {}, main {} : {}", pos, face, mc.player.getMainHandItem(), res);
    }

    private static void clicMenu(int index, int bouton, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, index, bouton, type, mc.player);
    }

    /** La case du menu ouvert qui est dans l'inventaire du joueur et porte l'objet voulu ; -1 sinon. */
    private static int caseJoueur(Predicate<ItemStack> voulu) {
        for (Slot s : mc.player.containerMenu.slots) {
            if (s.container == mc.player.getInventory() && voulu.test(s.getItem())) {
                return s.index;
            }
        }
        return -1;
    }

    /** Un seul exemplaire de l'objet dans chacune de ces cases du menu : prendre la pile, un clic droit par case, rendre le reste. */
    private static void poserUnParCase(Item objet, int... cases) {
        CLICS.add(() -> {
            origine = caseJoueur(s -> s.is(objet));
            if (origine < 0) {
                throw new IllegalStateException("pas de " + objet + " dans l'inventaire");
            }
            clicMenu(origine, 0, ClickType.PICKUP);
        });
        for (int c : cases) {
            CLICS.add(() -> clicMenu(c, 1, ClickType.PICKUP));
        }
        CLICS.add(() -> clicMenu(origine, 0, ClickType.PICKUP));
    }

    /** Attend (au plus 2 s) que le serveur ait rempli la case de sortie de la table, puis échoue si elle reste vide. */
    private static Runnable attendreSortie(int essais) {
        return () -> {
            if (!mc.player.containerMenu.getSlot(0).getItem().isEmpty()) {
                return;
            }
            if (essais <= 0) {
                throw new IllegalStateException("aucun resultat en sortie de la table : la recette n'est pas reconnue");
            }
            CLICS.addFirst(attendreSortie(essais - 1));
        };
    }

    /** Le bouton de la souris qui retire, sur une ligne de l'écran du coffre-fort ou de la bourse. */
    private static final int RETIRER = 1;

    /** Un vrai clic sur un bouton de l'écran du coffre-fort ou de la bourse, au centre de l'élément nommé. */
    private static void clicElement(String id, int bouton) {
        CoinAccountScreen<?> s = (CoinAccountScreen<?>) mc.screen;
        int[] c = s.elementCenter(id).orElseThrow(() -> new IllegalStateException("aucun element " + id));
        boolean pris = s.mouseClicked(c[0], c[1], bouton);
        LOG.info("[e2e] clic {} sur {} de {} en ({}, {}) : {}", bouton == 0 ? "gauche" : "droit", id, s.getClass().getSimpleName(),
                c[0], c[1], pris);
    }

    private static void boutonDeposer() {
        clicElement("deposit_all", 0);
    }

    private static void boutonRegrouper() {
        clicElement("group", 0);
    }

    /** Retirer : {@code montantIndex} dans 1, 10, 64. */
    private static void boutonRetirer(Coin coin, int montantIndex) {
        clicElement("row." + coin.key() + "." + CoinRequest.amounts()[montantIndex], RETIRER);
    }

    private static PurseMenu menuBourse() {
        return (PurseMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu();
    }

    private static SafeMenu menuCoffre() {
        return (SafeMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu();
    }

    private static boolean ecranOuvert(Class<?> menu) {
        return mc.screen instanceof AbstractContainerScreen<?> s && menu.isInstance(s.getMenu());
    }

    private static void fermer() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        mc.setScreen(null);
    }

    // ================================================================================================
    // Premier passage
    // ================================================================================================

    static void premierePhase() {
        preparation();
        changeALaTable();
        rangementsInterdits();
        gestesParasites();
        tapisDeCreate();
        etapeBourse();
        etapeCoffreFort();
        secondJoueur();
        equipe();
        fin();
    }

    private static void preparation() {
        pasC("Le joueur, en spectateur, se rend au centre de l'aire de la monnaie (les chunks se chargent)", () -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            depart = mc.player.blockPosition();
            BlockPos spawn = mc.level.getSharedSpawnPos();
            int x = depart.getX() + 100;
            int z = depart.getZ() + 90;
            if (Math.abs(x - spawn.getX()) <= 40 && Math.abs(z - spawn.getZ()) <= 40) {
                x = depart.getX() - 180;
            }
            o = new BlockPos(x, Y_DALLE, z);
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 20.5) + " " + (o.getY() + 5) + " " + (o.getZ() + 6.5));
        }, () -> ecoule() >= 4 && mc.level.getChunkSource().hasChunk((o.getX() + 20) >> 4, (o.getZ() + 6) >> 4)
                && mc.level.getChunkSource().hasChunk((o.getX() + 40) >> 4, (o.getZ() + 20) >> 4)
                && mc.level.getChunkSource().hasChunk((o.getX() - 3) >> 4, (o.getZ() - 6) >> 4),
                () -> "chunks de l'aire non recus, joueur en " + mc.player.blockPosition(), null);
        pasC("Aire de la monnaie sur une dalle, joueur sans droits (console RCON : deop)", () -> {
            rcon("difficulty peaceful");
            rcon("gamerule doMobSpawning false");
            rcon("fill " + xyz(p(-4, 0, -8)) + " " + xyz(p(44, 8, 22)) + " air");
            rcon("fill " + xyz(p(-4, -1, -8)) + " " + xyz(p(44, -1, 22)) + " stone");
            rcon("effect clear " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 2.5) + " " + o.getY() + " " + (o.getZ() + 2.5) + " 180 0");
            rcon("gamemode survival " + nom());
            rcon("deop " + nom());
        }, () -> mc.level.getBlockState(o.below()).is(Blocks.STONE) && !operateur() && mc.player.getHealth() > 0
                && pres(o.getX() + 2.5, o.getZ() + 2.5) && ecoule() >= 2,
                () -> "operateur " + operateur() + ", joueur en " + mc.player.blockPosition() + ", dalle " + mc.level.getBlockState(o.below()),
                () -> {
                    airePrete = true;
                    return "dalle en " + o.toShortString() + " (joueur parti de " + depart.toShortString() + "), deop par RCON : hasPermissions(2) cote client = "
                            + operateur() + ", gamemode " + mc.gameMode.getPlayerMode();
                });
    }

    // ---------------------------------------------------------------------------------------------- change

    private static void changeALaTable() {
        pasC("L'équipe crée 5 or (500 cuivres) : /eco give, par la console", () -> {
            rcon("setblock " + xyz(table()) + " minecraft:crafting_table");
            reponse = rcon("eco give " + nom() + " 500 change a la table");
        }, () -> pieces(Coin.GOLD) == 5 && valeurTotale() == 500 && mc.level.getBlockState(table()).is(Blocks.CRAFTING_TABLE) && ecoule() >= 1.5,
                () -> "or " + pieces(Coin.GOLD) + ", valeur " + valeurTotale() + ", reponse " + reponse,
                () -> "/eco give : " + reponse.trim() + " ; le client porte " + pieces(Coin.GOLD) + " pieces d'or");
        pasC("Il ouvre la table de fabrication (clic droit)", () -> {
            devant(table());
            valeurAvant = valeurTotale();
        }, () -> devantPret(table()) && ecoule() >= 1.5, () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Clic droit sur la table", () -> {
            tenirVide();
            clic(table(), Direction.NORTH);
        }, () -> mc.screen instanceof CraftingScreen, () -> "ecran " + mc.screen, () -> "table de fabrication ouverte par un clic droit reel");
        pasC("Il pose les 5 or dans la grille (une case) puis déplace le résultat d'un clic majuscule", () -> {
            CLICS.add(() -> {
                origine = caseJoueur(s -> s.is(Coin.GOLD.item()));
                clicMenu(origine, 0, ClickType.PICKUP);
            });
            CLICS.add(() -> clicMenu(1, 0, ClickType.PICKUP));
            CLICS.add(attendreSortie(40));
            CLICS.add(() -> clicMenu(0, 0, ClickType.QUICK_MOVE));
        }, () -> pieces(Coin.SILVER) == 50 && pieces(Coin.GOLD) == 0 && ecoule() >= 1.0,
                () -> "argent " + pieces(Coin.SILVER) + ", or " + pieces(Coin.GOLD) + ", valeur " + valeurTotale(),
                () -> "change a la table : 5 or -> " + pieces(Coin.SILVER) + " argent (valeur " + valeurTotale() + " cuivres, avant " + valeurAvant
                        + ") ; le serveur a autorise la sortie de pieces de la table");
        pasC("Il ferme la table", () -> fermer(), () -> mc.screen == null && ecoule() >= 1.0,
                () -> "ecran " + mc.screen, () -> {
                    if (valeurTotale() != 500) {
                        throw new IllegalStateException("valeur " + valeurTotale() + " apres le change");
                    }
                    return "la grille rend ses ingredients : rien ne reste, valeur totale " + valeurTotale();
                });
    }

    // ----------------------------------------------------------------------------------------- rangements interdits

    private static void rangementsInterdits() {
        pasC("L'équipe pose un coffre, une boîte à outils de Create et une armoire de Farmer's Delight", () -> {
            rcon("setblock " + xyz(coffreVanilla()) + " minecraft:chest");
            rcon("setblock " + xyz(boiteCreate()) + " create:white_toolbox");
            rcon("setblock " + xyz(panier()) + " farmersdelight:oak_cabinet");
        }, () -> ecoule() >= 1.5 && mc.level.getBlockState(coffreVanilla()).is(Blocks.CHEST)
                && mc.level.getBlockState(boiteCreate()).getBlock() == BuiltInRegistries.BLOCK.get(new ResourceLocation("create:white_toolbox"))
                && mc.level.getBlockState(panier()).getBlock() == BuiltInRegistries.BLOCK.get(new ResourceLocation("farmersdelight:oak_cabinet")),
                () -> mc.level.getBlockState(coffreVanilla()) + " " + mc.level.getBlockState(boiteCreate()) + " " + mc.level.getBlockState(panier()),
                () -> "coffre, boite a outils de Create et armoire de Farmer's Delight posees");
        essaiRangement("le coffre vanilla", "chest", Blocks.CHEST);
        essaiRangement("la boîte à outils de Create", "toolbox", BuiltInRegistries.BLOCK.get(new ResourceLocation("create:white_toolbox")));
        essaiRangement("l'armoire de Farmer's Delight", "cabinet", BuiltInRegistries.BLOCK.get(new ResourceLocation("farmersdelight:oak_cabinet")));
    }

    private static BlockPos cible(String cle) {
        return switch (cle) {
            case "chest" -> coffreVanilla();
            case "toolbox" -> boiteCreate();
            default -> panier();
        };
    }

    /** Ouvre le stockage, tente de ranger l'argent de quatre façons, constate que rien n'est entré. */
    private static void essaiRangement(String quoi, String cle, net.minecraft.world.level.block.Block bloc) {
        pasC("Il se met devant " + quoi + " et l'ouvre (clic droit, main vide)", () -> {
            devant(cible(cle));
            valeurAvant = valeurTotale();
        }, () -> devantPret(cible(cle)) && ecoule() >= 1.5, () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Clic droit sur " + quoi, () -> {
            tenirVide();
            clic(cible(cle), Direction.NORTH);
        }, () -> mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu() != mc.player.inventoryMenu
                        && mc.player.containerMenu != mc.player.inventoryMenu && ecoule() >= 0.5,
                () -> "ecran " + mc.screen + ", menu " + mc.player.containerMenu,
                () -> "stockage ouvert : " + mc.player.containerMenu.getClass().getSimpleName() + " (" + mc.player.containerMenu.slots.size() + " cases)");
        pasC("Il essaie de ranger l'argent : clic, clic droit, majuscule-clic, touche numérique", () -> {
            CLICS.add(() -> {
                origine = caseJoueur(s -> s.is(Coin.SILVER.item()));
                if (origine < 0) {
                    throw new IllegalStateException("aucune piece d'argent");
                }
                clicMenu(origine, 0, ClickType.PICKUP);
            });
            CLICS.add(() -> clicMenu(0, 0, ClickType.PICKUP));
            CLICS.add(() -> clicMenu(1, 1, ClickType.PICKUP));
            CLICS.add(() -> clicMenu(origine, 0, ClickType.PICKUP));
            CLICS.add(() -> clicMenu(origine, 0, ClickType.QUICK_MOVE));
            CLICS.add(() -> {
                int barre = -1;
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getItem(i).is(Coin.SILVER.item())) {
                        barre = i;
                    }
                }
                if (barre >= 0) {
                    clicMenu(0, barre, ClickType.SWAP);
                }
            });
        }, () -> ecoule() >= 1.5, () -> "", () -> {
            if (!mc.player.containerMenu.getCarried().isEmpty()) {
                throw new IllegalStateException("le curseur porte " + mc.player.containerMenu.getCarried());
            }
            if (valeurTotale() != valeurAvant) {
                throw new IllegalStateException("valeur " + valeurAvant + " -> " + valeurTotale() + " : des pieces ont bouge");
            }
            for (Slot s : mc.player.containerMenu.slots) {
                if (s.container != mc.player.getInventory() && CoinMath.valueOf(s.getItem()) > 0) {
                    throw new IllegalStateException("une piece est dans la case " + s.index + " de " + quoi);
                }
            }
            String serveur = rcon("data get block " + xyz(cible(cle)));
            if (serveur.contains("_coin")) {
                throw new IllegalStateException("le serveur montre une piece dans " + quoi + " : " + serveur);
            }
            return "pieces refusees par " + quoi + " : curseur vide, aucune piece dans ses " + (mc.player.containerMenu.slots.size() - 36)
                    + " cases, valeur du joueur inchangee (" + valeurAvant + ") ; contenu lu par la console sans aucune piece";
        });
        pasC("Il referme " + quoi, () -> fermer(), () -> mc.screen == null && ecoule() >= 1.0, () -> "ecran " + mc.screen, null);
    }

    // -------------------------------------------------------------------------------------------- gestes parasites

    private static void gestesParasites() {
        pasC("L'équipe pose un dépôt de Create, une source d'énergie de Create Crafts & Additions et un cadre", () -> {
            rcon("setblock " + xyz(depot()) + " create:depot");
            rcon("setblock " + xyz(source()) + " " + CREATIVE);
            rcon("summon minecraft:item_frame " + xyz(cadre()) + " {Facing:1b,TileX:" + cadre().getX() + ",TileY:" + cadre().getY() + ",TileZ:"
                    + cadre().getZ() + "}");
            rcon("summon minecraft:item_frame " + xyz(cadre().east(3)) + " {Facing:1b,TileX:" + cadre().east(3).getX() + ",TileY:" + cadre().getY()
                    + ",TileZ:" + cadre().getZ() + "}");
            valeurAvant = valeurTotale();
        }, () -> ecoule() >= 2 && !mc.level.getEntitiesOfClass(ItemFrame.class, new AABB(cadre()).inflate(1.5)).isEmpty(),
                () -> "cadres " + mc.level.getEntitiesOfClass(ItemFrame.class, new AABB(cadre()).inflate(1.5)).size(), null);
        pasC("Une pièce en main : clic droit sur le dépôt de Create", () -> {
            placerJoueur(depot().getX() + 0.5, depot().getZ() + 2.5, 180, 25);
        }, () -> pres(depot().getX() + 0.5, depot().getZ() + 2.5) && ecoule() >= 1.5, () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Clic droit, une pièce d'argent en main, sur le dépôt", () -> {
            tenir(s -> s.is(Coin.SILVER.item()));
            clic(depot(), Direction.UP);
        }, () -> ecoule() >= 1.5, () -> "", () -> {
            String serveur = rcon("data get block " + xyz(depot()));
            if (valeurTotale() != valeurAvant || serveur.contains("_coin")) {
                throw new IllegalStateException("valeur " + valeurAvant + " -> " + valeurTotale() + ", depot : " + serveur);
            }
            return "clic droit avec une piece sur un depot de Create : refuse, valeur inchangee (" + valeurTotale() + "), le depot ne porte aucune piece";
        });
        pasC("Accroupi (le geste de la recharge du détecteur), la pièce en main, clic droit sur la source d'énergie", () -> {
            placerJoueur(source().getX() + 0.5, source().getZ() + 2.5, 180, 25);
            mc.options.keyShift.setDown(true);
        }, () -> {
            // Une perte de focus de la fenêtre relâche toutes les touches (vu : « accroupi false ») : la touche est tenue
            // à chaque tick jusqu'au clic.
            mc.options.keyShift.setDown(true);
            return pres(source().getX() + 0.5, source().getZ() + 2.5) && mc.player.isShiftKeyDown() && ecoule() >= 1.5;
        },
                () -> "joueur en " + mc.player.blockPosition() + ", accroupi " + mc.player.isShiftKeyDown(), null);
        pasC("Clic droit sur la source, accroupi", () -> {
            tenir(s -> s.is(Coin.SILVER.item()));
            mc.options.keyShift.setDown(true);
            clic(source(), Direction.NORTH);
        }, () -> ecoule() >= 1.5, () -> "", () -> {
            mc.options.keyShift.setDown(false);
            if (valeurTotale() != valeurAvant) {
                throw new IllegalStateException("valeur " + valeurAvant + " -> " + valeurTotale());
            }
            return "geste de recharge (accroupi, clic droit sur une source) avec une piece en main : aucune piece perdue, valeur " + valeurTotale();
        });
        pasC("Debout devant les deux cadres ; un bloc de terre donné pour le témoin", () -> {
            rcon("give " + nom() + " minecraft:dirt 1");
            placerJoueur(cadre().getX() + 1.5, cadre().getZ() + 2.5, 180, 40);
        }, () -> pres(cadre().getX() + 1.5, cadre().getZ() + 2.5) && !mc.player.isShiftKeyDown() && caseJoueur0(Items.DIRT) && ecoule() >= 1.5,
                () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Témoin : la terre en main, interaction avec le premier cadre", () -> {
            tenir(s -> s.is(Items.DIRT));
            ItemFrame cadreVu = mc.level.getEntitiesOfClass(ItemFrame.class, new AABB(cadre()).inflate(1.5)).get(0);
            mc.gameMode.interactAt(mc.player, cadreVu, new net.minecraft.world.phys.EntityHitResult(cadreVu), InteractionHand.MAIN_HAND);
            mc.gameMode.interact(mc.player, cadreVu, InteractionHand.MAIN_HAND);
        }, () -> ecoule() >= 1.5, () -> "", () -> {
            ItemFrame cadreVu = mc.level.getEntitiesOfClass(ItemFrame.class, new AABB(cadre()).inflate(1.5)).get(0);
            if (!cadreVu.getItem().is(Items.DIRT)) {
                throw new IllegalStateException("temoin : le cadre n'a pas pris la terre (" + cadreVu.getItem() + "), l'essai ne prouve rien");
            }
            return "temoin : le premier cadre prend un bloc de terre";
        });
        pasC("Une pièce d'argent en main, interaction avec le second cadre", () -> {
            tenir(s -> s.is(Coin.SILVER.item()));
            ItemFrame cadreVu = mc.level.getEntitiesOfClass(ItemFrame.class, new AABB(cadre().east(3)).inflate(1.5)).get(0);
            var r1 = mc.gameMode.interactAt(mc.player, cadreVu, new net.minecraft.world.phys.EntityHitResult(cadreVu), InteractionHand.MAIN_HAND);
            var r2 = mc.gameMode.interact(mc.player, cadreVu, InteractionHand.MAIN_HAND);
            LOG.info("[e2e] interaction avec le cadre : {} puis {}", r1, r2);
        }, () -> ecoule() >= 1.5, () -> "", () -> {
            ItemFrame cadreVu = mc.level.getEntitiesOfClass(ItemFrame.class, new AABB(cadre().east(3)).inflate(1.5)).get(0);
            if (!cadreVu.getItem().isEmpty() || valeurTotale() != valeurAvant) {
                throw new IllegalStateException("cadre " + cadreVu.getItem() + ", valeur " + valeurAvant + " -> " + valeurTotale());
            }
            return "pas de piece dans le cadre (alors que le premier prenait la terre) : cadre vide, valeur inchangee (" + valeurTotale() + ")";
        });
        pasC("Le bloc de terre est retiré (console)", () -> rcon("clear " + nom() + " minecraft:dirt"),
                () -> !caseJoueur0(Items.DIRT) && ecoule() >= 1.0, () -> "", null);
    }

    // ------------------------------------------------------------------------------------------------- tapis

    private static void tapisDeCreate() {
        pasC("Trois tapis de Create bout à bout ; un objet de terre et une pièce d'argent jetée posés dessus", () -> {
            devant(tapisMilieu());
            reponse = rcon("e2emonnaie belt " + xyz(tapis()));
            avantBelt = valeurTotale();
        }, () -> ecoule() >= 2 && reponse.contains("ok") && devantPret(tapisMilieu()),
                () -> "reponse " + reponse, () -> "tapis : " + reponse.trim());
        pasC("La terre (témoin) et la pièce sont posées sur le tapis du milieu", () -> {
            BlockPos dessus = tapisMilieu().above();
            for (int i = 0; i < 3; i++) {
                BlockPos t = tapis().east(i).above();
                rcon("summon minecraft:item " + (t.getX() + 0.5) + " " + (t.getY() - 0.1) + " " + (t.getZ() + 0.5)
                        + " {Item:{id:\"minecraft:dirt\",Count:4b},Motion:[0.0,0.0,0.0]}");
            }
            reponse = rcon("e2emonnaie sol_prise " + nom() + " silver 1 " + xyz(dessus));
            posPiece = Vec3.atBottomCenterOf(dessus);
        }, () -> ecoule() >= 1.0 && reponse.contains("ok"), () -> "reponse " + reponse, null);
        pasCD(30, "Au bout de 6 s : la terre est prise par le tapis, la pièce est restée où elle est", () -> {}, () -> ecoule() >= 6,
                () -> "", () -> {
                    List<ItemEntity> terre = mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(tapis()).inflate(6, 4, 6),
                            e -> e.getItem().is(Items.DIRT));
                    List<CoinDropEntity> pieces = mc.level.getEntitiesOfClass(CoinDropEntity.class, new AABB(tapis()).inflate(6, 4, 6));
                    if (terre.size() >= 3) {
                        throw new IllegalStateException("temoin : le tapis n'a pris aucune des 3 piles de terre, l'essai ne prouve rien");
                    }
                    if (pieces.size() != 1) {
                        throw new IllegalStateException(pieces.size() + " piece(s) au sol pres du tapis");
                    }
                    double ecart = pieces.get(0).position().distanceTo(posPiece);
                    if (ecart > 0.8) {
                        throw new IllegalStateException("la piece a bouge de " + String.format("%.2f", ecart) + " bloc");
                    }
                    return "tapis de Create : " + (3 - terre.size()) + " pile(s) de terre sur 3 (temoin) ont disparu du sol en 6 s (prises par le tapis), la piece d'argent est restee a " + String.format("%.2f", ecart)
                            + " bloc de sa place, entiere (" + pieces.get(0).count() + ")";
                });
        pasC("Il ramasse la pièce (il se place dessus)", () -> {
            rcon("tp " + nom() + " " + posPiece.x + " " + (posPiece.y + 0.2) + " " + posPiece.z + " 180 25");
        }, () -> valeurTotale() == avantBelt && ecoule() >= 1.0,
                () -> "valeur " + valeurTotale() + " au lieu de " + avantBelt,
                () -> "la piece est revenue chez le joueur : valeur " + valeurTotale() + " (comme avant la jetee)");
    }

    // ------------------------------------------------------------------------------------------------- bourse

    private static void etapeBourse() {
        pasC("Il se met devant la table pour fabriquer une bourse (le matériel donné par la console)", () -> {
            rcon("give " + nom() + " minecraft:string 1");
            rcon("give " + nom() + " minecraft:leather 3");
            devant(table());
        }, () -> devantPret(table()) && ecoule() >= 2.0 && caseJoueur0(Items.LEATHER) && caseJoueur0(Items.STRING),
                () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Clic droit sur la table", () -> {
            tenirVide();
            clic(table(), Direction.NORTH);
        }, () -> mc.screen instanceof CraftingScreen, () -> "ecran " + mc.screen, null);
        pasC("Il dispose la recette de la bourse (une ficelle en haut, trois cuirs) et prend le résultat", () -> {
            poserUnParCase(Items.STRING, 2);
            poserUnParCase(Items.LEATHER, 4, 6, 8);
            CLICS.add(attendreSortie(40));
            CLICS.add(() -> clicMenu(0, 0, ClickType.QUICK_MOVE));
        }, () -> !bourse().isEmpty() && ecoule() >= 1.0, () -> "pas de bourse dans l'inventaire", () -> "bourse fabriquee a la table avec sa recette (ficelle et trois cuirs) : "
                + bourse().getHoverName().getString());
        pasC("Il referme la table, tient la bourse (vide)", () -> {
            fermer();
        }, () -> mc.screen == null && ecoule() >= 1.0, () -> "", () -> "bourse vide : " + Purse.total(bourse()) + " cuivre");
        pasC("Des pièces sont posées au sol à ses pieds : 3 or (la console compte « créé par l'équipe »)", () -> {
            valeurAvant = valeurTotale();
            reponse = rcon("e2emonnaie sol_creee gold 3 " + xyz(mc.player.blockPosition()));
        }, () -> Purse.total(bourse()) == 300 && ecoule() >= 1.0,
                () -> "bourse " + Purse.total(bourse()) + ", or libre " + pieces(Coin.GOLD) + ", reponse " + reponse,
                () -> {
                    if (pieces(Coin.GOLD) != 0 || valeurLibre() != valeurAvant) {
                        throw new IllegalStateException("or libre " + pieces(Coin.GOLD) + " : la bourse ne les a pas pris seuls");
                    }
                    return "la bourse ramasse les pieces au sol : " + Purse.total(bourse()) + " cuivres (3 or) dans la bourse, aucune piece libre en plus";
                });
        pasC("Clic droit avec la bourse en main : l'écran de la bourse", () -> {
            tenir(Purse::isPurse);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> ecranOuvert(PurseMenu.class), () -> "ecran " + mc.screen, () -> "ecran de la bourse ouvert : " + ((PurseMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).count(Coin.GOLD) + " or");
        // Le menu porte les cases de l'inventaire : l'inventaire du client suit pendant que l'écran est ouvert.
        pasC("Clic gauche sur « 10 » de la ligne de l'argent : 10 pièces entrent dans la bourse", () -> clicElement("row.silver.10", 0),
                () -> ecranOuvert(PurseMenu.class) && menuBourse().count(Coin.SILVER) == 10 && pieces(Coin.SILVER) == 40 && ecoule() >= 1.0,
                () -> "ecran : argent " + menuBourse().count(Coin.SILVER) + ", argent libre cote client " + pieces(Coin.SILVER),
                () -> "depot de 10 (clic gauche) : l'ecran montre 10 argent et, ecran toujours ouvert, l'inventaire du client montre "
                        + pieces(Coin.SILVER) + " argent en vrac (au lieu de 50)");
        pasC("Clic droit sur « 1 » de la ligne de l'or : 1 or sort en vrac", () -> clicElement("row.gold.1", 1),
                () -> ecranOuvert(PurseMenu.class) && menuBourse().count(Coin.GOLD) == 2 && pieces(Coin.GOLD) == 1 && ecoule() >= 1.0,
                () -> "ecran : or " + menuBourse().count(Coin.GOLD) + ", or libre cote client " + pieces(Coin.GOLD),
                () -> "retrait de 1 (clic droit) : l'ecran montre 2 or, le client voit 1 or en vrac, ecran ouvert");
        pasC("« Déposer tout » : l'or et les 40 pièces d'argent en vrac entrent dans la bourse", () -> boutonDeposer(),
                () -> menuBourse().count(Coin.SILVER) == 50 && menuBourse().count(Coin.GOLD) == 3 && valeurLibre() == 0 && ecoule() >= 1.0,
                () -> "ecran : argent " + menuBourse().count(Coin.SILVER) + ", or " + menuBourse().count(Coin.GOLD) + ", valeur libre client "
                        + valeurLibre() + " ; serveur : " + rcon("data get entity " + nom() + " Inventory"),
                () -> "Deposer tout : l'ecran de la bourse montre 3 or et 50 argent (" + menuBourse().total()
                        + " cuivres) et, ecran ouvert, le client ne voit plus aucune piece en vrac (valeur libre " + valeurLibre() + ")");
        pasC("« Regrouper » : 50 argent deviennent 5 or, à valeur égale", () -> boutonRegrouper(),
                () -> menuBourse().count(Coin.GOLD) == 8 && menuBourse().count(Coin.SILVER) == 0 && ecoule() >= 1.0,
                () -> "ecran : or " + menuBourse().count(Coin.GOLD) + ", argent " + menuBourse().count(Coin.SILVER),
                () -> "Regrouper : l'ecran montre " + menuBourse().count(Coin.GOLD) + " or, " + menuBourse().count(Coin.SILVER) + " argent, valeur "
                        + menuBourse().total() + " (inchangee : 800)");
        pasC("Panneau Change : « 1 or → 10 argent »", () -> clicElement("break.gold", 0),
                () -> menuBourse().count(Coin.GOLD) == 7 && menuBourse().count(Coin.SILVER) == 10 && ecoule() >= 1.0,
                () -> "ecran : or " + menuBourse().count(Coin.GOLD) + ", argent " + menuBourse().count(Coin.SILVER),
                () -> "Change, casser : 7 or et 10 argent, valeur " + menuBourse().total());
        pasC("Panneau Change : « 10 argent → 1 or »", () -> clicElement("group.gold", 0),
                () -> menuBourse().count(Coin.GOLD) == 8 && menuBourse().count(Coin.SILVER) == 0 && ecoule() >= 1.0,
                () -> "ecran : or " + menuBourse().count(Coin.GOLD) + ", argent " + menuBourse().count(Coin.SILVER),
                () -> "Change, regrouper la paire : 8 or, 0 argent, valeur " + menuBourse().total() + " (inchangee : 800)");
        pasC("Il referme l'écran : l'inventaire du client est le même", () -> fermer(),
                () -> mc.screen == null && Purse.count(bourse(), Coin.GOLD) == 8 && pieces(Coin.SILVER) == 0 && ecoule() >= 0.8,
                () -> "ecran " + mc.screen + ", bourse or " + Purse.count(bourse(), Coin.GOLD) + ", argent libre " + pieces(Coin.SILVER),
                () -> "apres la fermeture : bourse " + Purse.total(bourse()) + " cuivres (8 or), aucune piece d'argent libre, valeur totale " + valeurTotale());
        pasC("L'équipe reprend 500 cuivres : /eco take (les bourses d'abord)", () -> {
            reponse = rcon("eco take " + nom() + " 500 reprise avant le coffre-fort");
        }, () -> Purse.total(bourse()) == 300 && ecoule() >= 1.5,
                () -> "bourse " + Purse.total(bourse()) + ", reponse " + reponse,
                () -> "/eco take : " + reponse.trim() + " ; la bourse garde " + Purse.total(bourse()) + " cuivres");
    }

    private static boolean caseJoueur0(Item item) {
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ coffre-fort

    private static void etapeCoffreFort() {
        pasC("Il se met devant la table pour fabriquer un coffre-fort (matériel donné par la console)", () -> {
            rcon("give " + nom() + " minecraft:iron_ingot 7");
            rcon("give " + nom() + " minecraft:lever 1");
            rcon("give " + nom() + " minecraft:iron_block 1");
            devant(table());
        }, () -> devantPret(table()) && ecoule() >= 2.0 && caseJoueur0(Items.LEVER) && caseJoueur0(Items.IRON_BLOCK), () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Clic droit sur la table", () -> {
            tenirVide();
            clic(table(), Direction.NORTH);
        }, () -> mc.screen instanceof CraftingScreen, () -> "ecran " + mc.screen, null);
        pasC("Il dispose la recette du coffre-fort (7 lingots, un levier, un bloc de fer) et prend le résultat", () -> {
            poserUnParCase(Items.IRON_INGOT, 1, 2, 3, 4, 6, 7, 9);
            poserUnParCase(Items.LEVER, 5);
            poserUnParCase(Items.IRON_BLOCK, 8);
            CLICS.add(attendreSortie(40));
            CLICS.add(() -> clicMenu(0, 0, ClickType.QUICK_MOVE));
        }, () -> caseJoueur0(EconomyRegistry.SAFE_ITEM.get()) && ecoule() >= 1.0, () -> "pas de coffre-fort dans l'inventaire",
                () -> "coffre-fort fabrique a la table avec sa recette");
        pasC("Il referme la table et pose le coffre-fort sur le sol (clic droit)", () -> {
            fermer();
            placerJoueur(coffreFort().getX() + 0.5, coffreFort().getZ() + 2.5, 180, 40);
        }, () -> mc.screen == null && devantPret(coffreFort()) && ecoule() >= 1.5, () -> "ecran " + mc.screen, null);
        pasC("Pose du coffre-fort", () -> {
            tenir(s -> s.is(EconomyRegistry.SAFE_ITEM.get()));
            clic(coffreFort().below(), Direction.UP);
        }, () -> mc.level.getBlockState(coffreFort()).is(EconomyRegistry.SAFE.get()),
                () -> "bloc " + mc.level.getBlockState(coffreFort()), () -> "coffre-fort pose par un clic droit reel en " + coffreFort().toShortString());
        pasC("Clic droit sur le coffre-fort (main vide) : son écran", () -> {
            tenirVide();
            clic(coffreFort(), Direction.NORTH);
        }, () -> ecranOuvert(SafeMenu.class), () -> "ecran " + mc.screen, () -> "ecran du coffre-fort ouvert par son proprietaire");
        pasC("Clic gauche sur « 1 » de la ligne de l'or : la pièce vient de la bourse (pas d'or en vrac)", () -> clicElement("row.gold.1", 0),
                () -> menuCoffre().count(Coin.GOLD) == 1 && Purse.count(bourse(), Coin.GOLD) == 2 && ecoule() >= 1.0,
                () -> "compte or " + menuCoffre().count(Coin.GOLD) + ", bourse cote client " + Purse.count(bourse(), Coin.GOLD),
                () -> "depot de 1 (clic gauche) : le compte montre 1 or, la bourse du client (ecran ouvert) en a 2");
        pasC("Clic droit sur « 1 » de la ligne de l'or : la pièce sort en vrac", () -> clicElement("row.gold.1", 1),
                () -> menuCoffre().count(Coin.GOLD) == 0 && pieces(Coin.GOLD) == 1 && ecoule() >= 1.0,
                () -> "compte or " + menuCoffre().count(Coin.GOLD) + ", or libre cote client " + pieces(Coin.GOLD),
                () -> "retrait de 1 (clic droit) : le compte n'a plus d'or, le client voit 1 or en vrac, ecran ouvert");
        pasC("« Déposer tout » : les 300 cuivres (bourse et vrac) entrent dans le compte", () -> boutonDeposer(),
                () -> menuCoffre().total() == 300 && valeurTotale() == 0 && ecoule() >= 1.0,
                () -> "compte " + menuCoffre().total() + ", client " + valeurTotale(),
                () -> "Deposer tout : le compte affiche 300 cuivres (3 or) et, ecran ouvert, le client ne porte plus rien (valeur " + valeurTotale() + ")");
        pasC("Panneau Change du coffre-fort : « 1 or → 10 argent »", () -> clicElement("break.gold", 0),
                () -> menuCoffre().count(Coin.GOLD) == 2 && menuCoffre().count(Coin.SILVER) == 10 && ecoule() >= 1.0,
                () -> "compte or " + menuCoffre().count(Coin.GOLD) + ", argent " + menuCoffre().count(Coin.SILVER),
                () -> "Change dans le coffre-fort, casser : 2 or et 10 argent, total " + menuCoffre().total());
        pasC("Panneau Change du coffre-fort : « 10 argent → 1 or »", () -> clicElement("group.gold", 0),
                () -> menuCoffre().count(Coin.GOLD) == 3 && menuCoffre().count(Coin.SILVER) == 0 && menuCoffre().total() == 300 && ecoule() >= 1.0,
                () -> "compte or " + menuCoffre().count(Coin.GOLD) + ", argent " + menuCoffre().count(Coin.SILVER),
                () -> "Change dans le coffre-fort, regrouper la paire : 3 or, total " + menuCoffre().total() + " (inchange)");
        pasC("Il referme le coffre-fort : le joueur ne porte plus rien", () -> fermer(), () -> mc.screen == null && valeurTotale() == 0 && ecoule() >= 0.8,
                () -> "ecran " + mc.screen + ", joueur " + valeurTotale(),
                () -> "apres la fermeture le joueur ne porte plus aucune piece (bourse comprise) : valeur " + valeurTotale() + ", le compte du serveur garde 300");
        pasC("Un second coffre-fort (donné par la console) : il tente de le poser", () -> {
            rcon("give " + nom() + " oneforallsurvival:safe 1");
            placerJoueur(secondCoffreFort().getX() + 0.5, secondCoffreFort().getZ() + 2.5, 180, 40);
        }, () -> pres(secondCoffreFort().getX() + 0.5, secondCoffreFort().getZ() + 2.5) && caseJoueur0(EconomyRegistry.SAFE_ITEM.get()) && ecoule() >= 2.0,
                () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Clic droit pour poser le second : refusé", () -> {
            tenir(s -> s.is(EconomyRegistry.SAFE_ITEM.get()));
            clic(secondCoffreFort().below(), Direction.UP);
        }, () -> ecoule() >= 2.0, () -> "pile du client " + caseJoueur0(EconomyRegistry.SAFE_ITEM.get()), () -> {
            String serveur = rcon("execute if block " + xyz(secondCoffreFort()) + " oneforallsurvival:safe");
            String objet = rcon("clear " + nom() + " oneforallsurvival:safe 0");
            if (!mc.level.getBlockState(secondCoffreFort()).isAir() || !objet.contains("Found 1") || serveur.contains("Test passed")
                    || !caseJoueur0(EconomyRegistry.SAFE_ITEM.get())) {
                throw new IllegalStateException("bloc " + mc.level.getBlockState(secondCoffreFort()) + ", serveur " + serveur + ", objet " + objet);
            }
            return "second coffre-fort refuse : aucun bloc en " + secondCoffreFort().toShortString() + " (client et serveur : " + serveur.trim()
                    + "), l'objet reste dans l'inventaire du joueur cote serveur (" + objet.trim() + ") et le client le voit aussi "
                    + "(le serveur lui renvoie l'inventaire apres le refus)";
        });
        pasC("L'objet en trop est retiré (console)", () -> rcon("clear " + nom() + " oneforallsurvival:safe"),
                () -> !caseJoueur0(EconomyRegistry.SAFE_ITEM.get()) && ecoule() >= 1.5, () -> "", null);
    }

    // ----------------------------------------------------------------------------------------------- second joueur

    private static final Pattern CLE_VALEUR = Pattern.compile("(\\w+)=(\\[[^\\]]*\\]|[^ ]+)");

    private static Map<String, String> lire(String texte) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = CLE_VALEUR.matcher(texte);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static void secondJoueur() {
        pasCD(40, "Un second joueur sans droits (connexion simulée côté serveur) tente d'ouvrir ce coffre-fort", () -> {
            reponse = rcon("e2emonnaie autre " + xyz(coffreFort()));
        }, () -> reponse.contains("AUTRE_FIN"), () -> "reponse " + reponse, () -> {
            Map<String, String> r = lire(reponse);
            if (!"false".equals(r.get("operateur")) || !"false".equals(r.get("ouvert")) || !"false".equals(r.get("menu"))
                    || !"NOT_ALLOWED".equals(r.get("retrait")) || !"NOT_ALLOWED".equals(r.get("depot")) || !"0".equals(r.get("compte"))
                    || !r.get("messages").contains("not_yours")) {
                throw new IllegalStateException(reponse);
            }
            if (!("" + DEPART).equals(r.get("depart1")) || !("" + DEPART).equals(r.get("porte1")) || !"0".equals(r.get("depart2"))
                    || !("" + DEPART).equals(r.get("porte2"))) {
                throw new IllegalStateException("somme de depart du second joueur : " + reponse);
            }
            return "second joueur (non operateur, connexion simulee) : il a recu " + r.get("depart1") + " cuivres de depart, ne peut pas ouvrir le "
                    + "coffre-fort (ouvert=" + r.get("ouvert") + ", message " + r.get("messages") + "), retrait et depot refuses (" + r.get("retrait")
                    + "), compte inchange (" + r.get("compte") + ") ; apres sortie et retour il ne recoit rien de plus (depart " + r.get("depart2")
                    + ", porte " + r.get("porte2") + ")";
        });
    }

    // ---------------------------------------------------------------------------------------- équipe : injection, retrait

    /** Les lignes que {@code /eco stats} doit contenir, à ce stade. */
    private static List<String> lignesAttendues(long coffres, long circulation) {
        List<String> l = new ArrayList<>();
        l.add(Component.translatable(MoneySource.STARTING.langKey()).getString() + ": " + fmt(CREE_DEPART));
        l.add(Component.translatable(MoneySource.STAFF.langKey()).getString() + ": " + fmt(CREE_EQUIPE));
        l.add(Component.translatable(MoneySink.STAFF.langKey()).getString() + ": " + fmt(DETRUIT_EQUIPE));
        l.add(Component.translatable(MoneySink.FIRE.langKey()).getString() + ": " + fmt(0));
        l.add(Component.translatable(MoneySink.VOID.langKey()).getString() + ": " + fmt(0));
        l.add(Component.translatable(MoneySink.DESPAWN.langKey()).getString() + ": " + fmt(0));
        l.add("Vaults (exact mass): " + fmt(coffres));
        l.add("In circulation (created minus destroyed): " + fmt(circulation));
        return l;
    }

    private static String manque(String stats, List<String> lignes) {
        for (String ligne : lignes) {
            if (!stats.contains(ligne)) {
                return ligne;
            }
        }
        return null;
    }

    private static void equipe() {
        pasC("La bourse (vide) est retirée : sinon les pièces de l'équipe y entreraient d'abord", () -> rcon("clear " + nom() + " oneforallsurvival:purse"),
                () -> bourse().isEmpty() && ecoule() >= 1.5, () -> "bourse " + bourse() + ", valeur " + valeurTotale(), null);
        pasC("L'équipe injecte 1 rubis (10 000 cuivres) : /eco give", () -> {
            reponse = rcon("eco give " + nom() + " 10000 injection d'un rubis");
        }, () -> pieces(Coin.RUBY) == 1 && ecoule() >= 1.5, () -> "rubis " + pieces(Coin.RUBY) + ", reponse " + reponse,
                () -> "/eco give : " + reponse.trim() + " ; le joueur porte 1 piece de rubis");
        pasC("L'équipe retire 50 cuivres : /eco take (le rubis est cassé, la monnaie rendue)", () -> {
            reponse = rcon("eco take " + nom() + " 50 retrait de controle");
        }, () -> valeurTotale() == 9950 && pieces(Coin.RUBY) == 0 && ecoule() >= 1.5,
                () -> "valeur " + valeurTotale() + ", rubis " + pieces(Coin.RUBY) + ", reponse " + reponse,
                () -> "/eco take : " + reponse.trim() + " ; le joueur porte " + valeurTotale() + " cuivres : " + fmt(valeurTotale()));
        pasC("Il ouvre le coffre-fort et y dépose tout (9 950 cuivres de plus)", () -> {
            placerJoueur(coffreFort().getX() + 0.5, coffreFort().getZ() + 2.5, 180, 40);
        }, () -> pres(coffreFort().getX() + 0.5, coffreFort().getZ() + 2.5) && ecoule() >= 1.5, () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Clic droit sur le coffre-fort", () -> {
            tenirVide();
            clic(coffreFort(), Direction.NORTH);
        }, () -> ecranOuvert(SafeMenu.class), () -> "ecran " + mc.screen, null);
        pasC("« Déposer tout »", () -> boutonDeposer(),
                () -> ((SafeMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).total() == COFFRE_FINAL && ecoule() >= 1.0,
                () -> "compte " + ((SafeMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).total(),
                () -> "le compte affiche " + ((SafeMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).total() + " cuivres (" + fmt(COFFRE_FINAL) + ")");
        pasC("Il referme le coffre-fort : le joueur ne porte plus rien", () -> fermer(), () -> mc.screen == null && valeurTotale() == 0 && ecoule() >= 0.8,
                () -> "ecran " + mc.screen + ", joueur " + valeurTotale(),
                () -> "apres la fermeture le joueur ne porte plus aucune piece : valeur " + valeurTotale());
        pasC("/eco stats : créé par source, détruit par cause, masse des coffres-forts, en circulation", () -> {
            reponse = rcon("eco stats");
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            // En circulation : créé (1 000 + 10 800) moins détruit (1 050) ; elle doit aussi valoir le coffre de Dev plus ce que porte le second joueur.
            long circulation = CREE_DEPART + CREE_EQUIPE - DETRUIT_EQUIPE;
            if (circulation != COFFRE_FINAL + AUTRE_PORTE) {
                throw new IllegalStateException("attendus incoherents : " + circulation);
            }
            String m = manque(reponse, lignesAttendues(COFFRE_FINAL, circulation));
            if (m != null) {
                throw new IllegalStateException("ligne attendue absente : \"" + m + "\" dans : " + reponse.replace('\n', '|'));
            }
            return "/eco stats : " + reponse.trim().replace('\n', '|') + " ; en circulation = coffre de Dev (" + COFFRE_FINAL + ") + ce que porte le second joueur ("
                    + AUTRE_PORTE + ") = " + circulation + " : les comptes sont justes";
        });
        pasC("/eco ledger : 8 lignes, les deux sommes de départ, les créations et destructions de l'équipe", () -> {
            reponse2 = rcon("eco ledger");
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            if (!reponse2.contains("(8 lines)") || !reponse2.contains("Starting money") || !reponse2.contains("Removed by staff")
                    || !reponse2.contains("Created by staff") || !reponse2.contains("injection d'un rubis") || !reponse2.contains("retrait de controle")) {
                throw new IllegalStateException(reponse2.replace('\n', '|'));
            }
            return "/eco ledger : " + reponse2.trim().replace('\n', '|');
        });
    }

    private static String reponse2 = "";

    private static void fin() {
        pasC("Positions et attendus écrits ; le joueur rentre à son point de départ (la zone se décharge)", () -> {
            try {
                List<String> lignes = new ArrayList<>();
                lignes.add("origine=" + xyz(o));
                lignes.add("depart=" + xyz(depart));
                lignes.add("coffre=" + xyz(coffreFort()));
                lignes.add("stats=" + reponse.trim().replace('\n', '|'));
                lignes.add("ledger=" + reponse2.split("\n")[0].trim());
                lignes.add("massecoffres=" + COFFRE_FINAL);
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            rcon("clear " + nom() + " oneforallsurvival:purse");
            airePrete = false;
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && mc.player.blockPosition().distSqr(depart) < 9, () -> "joueur en " + mc.player.blockPosition(), null);
        pasC("Le joueur redevient opérateur (console RCON : op), pour les étapes qui suivent", () -> rcon("op " + nom()),
                () -> operateur() && ecoule() >= 1.5 && mc.player.getHealth() > 0, () -> "operateur " + operateur(),
                () -> "op par RCON, hasPermissions(2) = " + operateur() + ", vie " + mc.player.getHealth());
    }

    // ================================================================================================
    // Départ : somme de départ, déconnexion et reconnexion (avant les autres lots, qui vident l'inventaire)
    // ================================================================================================

    /** Appelé par {@link E2eClient} chaque fois qu'il entre (ou revient, après la reconnexion) dans la phase du départ. */
    static void commencerDepart() {
        debutDepart = System.nanoTime();
    }

    /** Ce que {@link E2eClient} doit faire ensuite. */
    enum Suite { ATTENDRE, RECONNECTER, FINI }

    /**
     * @param minecraft le jeu
     * @param relance vrai à la relance du serveur : le joueur ne doit rien recevoir
     */
    static Suite depart(Minecraft minecraft, boolean relance) {
        mc = minecraft;
        double s = (System.nanoTime() - debutDepart) / 1e9;
        if (relance) {
            if (s < 5) {
                return Suite.ATTENDRE;
            }
            try {
                Map<String, String> lu = lireFichier();
                String stats = rcon("eco stats").trim().replace('\n', '|');
                if (valeurTotale() != 0 || !stats.equals(lu.get("stats"))) {
                    E2eClient.fail("relance : le joueur porte " + valeurTotale() + " cuivres (attendu 0, rien de redonne), stats : " + stats
                            + " ; avant l'arret : " + lu.get("stats"));
                    return Suite.FINI;
                }
                E2eClient.pass("relance : apres l'arret et la relance du serveur, le joueur ne recoit aucune somme de depart (il porte " + valeurTotale()
                        + " cuivre) et /eco stats est identique a celui d'avant l'arret : " + stats);
            } catch (RuntimeException e) {
                E2eClient.fail("relance : controle de la somme de depart : " + e.getMessage());
            }
            return Suite.FINI;
        }
        if (departEtat == 0) {
            if (valeurTotale() == DEPART && s >= 2) {
                String stats = rcon("eco stats").trim();
                String ledger = rcon("eco ledger").trim();
                List<String> attendu = List.of(Component.translatable(MoneySource.STARTING.langKey()).getString() + ": " + fmt(DEPART),
                        Component.translatable(MoneySource.STAFF.langKey()).getString() + ": " + fmt(0));
                if (manque(stats, attendu) != null || !ledger.contains("(1 lines)")) {
                    E2eClient.fail("registre non neuf (supprimer oneforallsurvival_economy.dat et oneforallsurvival_safes.dat de run-server/world/data) : "
                            + stats.replace('\n', '|') + " ; " + ledger.split("\n")[0]);
                    return Suite.FINI;
                }
                E2eClient.pass("joueur neuf : " + valeurTotale() + " cuivres recus a sa premiere connexion (" + fmt(valeurTotale()) + "), le registre note "
                        + fmt(DEPART) + " de somme de depart et une ligne");
                departEtat = 1;
                return Suite.RECONNECTER;
            }
            if (s > 20) {
                E2eClient.fail("somme de depart non recue : le client porte " + valeurTotale() + " cuivres apres 20 s");
                return Suite.FINI;
            }
            return Suite.ATTENDRE;
        }
        // Après la vraie déconnexion et la reconnexion du client.
        if (s < 5) {
            return Suite.ATTENDRE;
        }
        String stats = rcon("eco stats").trim();
        String ledger = rcon("eco ledger").trim();
        if (valeurTotale() != DEPART
                || manque(stats, List.of(Component.translatable(MoneySource.STARTING.langKey()).getString() + ": " + fmt(DEPART))) != null
                || !ledger.contains("(1 lines)")) {
            E2eClient.fail("apres la reconnexion : le client porte " + valeurTotale() + " cuivres (attendu " + DEPART + "), stats : "
                    + stats.replace('\n', '|') + " ; " + ledger.split("\n")[0]);
            return Suite.FINI;
        }
        E2eClient.pass("apres une vraie deconnexion et une reconnexion du client : il porte toujours " + valeurTotale()
                + " cuivres, le registre n'a rien ajoute (somme de depart " + fmt(DEPART) + ", " + ledger.split("\n")[0] + ")");
        // Les autres lots videront l'inventaire : l'argent de départ part par la commande de l'équipe, donc au registre.
        String retrait = rcon("eco take " + mc.player.getGameProfile().getName() + " " + DEPART + " avant les essais des autres lots");
        E2eClient.pass("/eco take " + DEPART + " : " + retrait.trim());
        return Suite.FINI;
    }

    // ================================================================================================
    // Après un arrêt et une relance du serveur
    // ================================================================================================

    private static Map<String, String> lireFichier() {
        Map<String, String> lu = new LinkedHashMap<>();
        try {
            for (String l : Files.readAllLines(fichier(), StandardCharsets.UTF_8)) {
                int i = l.indexOf('=');
                lu.put(l.substring(0, i), l.substring(i + 1));
            }
        } catch (IOException e) {
            throw new IllegalStateException("premiere phase absente : " + fichier(), e);
        }
        return lu;
    }

    private static BlockPos position(String xyz) {
        String[] t = xyz.split(" ");
        return new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]));
    }

    static void relance() {
        Map<String, String> lu = new LinkedHashMap<>();
        pasC("Relecture des positions écrites avant l'arrêt", () -> {
            lu.putAll(lireFichier());
            o = position(lu.get("origine"));
            depart = position(lu.get("depart"));
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            placerJoueur(coffreFort().getX() + 0.5, coffreFort().getZ() + 2.5, 180, 40);
        }, () -> pres(coffreFort().getX() + 0.5, coffreFort().getZ() + 2.5) && ecoule() >= 3
                && mc.level.getBlockState(coffreFort()).is(EconomyRegistry.SAFE.get()),
                () -> "joueur en " + mc.player.blockPosition() + ", bloc " + mc.level.getBlockState(coffreFort()),
                () -> "relance : zone relue du disque en " + o.toShortString() + ", coffre-fort relu en " + coffreFort().toShortString());
        pasC("Relance : clic droit sur le coffre-fort", () -> {
            tenirVide();
            clic(coffreFort(), Direction.NORTH);
        }, () -> ecranOuvert(SafeMenu.class) && ecoule() >= 1.0, () -> "ecran " + mc.screen, () -> {
            long total = ((SafeMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).total();
            if (total != COFFRE_FINAL) {
                throw new IllegalStateException("compte " + total + " au lieu de " + COFFRE_FINAL);
            }
            return "relance : le compte du coffre-fort est relu a l'identique : " + total + " cuivres (" + fmt(total) + ")";
        });
        pasC("Relance : fermeture de l'écran ; /eco stats et /eco ledger relus", () -> fermer(), () -> mc.screen == null && ecoule() >= 0.8,
                () -> "ecran " + mc.screen, () -> {
                    String stats = rcon("eco stats").trim().replace('\n', '|');
                    String ledger = rcon("eco ledger").split("\n")[0].trim();
                    if (!stats.equals(lu.get("stats")) || !ledger.equals(lu.get("ledger"))) {
                        throw new IllegalStateException("stats " + stats + " (avant " + lu.get("stats") + "), registre " + ledger + " (avant " + lu.get("ledger") + ")");
                    }
                    return "relance : totaux du registre identiques a ceux d'avant l'arret : " + stats + " ; " + ledger;
                });
        pasCD(40, "Relance : le second joueur revient : sa somme de départ n'est pas redonnée, il n'ouvre toujours pas le coffre-fort", () -> {
            reponse = rcon("e2emonnaie autre " + xyz(coffreFort()));
        }, () -> reponse.contains("AUTRE_FIN"), () -> "reponse " + reponse, () -> {
            Map<String, String> r = lire(reponse);
            if (!"0".equals(r.get("depart1")) || !"0".equals(r.get("depart2")) || !("" + AUTRE_PORTE).equals(r.get("porte1"))
                    || !"false".equals(r.get("ouvert")) || !"NOT_ALLOWED".equals(r.get("retrait"))) {
                throw new IllegalStateException(reponse);
            }
            return "relance : le second joueur revient apres le redemarrage : somme de depart redonnee " + r.get("depart1") + ", il porte toujours "
                    + r.get("porte1") + " cuivres, ouvert=" + r.get("ouvert") + ", retrait " + r.get("retrait");
        });
        pasC("Relance : rangement de l'aire de la monnaie", () -> {
            rcon("fill " + xyz(p(-4, 0, -8)) + " " + xyz(p(44, 8, 22)) + " air");
            airePrete = false;
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
            rcon("gamerule doMobSpawning true");
        }, () -> ecoule() >= 2 && mc.level.getBlockState(coffreFort()).isAir(), () -> "bloc " + mc.level.getBlockState(coffreFort()),
                () -> "aire videe, regles du monde rendues");
    }

    // ================================================================================================
    // Arrêt
    // ================================================================================================

    /** Avant le {@code /stop}, même après un échec : joueur rendu opérateur, règles du monde rendues. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        mc = minecraft;
        mc.options.keyShift.setDown(false);
        if (o != null) {
            try {
                rcon("op " + nom());
                rcon("gamerule doMobSpawning true");
            } catch (RuntimeException e) {
                LOG.error("[e2e] rcon impossible avant l'arret", e);
            }
        }
    }
}
