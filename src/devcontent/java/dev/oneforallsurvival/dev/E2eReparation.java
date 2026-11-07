package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.repair.WelderEnergy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Étape « réparation » de {@link E2eClient} (développement seulement, jamais empaquetée). Tout passe par le
 * vrai chemin d'un joueur : {@code MultiPlayerGameMode.useItemOn}, le poste en main principale et la pièce en
 * main secondaire, et de vraies commandes ({@code /data merge}, {@code /item replace}, {@code /coast}). Le
 * client ne connaît le serveur que par ce qu'il reçoit : messages traduisibles, inventaire et état du bloc.
 *
 * <p>Deux séquences. {@link #premierePhase} répare, exerce {@code /coast} et laisse trois machines et une zone
 * dont les positions vont dans {@value #FICHIER}. {@link #relance}, après un vrai arrêt et une vraie relance
 * du serveur, relit le tout.
 */
final class E2eReparation {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-persistance.txt";
    private static final String ZONE = "e2e_cote";
    private static final String ZONE_GARDEE = "e2e_persist";
    private static final double DELAI = 25;
    private static final int FENTE_ETABLI = 0;
    private static final int FENTE_TESTEUR = 1;
    private static final int FENTE_POSTE = 2;
    private static final int FENTE_VIDE = 3;

    /** Un message reçu : sa clé de langue (vide s'il n'en a pas), ses arguments, son texte. */
    private record Recu(String cle, List<String> args, String texte) {}

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec,
            Supplier<String> succes) {}

    private static final String P_RAPPORT = "message.oneforallsurvival.report.";
    private static final String P_REPARE = "message.oneforallsurvival.repair.";
    private static final String P_COTE = "command.oneforallsurvival.coast.";
    private static final String DATA_MODIFIE = "commands.data.block.modified";

    private static final List<Recu> RECUS = new ArrayList<>();
    private static final List<Pas> PAS = new ArrayList<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;

    private static Minecraft mc;
    private static BlockPos aire;
    private static BlockPos m1;
    private static BlockPos m2;
    private static BlockPos m3;
    private static String coteAvant;

    private E2eReparation() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape";
    }

    static void recevoir(Component message) {
        Recu r = lire(message);
        synchronized (RECUS) {
            RECUS.add(r);
        }
    }

    private static Recu lire(Component message) {
        // sendFailure enveloppe le message : un composant vide, rouge, avec le vrai message en enfant.
        if (!(message.getContents() instanceof TranslatableContents) && !message.getSiblings().isEmpty()) {
            return lire(message.getSiblings().get(0));
        }
        if (message.getContents() instanceof TranslatableContents t) {
            return new Recu(t.getKey(), Arrays.stream(t.getArgs()).map(E2eReparation::argument).toList(),
                    message.getString());
        }
        return new Recu("", List.of(), message.getString());
    }

    private static String argument(Object a) {
        if (a instanceof Component c) {
            return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
        }
        return String.valueOf(a);
    }

    // ================================================================================================
    // Déroulement
    // ================================================================================================

    /** Avance d'un pas ; vrai quand la séquence est finie. Un échec fait passer {@link E2eClient} à l'arrêt. */
    static boolean avancer(Minecraft minecraft) {
        mc = minecraft;
        if (index >= PAS.size()) {
            return true;
        }
        Pas p = PAS.get(index);
        if (!actionFaite) {
            LOG.info("[e2e] pas : {}", p.legende());
            p.action().run();
            actionFaite = true;
            debut = System.nanoTime();
            return false;
        }
        if (p.pret().getAsBoolean()) {
            if (p.succes() != null) {
                E2eClient.pass(p.succes().get());
            }
            index++;
            actionFaite = false;
        } else if ((System.nanoTime() - debut) / 1e9 > DELAI) {
            E2eClient.fail(p.legende() + " : " + p.echec().get() + " ; recus : " + recus());
        }
        return false;
    }

    private static double ecoule() {
        return (System.nanoTime() - debut) / 1e9;
    }

    private static String recus() {
        synchronized (RECUS) {
            return RECUS.toString();
        }
    }

    private static void pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec,
            Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes));
    }

    /** Un contrôle immédiat : passe ou attend jusqu'au délai, puis échoue avec le détail. */
    private static void controle(String legende, BooleanSupplier ok, Supplier<String> detail) {
        pas(legende, () -> {}, ok, detail, detail);
    }

    private static void commande(String texte) {
        LOG.info("[e2e] commande : /{}", texte);
        mc.player.connection.sendCommand(texte);
    }

    private static String nom() {
        return mc.player.getGameProfile().getName();
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static void vider() {
        synchronized (RECUS) {
            RECUS.clear();
        }
    }

    private static Recu trouve(String cle) {
        synchronized (RECUS) {
            for (Recu r : RECUS) {
                if (r.cle().equals(cle)) {
                    return r;
                }
            }
        }
        return null;
    }

    private static void clic(BlockPos pos) {
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
        LOG.info("[e2e] clic droit en {}, main {} / {} : {}", pos, mc.player.getMainHandItem(),
                mc.player.getOffhandItem(), res);
    }

    private static void tenir(int fente) {
        mc.player.getInventory().selected = fente;
    }

    /** Le rang de l'établi qui porte un état d'usure (tag du block entity) dans l'inventaire, ou -1. */
    private static int slotEtabliUse() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(BenchRegistry.ENGINEER_BENCH_ITEM.get()) && s.getTagElement("BlockEntityTag") != null) {
                return i;
            }
        }
        return -1;
    }

    private static MachineState etat(BlockPos pos) {
        var s = mc.level.getBlockState(pos);
        return s.hasProperty(ElectricMachineBlock.STATE) ? s.getValue(ElectricMachineBlock.STATE) : null;
    }

    private static int batterie() {
        return WelderEnergy.stored(mc.player.getInventory().getItem(FENTE_POSTE));
    }

    private static ItemStack mainSecondaire() {
        return mc.player.getOffhandItem();
    }

    // ================================================================================================
    // Briques du scénario
    // ================================================================================================

    /** Une commande dont on attend le message de cette clé. */
    private static void commandeAttendue(String legende, Supplier<String> texte, String cle) {
        pas(legende, () -> {
            vider();
            commande(texte.get());
        }, () -> trouve(cle) != null || (cle.equals(DATA_MODIFIE) && rienAChanger()), () -> "pas de message " + cle, null);
    }

    /** {@code /data merge} sur une valeur déjà en place (la pièce annoncée est tirée au sort) : une erreur sans clé. */
    private static boolean rienAChanger() {
        synchronized (RECUS) {
            return RECUS.stream().anyMatch(r -> r.texte().startsWith("Nothing changed"));
        }
    }

    /** Remplace le contenu de la main secondaire par une vraie commande, et attend de le voir. */
    private static void mainSecondaire(String legende, String objet) {
        pas(legende, () -> commande("item replace entity " + nom() + " weapon.offhand with " + objet),
                () -> objet.equals("minecraft:air") ? mainSecondaire().isEmpty()
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mainSecondaire().getItem()).toString().equals(objet),
                () -> "main secondaire : " + mainSecondaire(), null);
    }

    /**
     * Le testeur en main sur une machine : le rapport attendu, ligne par ligne, et aucun écran ouvert une
     * seconde après la dernière ligne. {@code panne} et {@code piece} : clés attendues, ou {@code null} si
     * la ligne ne doit pas exister.
     */
    private static void rapport(String contexte, Supplier<BlockPos> machine, String etatPct, String aspect, String panne,
            String piece, boolean protegee) {
        pas("Testeur : " + contexte, () -> {
            vider();
            tenir(FENTE_TESTEUR);
            clic(machine.get());
        }, () -> trouve(P_RAPPORT + "protected") != null || trouve(P_RAPPORT + "exposed") != null,
                () -> "rapport incomplet", null);
        pas("Testeur : " + contexte + " (aucun écran)", () -> {}, () -> ecoule() >= 1.0, () -> "", null);
        Supplier<String> lecture = () -> "testeur, " + contexte + " : etat " + arg(P_RAPPORT + "condition") + " %, aspect "
                + arg(P_RAPPORT + "visible") + ", energie " + arg(P_RAPPORT + "energy") + ", panne " + arg(P_RAPPORT + "fault")
                + ", piece " + arg(P_RAPPORT + "part") + ", protegee " + (trouve(P_RAPPORT + "protected") != null)
                + ", ecran " + mc.screen;
        controle("Testeur : " + contexte + " (lecture)", () -> mc.screen == null
                && etatPct.equals(arg(P_RAPPORT + "condition"))
                && (P_RAPPORT + "visible." + aspect).equals(arg(P_RAPPORT + "visible"))
                && Objects.equals(panne, arg(P_RAPPORT + "fault"))
                && Objects.equals(piece, arg(P_RAPPORT + "part"))
                && (trouve(P_RAPPORT + "protected") != null) == protegee
                && (trouve(P_RAPPORT + "exposed") != null) != protegee, lecture);
    }

    /** Le premier argument du message de cette clé, ou {@code null} s'il n'est pas arrivé. */
    private static String arg(String cle) {
        Recu r = trouve(cle);
        return r == null || r.args().isEmpty() ? null : r.args().get(0);
    }

    /** Le poste en main principale, debout, sur une machine : on attend le message d'issue. */
    private static void souder(String legende, Supplier<BlockPos> machine, String cle) {
        pas(legende, () -> {
            vider();
            tenir(FENTE_POSTE);
            clic(machine.get());
        }, () -> trouve(cle) != null, () -> "pas de message " + cle, null);
    }

    private static void poser(String legende, Supplier<BlockPos> ou) {
        pas(legende, () -> {
            tenir(FENTE_ETABLI);
            BlockPos sol = ou.get().below();
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false));
        }, () -> mc.level.getBlockState(ou.get()).is(BenchRegistry.ENGINEER_BENCH.get()),
                () -> "etabli absent en " + ou.get(), null);
    }

    private static void verifierCote(String legende, Supplier<BlockPos> ou, String attendu) {
        pas(legende, () -> {
            vider();
            commande("coast check " + xyz(ou.get()));
        }, () -> trouve(P_COTE + "check." + attendu) != null, () -> "reponse attendue : " + attendu,
                () -> "/coast check " + ou.get().toShortString() + " : " + attendu + " (" + derniereReponseCote() + ")");
    }

    private static String derniereReponseCote() {
        Recu oui = trouve(P_COTE + "check.yes");
        Recu non = trouve(P_COTE + "check.no");
        return oui != null ? oui.texte() : non != null ? non.texte() : "(aucune)";
    }

    // ================================================================================================
    // Première phase : réparation, /coast, puis machines et zone laissées pour la relance
    // ================================================================================================

    static void premierePhase() {
        pas("Aire d'essai et outils par de vraies commandes", () -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            aire = mc.player.blockPosition();
            m1 = aire.offset(3, 0, 0);
            m2 = aire.offset(0, 0, 3);
            m3 = aire.offset(-3, 0, 0);
            commande("fill " + xyz(aire.offset(-6, 0, -6)) + " " + xyz(aire.offset(6, 4, 6)) + " air");
            commande("fill " + xyz(aire.offset(-6, -1, -6)) + " " + xyz(aire.offset(6, -1, 6)) + " stone");
            // Le joueur du monde d'essai peut être resté en créatif, où le poste ne consomme pas la pièce.
            commande("gamemode survival " + nom());
            commande("clear " + nom());
            commande("item replace entity " + nom() + " hotbar." + FENTE_ETABLI + " with oneforallsurvival:engineer_bench 3");
            commande("item replace entity " + nom() + " hotbar." + FENTE_TESTEUR + " with oneforallsurvival:tester");
            commande("item replace entity " + nom() + " hotbar." + FENTE_POSTE + " with oneforallsurvival:welder");
        }, () -> mc.level.getBlockState(aire.below()).is(Blocks.STONE)
                && mc.player.getInventory().getItem(FENTE_ETABLI).is(BenchRegistry.ENGINEER_BENCH_ITEM.get())
                && mc.player.getInventory().getItem(FENTE_TESTEUR).is(RepairRegistry.TESTER.get())
                && mc.player.getInventory().getItem(FENTE_POSTE).is(RepairRegistry.WELDER.get()),
                () -> "aire ou outils absents", () -> "/gamemode survival, /fill et /item replace : aire d'essai en " + aire + ", testeur et poste recus");
        poser("Pose de la machine 1 par un clic droit", () -> m1);
        poser("Pose de la machine 2 par un clic droit", () -> m2);
        poser("Pose de la machine 3 par un clic droit", () -> m3);

        // Témoin : sans le testeur, le clic droit ouvre bien l'écran. Sans cela, « aucun écran » ne prouverait rien.
        pas("Témoin : main vide sur l'établi, l'écran s'ouvre", () -> {
            tenir(FENTE_VIDE);
            clic(m1);
        }, () -> mc.screen instanceof EngineerBenchScreen, () -> "ecran : " + mc.screen,
                () -> "temoin : main vide sur l'etabli, l'ecran s'ouvre");
        pas("Fermeture de l'écran témoin", () -> mc.player.closeContainer(), () -> mc.screen == null, () -> "ecran : " + mc.screen, null);

        rapport("machine neuve", () -> m1, "100", "good", null, null, false);

        commandeAttendue("/data merge : pièce annoncée", () -> "data merge block " + xyz(m1) + " {next_fault:\"FUSE\"}",
                DATA_MODIFIE);
        mainSecondaire("La bonne pièce en main secondaire", "oneforallsurvival:fuse");
        souder("Poste et pièce sur une machine neuve : rien à faire", () -> m1, P_REPARE + "nothing_to_do");
        controle("Rien à faire : pièce gardée", () -> mainSecondaire().getCount() == 1 && mc.screen == null,
                () -> "machine neuve : poste refuse (nothing_to_do), piece gardee (" + mainSecondaire() + "), aucun ecran");

        commandeAttendue("/data merge : état 15 %", () -> "data merge block " + xyz(m1) + " {condition:15.0d}",
                DATA_MODIFIE);
        rapport("machine usee, condition posee a 15 par /data", () -> m1, "15", "smoking", null, "item.oneforallsurvival.fuse", false);

        commandeAttendue("/data merge : panne de fusible",
                () -> "data merge block " + xyz(m1) + " {condition:0.0d,fault_type:\"FUSE\"}", DATA_MODIFIE);
        pas("La panne arrive au client par l'état du bloc", () -> {}, () -> etat(m1) == MachineState.FAULT,
                () -> "etat du bloc : " + etat(m1), () -> "etat du bloc lu par le client : FAULT");
        rapport("machine en panne", () -> m1, "0", "fault", "fault.oneforallsurvival.fuse", "item.oneforallsurvival.fuse", false);

        // Casser puis reposer ne répare pas : le serveur lâche l'objet usé (/setblock destroy suit la vraie loot table),
        // le joueur le ramasse et le repose par un vrai clic droit, et le testeur relit la même panne.
        pas("Casse de la machine en panne par le serveur", () -> {
            commande("setblock " + xyz(m1) + " minecraft:air destroy");
        }, () -> mc.level.getBlockState(m1).isAir(), () -> "bloc en " + m1 + " : " + mc.level.getBlockState(m1), null);
        pas("L'objet lâché porte l'état et rejoint l'inventaire", () -> commande("tp @e[type=minecraft:item,distance=..10] @s"),
                () -> slotEtabliUse() >= 0, () -> "inventaire sans etabli portant son etat", () -> "etabli use ramasse, "
                        + mc.player.getInventory().getItem(slotEtabliUse()).getTag());
        pas("Repose de l'objet usé par un clic droit", () -> {
            int slot = slotEtabliUse();
            if (slot >= 0 && slot < 9) {
                tenir(slot);
            }
            BlockPos sol = m1.below();
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false));
        }, () -> mc.level.getBlockState(m1).is(BenchRegistry.ENGINEER_BENCH.get()) && etat(m1) == MachineState.FAULT,
                () -> "bloc en " + m1 + " : " + mc.level.getBlockState(m1), () -> "etabli repose en panne, etat du bloc " + etat(m1));
        rapport("machine reposee apres casse : toujours en panne", () -> m1, "0", "fault", "fault.oneforallsurvival.fuse",
                "item.oneforallsurvival.fuse", false);

        mainSecondaire("Une mauvaise pièce en main secondaire", "oneforallsurvival:coil");
        souder("Mauvaise pièce : refusée", () -> m1, P_REPARE + "wrong_part");
        controle("Mauvaise pièce : gardée, machine en panne", () -> mainSecondaire().is(RepairRegistry.COIL.get())
                && mainSecondaire().getCount() == 1 && etat(m1) == MachineState.FAULT && mc.screen == null
                && "item.oneforallsurvival.fuse".equals(arg(P_REPARE + "wrong_part")),
                () -> "mauvaise piece refusee, le message demande " + arg(P_REPARE + "wrong_part") + ", piece gardee, etat " + etat(m1)
                        + ", aucun ecran");

        mainSecondaire("Main secondaire vide", "minecraft:air");
        souder("Sans pièce : refusé", () -> m1, P_REPARE + "no_part");
        mainSecondaire("La bonne pièce, poste vide", "oneforallsurvival:fuse");
        souder("Poste vide : refusé", () -> m1, P_REPARE + "no_energy");
        controle("Poste vide : pièce gardée", () -> mainSecondaire().getCount() == 1 && etat(m1) == MachineState.FAULT,
                () -> "sans piece : no_part ; poste vide (" + batterie() + " FE) : no_energy, piece gardee, etat " + etat(m1));

        commandeAttendue("/data merge : réserve de la machine", () -> "data merge block " + xyz(m1) + " {energy:1000}",
                DATA_MODIFIE);
        pas("Accroupi", () -> {
            tenir(FENTE_POSTE);
            mc.options.keyShift.setDown(true);
        }, () -> mc.player.isShiftKeyDown() && ecoule() > 0.5, () -> "le joueur ne s'accroupit pas", null);
        pas("Recharge du poste, accroupi", () -> {
            vider();
            clic(m1);
        }, () -> trouve("message.oneforallsurvival.welder.charged") != null && batterie() > 0,
                () -> "batterie " + batterie(), () -> "accroupi, poste sur la machine : recharge, batterie " + batterie()
                        + " FE, message " + trouve("message.oneforallsurvival.welder.charged").texte());
        pas("Debout", () -> mc.options.keyShift.setDown(false), () -> !mc.player.isShiftKeyDown() && ecoule() > 0.5,
                () -> "le joueur reste accroupi", null);

        souder("Bonne pièce : réparation", () -> m1, P_REPARE + "repaired");
        pas("Réparation : pièce consommée, panne levée", () -> {},
                () -> mainSecondaire().isEmpty() && batterie() == 600 && etat(m1) != MachineState.FAULT && mc.screen == null,
                () -> "main secondaire " + mainSecondaire() + ", batterie " + batterie() + ", etat " + etat(m1),
                () -> "poste en main principale et fusible en main secondaire par useItemOn : machine reparee, fusible consomme, "
                        + "batterie 1000 -> " + batterie() + " FE, etat " + etat(m1) + ", aucun ecran");
        rapport("machine reparee", () -> m1, "100", "good", null, null, false);

        commandeAttendue("/data merge : pièce annoncée", () -> "data merge block " + xyz(m1) + " {next_fault:\"BEARING\"}",
                DATA_MODIFIE);
        mainSecondaire("Une pièce traitée en main secondaire", "oneforallsurvival:treated_bearing");
        souder("Pièce traitée : la machine est protégée", () -> m1, P_REPARE + "repaired");
        rapport("machine protegee par une piece traitee", () -> m1, "100", "good", null, null, true);

        // /coast, par de vraies commandes. Une zone déclarée dans la dimension remplace la détection de l'eau.
        pas("Nettoyage de zones d'un essai interrompu", () -> {
            vider();
            commande("coast zone remove " + ZONE);
            commande("coast zone remove " + ZONE_GARDEE);
            commande("coast zone remove e2e_vivres");
            commande("coast zone list");
        }, () -> trouve(P_COTE + "zone.list.empty") != null || trouve(P_COTE + "zone.list.header") != null,
                () -> "pas de reponse a /coast zone list", null);
        pas("/coast check avant toute zone", () -> {
            vider();
            commande("coast check " + xyz(aire));
        }, () -> trouve(P_COTE + "check.yes") != null || trouve(P_COTE + "check.no") != null, () -> "pas de reponse", () -> {
            coteAvant = trouve(P_COTE + "check.yes") != null ? "yes" : "no";
            return "/coast check sans zone (detection de l'eau) : " + coteAvant + " (" + derniereReponseCote() + ")";
        });
        commandeAttendue("/coast zone add", () -> "coast zone add " + ZONE + " " + xyz(aire.offset(-2, -2, -2)) + " " + xyz(aire.offset(2, 2, 2)),
                P_COTE + "zone.added");
        commandeAttendue("/coast zone add, même identifiant : refus",
                () -> "coast zone add " + ZONE + " " + xyz(aire) + " " + xyz(aire), P_COTE + "zone.duplicate");
        commandeAttendue("/coast zone list", () -> "coast zone list", P_COTE + "zone.list.entry");
        controle("/coast zone list : la zone y est", () -> ZONE.equals(arg(P_COTE + "zone.list.entry")),
                () -> "/coast zone add puis list : " + (trouve(P_COTE + "zone.list.entry") == null ? "-" : trouve(P_COTE + "zone.list.entry").texte())
                        + " ; second ajout du meme identifiant refuse");
        verifierCote("/coast check dans la zone", () -> aire, "yes");
        verifierCote("/coast check hors de la zone", () -> aire.offset(20, 0, 0), "no");
        commandeAttendue("/coast zone remove", () -> "coast zone remove " + ZONE, P_COTE + "zone.removed");
        pas("/coast check après le retrait", () -> {
            vider();
            commande("coast check " + xyz(aire));
        }, () -> trouve(P_COTE + "check." + coteAvant) != null, () -> "attendu : " + coteAvant,
                () -> "/coast zone remove puis check : de nouveau " + coteAvant + " (" + derniereReponseCote() + ")");
        commandeAttendue("/coast zone list après le retrait", () -> "coast zone list", P_COTE + "zone.list.empty");
        commandeAttendue("/coast zone remove d'une zone inconnue", () -> "coast zone remove " + ZONE, P_COTE + "zone.unknown");

        // Ce que la relance devra retrouver : usure, panne, protection (m1) et une zone.
        commandeAttendue("/data merge : machine 3 usée", () -> "data merge block " + xyz(m3) + " {condition:37.0d,next_fault:\"FOULING\"}",
                DATA_MODIFIE);
        commandeAttendue("/data merge : machine 2 en panne",
                () -> "data merge block " + xyz(m2) + " {condition:0.0d,fault_type:\"WINDING\"}", DATA_MODIFIE);
        pas("Machine 2 : panne au client", () -> {}, () -> etat(m2) == MachineState.FAULT, () -> "etat " + etat(m2), null);
        rapport("avant arret, machine 3 usee", () -> m3, "37", "tired", null, "item.oneforallsurvival.cleaning_kit", false);
        rapport("avant arret, machine 2 en panne", () -> m2, "0", "fault", "fault.oneforallsurvival.winding", "item.oneforallsurvival.coil", false);
        commandeAttendue("/coast zone add, zone gardée", () -> "coast zone add " + ZONE_GARDEE + " " + xyz(m1) + " " + xyz(m1), P_COTE + "zone.added");
        verifierCote("/coast check sur la zone gardée", () -> m1, "yes");
        pas("Positions écrites pour la relance", () -> {
            try {
                Files.writeString(fichier(), xyz(m1) + "\n" + xyz(m2) + "\n" + xyz(m3) + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, () -> true, () -> "", () -> "a retrouver apres relance : machine 1 protegee " + m1.toShortString() + ", machine 2 en panne "
                + m2.toShortString() + ", machine 3 a 37 % " + m3.toShortString() + ", zone " + ZONE_GARDEE + ", batterie " + batterie());
    }

    private static Path fichier() {
        return mc.gameDirectory.toPath().resolve(FICHIER);
    }

    // ================================================================================================
    // Après un arrêt et une relance du serveur
    // ================================================================================================

    static void relance() {
        pas("Relecture des positions", () -> {
            try {
                List<String> lignes = Files.readAllLines(fichier(), StandardCharsets.UTF_8);
                m1 = lire(lignes.get(0));
                m2 = lire(lignes.get(1));
                m3 = lire(lignes.get(2));
            } catch (IOException e) {
                throw new IllegalStateException("premiere phase absente : " + fichier(), e);
            }
        }, () -> mc.level.getBlockState(m1).is(BenchRegistry.ENGINEER_BENCH.get())
                && mc.level.getBlockState(m2).is(BenchRegistry.ENGINEER_BENCH.get())
                && mc.level.getBlockState(m3).is(BenchRegistry.ENGINEER_BENCH.get()),
                () -> "machines absentes", () -> "relance : les trois machines sont la, position du joueur " + mc.player.blockPosition());
        pas("L'état du bloc en panne est relu du disque", () -> {}, () -> etat(m2) == MachineState.FAULT,
                () -> "etat " + etat(m2), () -> "relance : machine 2 FAULT dans l'etat du bloc");
        controle("Le poste a gardé sa charge", () -> batterie() == 200,
                () -> "relance : batterie du poste dans l'inventaire : " + batterie() + " FE (200 attendus)");
        rapport("relance, machine 1 protegee", () -> m1, "100", "good", null, null, true);
        rapport("relance, machine 2 en panne", () -> m2, "0", "fault", "fault.oneforallsurvival.winding", "item.oneforallsurvival.coil", false);
        rapport("relance, machine 3 usee", () -> m3, "37", "tired", null, "item.oneforallsurvival.cleaning_kit", false);
        commandeAttendue("/coast zone list après la relance", () -> "coast zone list", P_COTE + "zone.list.entry");
        controle("La zone a survécu", () -> ZONE_GARDEE.equals(arg(P_COTE + "zone.list.entry")),
                () -> "relance : /coast zone list : " + trouve(P_COTE + "zone.list.entry").texte());
        verifierCote("/coast check dans la zone gardée", () -> m1, "yes");
        verifierCote("/coast check hors de la zone gardée", () -> m3, "no");
        // Rangement : l'essai suivant repart d'un monde sans zone.
        commandeAttendue("/coast zone remove, zone gardée", () -> "coast zone remove " + ZONE_GARDEE, P_COTE + "zone.removed");
        pas("Retrait des machines d'essai", () -> {
            commande("setblock " + xyz(m1) + " air");
            commande("setblock " + xyz(m2) + " air");
            commande("setblock " + xyz(m3) + " air");
        }, () -> mc.level.getBlockState(m1).isAir() && mc.level.getBlockState(m2).isAir() && mc.level.getBlockState(m3).isAir(),
                () -> "machines encore la", null);
    }

    private static BlockPos lire(String ligne) {
        String[] t = ligne.trim().split(" ");
        return new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]));
    }
}
