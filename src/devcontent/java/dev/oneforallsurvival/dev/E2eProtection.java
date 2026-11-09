package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.fence.FenceRegistry;
import dev.oneforallsurvival.food.CanteenPotMenu;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.HotPlateBlock;
import dev.oneforallsurvival.food.IncubatorMenu;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;

/**
 * Étape « vivres et protection » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot 5 vu par un
 * vrai client contre un serveur dédié, avec le pack complet et First Aid. La couveuse (éclosion, coupure courte sans perte,
 * coupure longue, compteur de coupure relu après un vrai arrêt du serveur), la plaque électrique sous un Cooking Pot, la
 * marmite de cantine, la clôture électrique contre un zombie, puis contre un joueur qui n'est pas opérateur.
 *
 * <p><b>La preuve qui compte</b> : {@code ClotureGameTests} saute ses deux essais de plancher de vie sous First Aid, le
 * joueur factice des GameTests n'y étant pas un vrai joueur. Cette étape est donc la seule preuve du plancher de vie
 * pour un vrai joueur : opérateur retiré (console RCON), vie abaissée par {@code /damage}, puis le joueur pousse contre la
 * clôture plusieurs fois. À chaque choc sont consignés, côté client et côté serveur, les points de vie vanilla et les
 * membres de First Aid ({@code ForgeCaps."firstaid:cap_adv_dmg_mdl"}).
 *
 * <p>Ce qui est abrégé, dit dans les lignes {@code PASS} : la progression de l'œuf de la première couveuse est avancée à
 * {@value #RESTE_ECLOSION} ticks de l'éclosion par {@code /data merge} (vingt minutes réelles sinon). La coupure longue,
 * elle, est attendue en temps réel (60 s). Le temps du monde n'est jamais accéléré.
 *
 * <p>L'aire est une dalle à 70 blocs à l'ouest du joueur, en hauteur, hors du point d'apparition. Chaque pas qui échoue est
 * consigné (ligne {@code FAIL}) puis la séquence continue, sauf pour les pas dont la suite dépend.
 */
final class E2eProtection {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-protection.txt";
    private static final double DELAI = 25;
    private static final int ECHECS_MAX = 8;
    private static final int Y_DALLE = 150;
    /** Ticks qu'il reste à l'œuf avancé pour éclore (6 s). */
    private static final int RESTE_ECLOSION = 120;
    private static final String CREATIVE = "createaddition:creative_energy";
    private static final String POT = "farmersdelight:cooking_pot";
    private static final String CLE_FA = "firstaid:cap_adv_dmg_mdl";
    /** Le plancher de vie de la clôture pour un joueur (config {@code safeHealth}, 4 points par défaut). */
    private static final double PLANCHER = 4.0;
    private static final Pattern PARTIE = Pattern.compile("([A-Za-z]+): [{]health: (-?[0-9.]+)f?");

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai, boolean critique) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;
    private static int echecsDuPas;

    private static Minecraft mc;
    private static BlockPos depart;
    private static BlockPos o;
    private static BlockPos incA;
    private static BlockPos srcA;
    private static BlockPos incB;
    private static BlockPos srcB;
    private static BlockPos plaque;
    private static BlockPos marmite;
    private static BlockPos srcPlaque;
    private static BlockPos cantine;
    private static BlockPos srcCantine;
    private static BlockPos elec1;
    private static BlockPos src1;
    private static BlockPos elec2;
    private static BlockPos src2;

    // Ce que les pas se transmettent.
    private static int progAvant;
    private static int coupureMax;
    private static int stewsAvant;
    /** Les ragoûts que le joueur avait avant l'étape (celui des vivres, relu à la relance) : l'étape n'en retire pas un de plus. */
    private static int ragoutsAvantEtape;
    private static double vieZombieMin;
    private static Vec3 departZombie;
    private static boolean zombieVu;
    private static boolean zombieMort;
    private static double derniereVieZombie = 20;
    private static double zombiePlusProche;
    private static double zombieRepousse;
    private static int essaisZombie;
    private static double vieZombie2Min;
    private static int coups;
    private static double zPrecedent;
    private static double vieJoueurAvant;
    private static double vieJoueurMin;
    private static boolean joueurMort;
    private static String faAvant = "";
    private static String faApres = "";
    private static double sommeFaAvant;
    private static double sommeFaApres;
    private static long derniereLecture;
    private static int degats;
    private static double teteAvant;
    private static double corpsAvant;
    private static long dernierDegat;
    private static int outageAvantArret;

    private E2eProtection() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape";
    }

    static void recevoir(Component message) {
        // Les réponses utiles passent par la console RCON, pas par le chat.
    }

    // ================================================================================================
    // Déroulement
    // ================================================================================================

    /** Avance d'un pas ; vrai quand la séquence est finie. */
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
        } else if (ecoule() > p.delai()) {
            String detail = p.legende() + " : " + p.echec().get();
            if (p.critique()) {
                E2eClient.fail(detail);
            } else {
                E2eClient.failSoft(detail);
                echecsDuPas++;
                index++;
                actionFaite = false;
                if (echecsDuPas >= ECHECS_MAX) {
                    E2eClient.fail("trop d'echecs dans l'etape de la protection : arret");
                }
            }
        }
        return false;
    }

    private static double ecoule() {
        return (System.nanoTime() - debut) / 1e9;
    }

    private static void pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, DELAI, false));
    }

    private static void pasD(double delai, String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec,
            Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, delai, false));
    }

    private static void pasC(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, DELAI, true));
    }

    private static void pasCD(double delai, String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec,
            Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, delai, true));
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

    private static void clic(BlockPos pos) {
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
        LOG.info("[e2e] clic droit en {}, main {} : {}", pos, mc.player.getMainHandItem(), res);
    }

    private static void tenirVide() {
        for (int i = 8; i >= 0; i--) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        mc.player.getInventory().selected = 8;
    }

    private static MachineState etat(BlockPos pos) {
        var s = mc.level.getBlockState(pos);
        return s.hasProperty(ElectricMachineBlock.STATE) ? s.getValue(ElectricMachineBlock.STATE) : null;
    }

    private static Block bloc(String id) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
    }

    private static Item objet(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
    }

    private static int compter(Predicate<ItemStack> p) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (p.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static Predicate<ItemStack> est(Item item) {
        return s -> s.is(item);
    }

    private static boolean operateur() {
        return mc.player.hasPermissions(2);
    }

    // ---------------------------------------------------------------------------------------------- menus

    private static AbstractContainerMenu menu() {
        return mc.player.containerMenu;
    }

    private static boolean ouvert(Class<? extends AbstractContainerMenu> type) {
        return type.isInstance(menu()) && mc.screen instanceof AbstractContainerScreen<?>;
    }

    private static int fenteMenu(Predicate<ItemStack> p, boolean joueur) {
        for (Slot s : menu().slots) {
            if ((s.container == mc.player.getInventory()) == joueur && p.test(s.getItem())) {
                return s.index;
            }
        }
        return -1;
    }

    private static void deplacer(int fente) {
        if (fente < 0) {
            throw new IllegalStateException("case absente du menu " + menu().getClass().getSimpleName());
        }
        LOG.info("[e2e] clic majuscule sur la case {} : {}", fente, menu().slots.get(fente).getItem());
        mc.gameMode.handleInventoryMouseClick(menu().containerId, fente, 0, ClickType.QUICK_MOVE, mc.player);
    }

    private static void ouvrir(String legende, Supplier<BlockPos> ou, Class<? extends AbstractContainerMenu> type) {
        pasC(legende, () -> {
            tenirVide();
            clic(ou.get());
        }, () -> ouvert(type), () -> "menu " + type.getSimpleName() + " non ouvert, menu " + menu().getClass().getSimpleName()
                + ", ecran " + mc.screen, () -> "menu " + type.getSimpleName() + " ouvert par un vrai clic droit en " + ou.get().toShortString());
    }

    private static void fermer(String legende) {
        pas(legende, () -> mc.player.closeContainer(), () -> mc.screen == null && menu() == mc.player.inventoryMenu,
                () -> "menu encore ouvert : " + menu().getClass().getSimpleName(), null);
    }

    /** Le joueur (opérateur) est placé à {@code distance} blocs au nord de {@code cible}, le regard vers le sud. */
    private static void poste(BlockPos cible) {
        commande("tp " + nom() + " " + (cible.getX() + 0.5) + " " + o.getY() + " " + (cible.getZ() - 2.5) + " 0 20");
    }

    private static boolean pres(BlockPos cible) {
        return Math.abs(mc.player.getX() - (cible.getX() + 0.5)) < 1.0 && Math.abs(mc.player.getZ() - (cible.getZ() - 2.5)) < 1.0;
    }

    private static void allerA(String legende, Supplier<BlockPos> cible) {
        pasC(legende, () -> poste(cible.get()), () -> pres(cible.get()) && ecoule() >= 0.5, () -> "joueur en " + mc.player.blockPosition(), null);
    }

    // ---------------------------------------------------------------------------------------------- RCON, vie, First Aid

    private static String rcon(String commandeConsole) {
        return E2eReseau.rcon(commandeConsole);
    }

    /** La vie vanilla du serveur, ou -1. */
    private static double vieServeur() {
        Matcher m = Pattern.compile("(-?[0-9.]+)f").matcher(rcon("data get entity " + nom() + " Health"));
        return m.find() ? Double.parseDouble(m.group(1)) : -1;
    }

    /** Le modèle de dégâts de First Aid tel que le serveur l'écrit. */
    private static String modeleFirstAid() {
        return rcon("data get entity " + nom() + " ForgeCaps.\"" + CLE_FA + "\"");
    }

    private static double somme(String modele) {
        Matcher m = PARTIE.matcher(modele);
        double s = 0;
        while (m.find()) {
            s += Double.parseDouble(m.group(2));
        }
        return s;
    }

    /** Les membres de First Aid lus dans le modèle du serveur : « head=4.0 », « body=6.0 »... */
    private static List<String> parties(String modele) {
        Matcher m = PARTIE.matcher(modele);
        List<String> l = new ArrayList<>();
        while (m.find()) {
            l.add(m.group(1) + "=" + m.group(2));
        }
        return l;
    }

    /** La valeur d'un membre (head, body...) dans le modèle, ou -1. */
    private static double membre(String modele, String nom) {
        Matcher m = PARTIE.matcher(modele);
        while (m.find()) {
            if (m.group(1).equals(nom)) {
                return Double.parseDouble(m.group(2));
            }
        }
        return -1;
    }

    /** Une ligne de relevé : vie vue du client, vie du serveur, membres de First Aid. */
    private static String releve() {
        String fa = modeleFirstAid();
        return "vie client " + mc.player.getHealth() + " | vie serveur " + vieServeur() + " | First Aid membres " + parties(fa)
                + " (somme " + somme(fa) + ") | vivant " + mc.player.isAlive();
    }

    // ================================================================================================
    // Première phase
    // ================================================================================================

    static void premierePhase() {
        preparation();
        couveuseEclosion();
        couveuseCoupureCourte();
        couveuseCoupureLongue();
        plaque();
        cantine();
        zombie();
        joueur();
        couveusePersistance();
    }

    private static void placer(BlockPos origine) {
        o = origine;
        incA = o;
        srcA = o.offset(-1, 0, 0);
        incB = o.offset(4, 0, 0);
        srcB = o.offset(3, 0, 0);
        srcPlaque = o.offset(7, 0, 0);
        plaque = o.offset(8, 0, 0);
        marmite = o.offset(8, 1, 0);
        srcCantine = o.offset(11, 0, 0);
        cantine = o.offset(12, 0, 0);
        elec1 = o.offset(0, 0, 8);
        src1 = o.offset(-1, 0, 8);
        elec2 = o.offset(18, 0, 8);
        src2 = o.offset(17, 0, 8);
    }

    private static void preparation() {
        pasC("Aire du lot 5 sur une dalle, règles du monde et vivres par de vraies commandes", () -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            if (bloc(CREATIVE) == Blocks.AIR || bloc(POT) == Blocks.AIR) {
                throw new IllegalStateException("source creative ou Cooking Pot absents : lancer avec -PwithPack");
            }
            depart = mc.player.blockPosition();
            ragoutsAvantEtape = compter(est(objet("farmersdelight:beef_stew")));
            BlockPos spawn = mc.level.getSharedSpawnPos();
            int x = depart.getX() - 70;
            if (Math.abs(x - spawn.getX()) <= 40 && Math.abs(depart.getZ() - spawn.getZ()) <= 40) {
                x = depart.getX() + 160;
            }
            placer(new BlockPos(x, Y_DALLE, depart.getZ()));
            commande("difficulty easy");
            commande("gamerule doMobSpawning false");
            commande("gamerule naturalRegeneration false");
            commande("fill " + xyz(o.offset(-4, 0, -6)) + " " + xyz(o.offset(26, 6, 14)) + " air");
            commande("fill " + xyz(o.offset(-4, -1, -6)) + " " + xyz(o.offset(26, -1, 14)) + " stone");
            commande("gamemode survival " + nom());
            commande("tp " + nom() + " " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() - 2.5) + " 0 20");
        }, () -> etat0Dalle() && mc.player.blockPosition().getY() >= Y_DALLE - 1,
                () -> "joueur en " + mc.player.blockPosition() + ", dalle " + mc.level.getBlockState(o.below()),
                () -> "difficulty easy, gamerule (naissances et regeneration naturelle coupees), /fill, /tp, /give : aire en " + o.toShortString()
                        + " (joueur parti de " + depart.toShortString() + ", spawn " + mc.level.getSharedSpawnPos().toShortString() + ")");
        pasC("Machines et sources de courant posées par /setblock", () -> {
            commande("setblock " + xyz(incA) + " oneforallsurvival:incubator");
            commande("setblock " + xyz(srcA) + " " + CREATIVE);
            commande("setblock " + xyz(incB) + " oneforallsurvival:incubator");
            commande("setblock " + xyz(srcB) + " " + CREATIVE);
            commande("setblock " + xyz(plaque) + " oneforallsurvival:hot_plate");
            commande("setblock " + xyz(marmite) + " " + POT);
            commande("setblock " + xyz(srcPlaque) + " " + CREATIVE);
            commande("setblock " + xyz(cantine) + " oneforallsurvival:canteen_pot");
            commande("setblock " + xyz(srcCantine) + " " + CREATIVE);
            // La ligne du zombie : quatre clôtures vers l'est puis un coude de deux vers le sud ; celle du joueur : quatre clôtures.
            for (int i = 1; i <= 4; i++) {
                commande("setblock " + xyz(elec1.offset(i, 0, 0)) + " oneforallsurvival:electric_fence");
                commande("setblock " + xyz(elec2.offset(i, 0, 0)) + " oneforallsurvival:electric_fence");
            }
            commande("setblock " + xyz(elec1.offset(4, 0, 1)) + " oneforallsurvival:electric_fence");
            commande("setblock " + xyz(elec1.offset(4, 0, 2)) + " oneforallsurvival:electric_fence");
            commande("setblock " + xyz(elec1) + " oneforallsurvival:electrifier");
            commande("setblock " + xyz(src1) + " " + CREATIVE);
            commande("setblock " + xyz(elec2) + " oneforallsurvival:electrifier");
            commande("setblock " + xyz(src2) + " " + CREATIVE);
        }, () -> mc.level.getBlockState(incA).is(FoodRegistry.INCUBATOR.get()) && mc.level.getBlockState(incB).is(FoodRegistry.INCUBATOR.get())
                && mc.level.getBlockState(plaque).is(FoodRegistry.HOT_PLATE.get()) && mc.level.getBlockState(marmite).is(bloc(POT))
                && mc.level.getBlockState(cantine).is(FoodRegistry.CANTEEN_POT.get())
                && mc.level.getBlockState(elec1).is(FenceRegistry.ELECTRIFIER.get()) && mc.level.getBlockState(elec2).is(FenceRegistry.ELECTRIFIER.get())
                && mc.level.getBlockState(elec1.offset(4, 0, 2)).is(FenceRegistry.ELECTRIC_FENCE.get())
                && mc.level.getBlockState(src2).is(bloc(CREATIVE)),
                () -> "blocs absents : " + mc.level.getBlockState(incA) + " " + mc.level.getBlockState(plaque) + " " + mc.level.getBlockState(cantine)
                        + " " + mc.level.getBlockState(elec1),
                () -> "deux couveuses, une plaque sous un Cooking Pot, une marmite de cantine, deux electrificateurs avec leurs clotures "
                        + "(l'une en coude), chacun contre une source Create Crafts & Additions");
    }

    private static boolean etat0Dalle() {
        return mc.level.getBlockState(o.below()).is(Blocks.STONE);
    }

    /** Un objet donné en quantité exacte : la pile entière entre d'un coup par un clic majuscule. */
    private static void donner(String legende, Item item, int nombre) {
        pas(legende, () -> {
            commande("clear " + nom() + " " + BuiltInRegistries.ITEM.getKey(item));
            commande("give " + nom() + " " + BuiltInRegistries.ITEM.getKey(item) + " " + nombre);
        }, () -> compter(est(item)) == nombre, () -> BuiltInRegistries.ITEM.getKey(item) + " : " + compter(est(item)) + " au lieu de " + nombre, null);
    }

    // ================================================================================================
    // Couveuse
    // ================================================================================================

    private static IncubatorMenu couveuse() {
        return (IncubatorMenu) menu();
    }

    private static void couveuseEclosion() {
        allerA("Le joueur devant la première couveuse", () -> incA);
        ouvrir("Ouverture de la couveuse", () -> incA, IncubatorMenu.class);
        donner("Un œuf en main", Items.EGG, 1);
        pas("Un œuf entre dans la couveuse (clic majuscule)", () -> deplacer(fenteMenu(est(Items.EGG), true)),
                () -> couveuse().getSlot(0).getItem().is(Items.EGG), () -> "case 0 : " + couveuse().getSlot(0).getItem(), null);
        pasD(30, "La couveuse alimentée couve : l'état est en marche et la progression avance", () -> {},
                () -> etat(incA) == MachineState.RUNNING && couveuse().progress(0) > 20 && couveuse().energy() > 0,
                () -> "etat " + etat(incA) + ", progression " + couveuse().progress(0) + "/" + couveuse().total() + ", energie " + couveuse().energy(),
                () -> "couveuse alimentee par une source Create : etat RUNNING, progression " + couveuse().progress(0) + "/" + couveuse().total()
                        + " ticks (duree lue cote client, config synchronisee : " + couveuse().total() / 1200 + " min), energie " + couveuse().energy() + "/"
                        + couveuse().capacity());
        pas("Durée abrégée : la progression de l'œuf est avancée à " + RESTE_ECLOSION + " ticks de l'éclosion (/data merge)", () -> {
            int proche = couveuse().total() - RESTE_ECLOSION;
            commande("data merge block " + xyz(incA) + " {progress:[I;" + proche + ",0,0,0]}");
        }, () -> couveuse().progress(0) >= couveuse().total() - RESTE_ECLOSION,
                () -> "progression " + couveuse().progress(0) + "/" + couveuse().total(),
                () -> "progression de l'oeuf : " + couveuse().progress(0) + "/" + couveuse().total() + " (abregee par commande ; l'eclosion qui suit est reelle)");
        pasD(40, "L'œuf éclot : un poussin naît au-dessus de la couveuse, la case est vidée", () -> {},
                () -> poussin() != null && couveuse().getSlot(0).getItem().isEmpty(),
                () -> "poussins vus " + poussins() + ", case 0 " + couveuse().getSlot(0).getItem() + ", progression " + couveuse().progress(0),
                () -> {
                    Chicken c = poussin();
                    return "poussin vu par le client en " + c.blockPosition().toShortString() + " (bebe " + c.isBaby() + "), la case de l'oeuf est vide, "
                            + "etat de la couveuse " + etat(incA);
                });
    }

    private static List<Chicken> poussins() {
        return mc.level.getEntitiesOfClass(Chicken.class, new AABB(incA).inflate(5));
    }

    private static Chicken poussin() {
        for (Chicken c : poussins()) {
            if (c.isBaby()) {
                return c;
            }
        }
        return null;
    }

    private static void couveuseCoupureCourte() {
        donner("Un second œuf en main", Items.EGG, 1);
        pas("Un second œuf dans la couveuse", () -> deplacer(fenteMenu(est(Items.EGG), true)),
                () -> couveuse().getSlot(0).getItem().is(Items.EGG) && etat(incA) == MachineState.RUNNING && couveuse().progress(0) > 10,
                () -> "case 0 " + couveuse().getSlot(0).getItem() + ", etat " + etat(incA) + ", progression " + couveuse().progress(0), null);
        pasD(60, "Coupure courte : source retirée, réserve vidée, le compteur de coupure monte", () -> {
            progAvant = couveuse().progress(0);
            commande("setblock " + xyz(srcA) + " minecraft:air");
            commande("data merge block " + xyz(incA) + " {energy:0}");
        }, () -> etat(incA) == MachineState.NO_POWER && couveuse().outage() >= 200,
                () -> "etat " + etat(incA) + ", compteur de coupure " + couveuse().outage() + "/" + couveuse().outageTotal(),
                () -> "sans courant : etat NO_POWER, compteur de coupure " + couveuse().outage() + "/" + couveuse().outageTotal()
                        + " ticks (visible dans le menu), l'oeuf est toujours la : " + couveuse().getSlot(0).getItem());
        pas("Le courant revient avant le seuil : rien n'est perdu, le compteur retombe à zéro", () -> {
            commande("setblock " + xyz(srcA) + " " + CREATIVE);
        }, () -> etat(incA) == MachineState.RUNNING && couveuse().outage() == 0 && couveuse().getSlot(0).getItem().is(Items.EGG)
                && couveuse().progress(0) >= progAvant,
                () -> "etat " + etat(incA) + ", compteur " + couveuse().outage() + ", case 0 " + couveuse().getSlot(0).getItem() + ", progression "
                        + couveuse().progress(0) + " (avant la coupure " + progAvant + ")",
                () -> "coupure courte sans perte : l'oeuf est la, progression " + progAvant + " -> " + couveuse().progress(0)
                        + " (gardee), compteur de coupure remis a " + couveuse().outage());
    }

    private static void couveuseCoupureLongue() {
        pasCD(120, "Coupure longue : source retirée, la couvée est perdue au bout du seuil (temps réel)", () -> {
            coupureMax = 0;
            commande("setblock " + xyz(srcA) + " minecraft:air");
            commande("data merge block " + xyz(incA) + " {energy:0}");
        }, () -> {
            coupureMax = Math.max(coupureMax, couveuse().outage());
            return coupureMax >= couveuse().outageTotal() - 60 && couveuse().getSlot(0).getItem().isEmpty();
        }, () -> "compteur de coupure maximal vu " + coupureMax + "/" + couveuse().outageTotal() + ", case 0 " + couveuse().getSlot(0).getItem()
                + ", etat " + etat(incA),
                () -> "couvee perdue : le compteur est monte jusqu'a " + coupureMax + "/" + couveuse().outageTotal() + " ticks (vu dans le menu) "
                        + "puis la case de l'oeuf est vide ; duree reelle de l'attente " + String.format("%.0f", ecoule()) + " s");
        fermer("Fermeture de la couveuse");
        pas("Le courant revient pour la suite", () -> commande("setblock " + xyz(srcA) + " " + CREATIVE),
                () -> etat(incA) == MachineState.STOPPED || etat(incA) == MachineState.RUNNING, () -> "etat " + etat(incA), null);
    }

    // ================================================================================================
    // Plaque électrique sous un Cooking Pot
    // ================================================================================================

    private static CookingPotMenu pot() {
        return (CookingPotMenu) menu();
    }

    private static void ragout(String legende) {
        pas(legende, () -> {
            deplacer(fenteMenu(est(Items.BEEF), true));
            deplacer(fenteMenu(est(Items.CARROT), true));
            deplacer(fenteMenu(est(Items.POTATO), true));
            deplacer(fenteMenu(est(Items.BOWL), true));
        }, () -> ingredientsDansLaMarmite(), () -> "cases " + menu().slots.subList(0, 8).stream().map(s -> s.getItem().toString()).toList(), null);
    }

    private static boolean ingredientsDansLaMarmite() {
        boolean boeuf = false;
        for (int i = 0; i < 6; i++) {
            boeuf |= menu().getSlot(i).getItem().is(Items.BEEF);
        }
        return boeuf && menu().getSlot(CookingPotMenu.INDEX_CONTAINER).getItem().is(Items.BOWL);
    }

    private static void plaque() {
        allerA("Le joueur devant la plaque", () -> plaque);
        donner("Un bœuf cru", Items.BEEF, 1);
        donner("Une carotte", Items.CARROT, 1);
        donner("Une pomme de terre", Items.POTATO, 1);
        donner("Un bol", Items.BOWL, 1);
        ouvrir("Ouverture du Cooking Pot posé sur la plaque", () -> marmite, CookingPotMenu.class);
        ragout("Les ingrédients du ragoût entrent dans le Cooking Pot");
        pasD(30, "La plaque alimentée est allumée et le Cooking Pot est chauffé", () -> {},
                () -> etat(plaque) == MachineState.RUNNING && mc.level.getBlockState(plaque).getValue(HotPlateBlock.LIT) && pot().isHeated(),
                () -> "plaque " + etat(plaque) + " allumee " + mc.level.getBlockState(plaque).getValue(HotPlateBlock.LIT) + ", marmite chauffee " + pot().isHeated(),
                () -> "plaque : etat RUNNING, LIT vrai, le menu de Farmer's Delight lit isHeated() vrai (sa propre regle, pas une copie)");
        pasD(40, "Le ragoût cuit sur la plaque", () -> {},
                () -> pot().getSlot(CookingPotMenu.INDEX_OUTPUT).getItem().is(objet("farmersdelight:beef_stew")),
                () -> "cases " + menu().slots.subList(0, 9).stream().map(s -> s.getItem().toString()).toList() + ", chauffee " + pot().isHeated(),
                () -> "ragout de boeuf cuit sans feu, sur la plaque : sortie " + pot().getSlot(CookingPotMenu.INDEX_OUTPUT).getItem());
        pas("Le ragoût est repris", () -> deplacer(CookingPotMenu.INDEX_OUTPUT),
                () -> compter(est(objet("farmersdelight:beef_stew"))) >= 1, () -> "ragout absent de l'inventaire", null);
        fermer("Fermeture du Cooking Pot");
        donner("Un second bœuf cru", Items.BEEF, 1);
        donner("Une seconde carotte", Items.CARROT, 1);
        donner("Une seconde pomme de terre", Items.POTATO, 1);
        donner("Un second bol", Items.BOWL, 1);
        pas("Plaque coupée : source retirée, réserve vidée", () -> {
            commande("setblock " + xyz(srcPlaque) + " minecraft:air");
            commande("data merge block " + xyz(plaque) + " {energy:0}");
        }, () -> etat(plaque) == MachineState.NO_POWER && !mc.level.getBlockState(plaque).getValue(HotPlateBlock.LIT),
                () -> "plaque " + etat(plaque) + " allumee " + mc.level.getBlockState(plaque).getValue(HotPlateBlock.LIT), null);
        ouvrir("Réouverture du Cooking Pot, plaque sans courant", () -> marmite, CookingPotMenu.class);
        ragout("Les ingrédients d'un second ragoût entrent dans le Cooking Pot");
        pasD(15, "Sans courant, plus de chaleur : le Cooking Pot n'est pas chauffé et rien ne cuit en 8 s", () -> {},
                () -> ecoule() >= 8 && !pot().isHeated() && pot().getSlot(CookingPotMenu.INDEX_OUTPUT).getItem().isEmpty()
                        && pot().getCookProgressionScaled() == 0 && etat(plaque) == MachineState.NO_POWER,
                () -> "chauffee " + pot().isHeated() + ", sortie " + pot().getSlot(CookingPotMenu.INDEX_OUTPUT).getItem() + ", progression "
                        + pot().getCookProgressionScaled() + ", plaque " + etat(plaque),
                () -> "plaque sans courant : etat NO_POWER, LIT faux, isHeated() faux, progression de cuisson 0, sortie vide apres 8 s "
                        + "(les memes ingredients cuisaient en 10 s avec le courant)");
        fermer("Fermeture du Cooking Pot");
    }

    // ================================================================================================
    // Marmite de cantine
    // ================================================================================================

    private static CanteenPotMenu marmiteDeCantine() {
        return (CanteenPotMenu) menu();
    }

    private static void cantine() {
        allerA("Le joueur devant la marmite de cantine", () -> cantine);
        // Deux jeux d'ingrédients et cinq bols : la fournée en paie un jeu et trois bols, le reste prouve le prix réel.
        pas("Deux bœufs, deux carottes, deux pommes de terre, cinq bols", () -> {
            for (Item i : new Item[] {Items.BEEF, Items.CARROT, Items.POTATO, Items.BOWL}) {
                commande("clear " + nom() + " " + BuiltInRegistries.ITEM.getKey(i));
            }
            stewsAvant = compter(est(objet("farmersdelight:beef_stew")));
            commande("give " + nom() + " minecraft:beef 2");
            commande("give " + nom() + " minecraft:carrot 2");
            commande("give " + nom() + " minecraft:potato 2");
            commande("give " + nom() + " minecraft:bowl 5");
        }, () -> compter(est(Items.BEEF)) == 2 && compter(est(Items.CARROT)) == 2 && compter(est(Items.POTATO)) == 2 && compter(est(Items.BOWL)) == 5
                ,
                () -> "boeuf " + compter(est(Items.BEEF)) + ", bols " + compter(est(Items.BOWL)) + ", ragouts " + compter(est(objet("farmersdelight:beef_stew"))), null);
        ouvrir("Ouverture de la marmite de cantine", () -> cantine, CanteenPotMenu.class);
        pas("Les ingrédients et les bols entrent (clics majuscule)", () -> {
            deplacer(fenteMenu(est(Items.BEEF), true));
            deplacer(fenteMenu(est(Items.CARROT), true));
            deplacer(fenteMenu(est(Items.POTATO), true));
            deplacer(fenteMenu(est(Items.BOWL), true));
        }, () -> marmiteDeCantine().containersNeeded() == 3 && marmiteDeCantine().containersMissing() == 0
                && marmiteDeCantine().getSlot(6).getItem().is(Items.BOWL),
                () -> "bols exiges " + marmiteDeCantine().containersNeeded() + ", manquants " + marmiteDeCantine().containersMissing()
                        + ", cases " + menu().slots.subList(0, 8).stream().map(s -> s.getItem().toString()).toList(),
                () -> "la marmite exige " + marmiteDeCantine().containersNeeded() + " bols (un par portion), 0 manquant : " + marmiteDeCantine().getSlot(6).getItem());
        pasD(30, "La marmite se met en marche", () -> {},
                () -> etat(cantine) == MachineState.RUNNING && marmiteDeCantine().progress() > 0,
                () -> "etat " + etat(cantine) + ", progression " + marmiteDeCantine().progress() + "/" + marmiteDeCantine().total(),
                () -> "marmite de cantine : etat RUNNING, progression " + marmiteDeCantine().progress() + "/" + marmiteDeCantine().total()
                        + ", energie " + marmiteDeCantine().energy() + "/" + marmiteDeCantine().capacity());
        pasD(60, "La fournée finit : trois portions pour un seul jeu d'ingrédients", () -> {},
                () -> marmiteDeCantine().getSlot(7).getItem().is(objet("farmersdelight:beef_stew")) && marmiteDeCantine().getSlot(7).getItem().getCount() == 3,
                () -> "sortie " + marmiteDeCantine().getSlot(7).getItem() + ", progression " + marmiteDeCantine().progress() + "/" + marmiteDeCantine().total(),
                () -> "sortie de la marmite : " + marmiteDeCantine().getSlot(7).getItem() + " ; ingredients restants " + restantsCantine());
        pas("Le prix d'une fournée : un seul jeu d'ingrédients et trois bols", () -> {},
                () -> restantsCantine().equals("boeuf 1, carotte 1, pomme de terre 1, bols 2"),
                () -> "restants : " + restantsCantine() + " (attendu : boeuf 1, carotte 1, pomme de terre 1, bols 2)",
                () -> "sur 2 jeux et 5 bols donnes, il reste " + restantsCantine() + " : un jeu et trois bols payes pour trois portions");
        pas("Les trois ragoûts sont repris dans l'inventaire", () -> deplacer(7),
                () -> compter(est(objet("farmersdelight:beef_stew"))) == stewsAvant + 3,
                () -> "ragouts dans l'inventaire : " + compter(est(objet("farmersdelight:beef_stew"))), () -> "inventaire : 3 ragouts de boeuf");
        fermer("Fermeture de la marmite de cantine");
        // Deux piles de même date se fondent : les ragoûts de l'étape ne se distinguent alors de celui des vivres que par leur nombre.
        pas("Les ragoûts de l'étape sont retirés (le nombre d'avant l'étape reste)", () -> {}, () -> {
            int total = compter(est(objet("farmersdelight:beef_stew")));
            if (total == ragoutsAvantEtape) {
                return true;
            }
            if (System.nanoTime() - derniereLecture > 700_000_000L) {
                for (ItemStack st : mc.player.getInventory().items) {
                    // Seule une pile datée est visée : sans date, un /clear toucherait aussi le ragoût des vivres.
                    if (st.is(objet("farmersdelight:beef_stew")) && st.getTag() != null) {
                        int exces = total - ragoutsAvantEtape;
                        commande("clear " + nom() + " farmersdelight:beef_stew" + st.getTag() + " " + Math.min(exces, st.getCount()));
                        derniereLecture = System.nanoTime();
                        break;
                    }
                }
            }
            return false;
        }, () -> "ragouts : " + compter(est(objet("farmersdelight:beef_stew"))) + " au lieu de " + ragoutsAvantEtape, null);
    }

    private static String restantsCantine() {
        int boeuf = 0;
        int carotte = 0;
        int patate = 0;
        for (int i = 0; i < 6; i++) {
            ItemStack s = menu().getSlot(i).getItem();
            if (s.is(Items.BEEF)) {
                boeuf += s.getCount();
            } else if (s.is(Items.CARROT)) {
                carotte += s.getCount();
            } else if (s.is(Items.POTATO)) {
                patate += s.getCount();
            }
        }
        return "boeuf " + boeuf + ", carotte " + carotte + ", pomme de terre " + patate + ", bols " + menu().getSlot(6).getItem().getCount();
    }

    // ================================================================================================
    // Clôture : un zombie
    // ================================================================================================

    private static Zombie zombieProche(BlockPos autour) {
        for (Zombie z : mc.level.getEntitiesOfClass(Zombie.class, new AABB(autour).inflate(12))) {
            return z;
        }
        return null;
    }

    /**
     * Un zombie casqué (le soleil ne le brûle pas) dans l'enclos au sud de la barre est-ouest de la ligne, fermé par de la pierre
     * à l'ouest et au sud et par le coude de clôtures à l'est, posé contre la barre : à 0,95 bloc du coin de son bloc, soit
     * 0,025 bloc de la barre, sous la marge de contact. Avec son IA il peut être repoussé ; sans IA ({@code sansIA}) il reste
     * exactement où il est, ce qui prouve que le contact dure quand il n'y a plus de courant.
     */
    private static String invoquerZombie(boolean sansIA) {
        return "summon minecraft:zombie " + (o.getX() + 2.5) + " " + o.getY() + " " + (o.getZ() + 8.95)
                + " {PersistenceRequired:1b,Silent:1b," + (sansIA ? "NoAI:1b," : "")
                + "ArmorItems:[{},{},{},{id:\"minecraft:iron_helmet\",Count:1b}]}";
    }

    private static void mesurerZombie(Zombie z) {
        double zz = z.getZ();
        zombiePlusProche = Math.min(zombiePlusProche, zz);
        zombieRepousse = Math.max(zombieRepousse, zz - zombiePlusProche);
    }

    private static void zombie() {
        allerA("Le joueur près de la ligne du zombie, de l'autre côté de la clôture", () -> elec1.offset(2, 0, -2));
        pasC("L'enclos du zombie : pierre à l'ouest et au sud, trois blocs de haut", () -> {
            commande("fill " + xyz(elec1.offset(0, 0, 1)) + " " + xyz(elec1.offset(0, 2, 3)) + " minecraft:stone");
            commande("fill " + xyz(elec1.offset(1, 0, 3)) + " " + xyz(elec1.offset(4, 2, 3)) + " minecraft:stone");
        }, () -> mc.level.getBlockState(elec1.offset(0, 2, 2)).is(Blocks.STONE) && mc.level.getBlockState(elec1.offset(4, 0, 3)).is(Blocks.STONE),
                () -> "enclos absent", null);
        pasD(30, "L'électrificateur alimenté est en marche, sa ligne est relue", () -> {},
                () -> etat(elec1) == MachineState.RUNNING, () -> "etat " + etat(elec1), () -> "electrificateur : etat RUNNING, source Create contre lui");
        pasC("Un zombie casqué dans l'enclos, au sud de la clôture, le joueur au nord", () -> {
            zombieVu = false;
            zombieMort = false;
            derniereVieZombie = 20;
            vieZombieMin = 1000;
            zombieRepousse = 0;
            zombiePlusProche = 1000;
            essaisZombie = 0;
            commande(invoquerZombie(false));
        }, () -> {
            Zombie z = zombieProche(elec1);
            if (z != null && !zombieVu) {
                zombieVu = true;
                departZombie = z.position();
            }
            return zombieVu;
        }, () -> "zombie non vu", () -> "zombie vu par le client en " + departZombie + ", vie " + zombieProche(elec1).getHealth() + "/"
                + zombieProche(elec1).getMaxHealth());
        pasD(40, "Le zombie marche vers le joueur, bute contre la clôture : blessé et repoussé", () -> {},
                () -> {
                    Zombie z = zombieProche(elec1);
                    if (z != null) {
                        zombieVu = true;
                        derniereVieZombie = z.getHealth();
                        vieZombieMin = Math.min(vieZombieMin, z.getHealth());
                        mesurerZombie(z);
                    } else if (zombieVu && derniereVieZombie <= 2.0) {
                        zombieMort = true; // sa propre mort : vu à 2 points ou moins, puis disparu
                    }
                    // Avec son IA, un zombie peut s'éloigner de la barre avant le premier choc : un autre est posé, jusqu'à 3 fois.
                    // Chaque essai repart de zéro : une clôture qui ne choque jamais échoue à chacun.
                    if (!zombieMort && vieZombieMin >= 20 && ecoule() > 8 * (1 + essaisZombie) && essaisZombie < 3) {
                        essaisZombie++;
                        LOG.info("[e2e] zombie jamais touche en {} s : nouvel essai {}", (int) ecoule(), essaisZombie + 1);
                        commande("kill @e[type=minecraft:zombie,distance=..60]");
                        commande(invoquerZombie(false));
                        zombieVu = false;
                        zombieMort = false;
                        derniereVieZombie = 20;
                        vieZombieMin = 1000;
                        zombiePlusProche = 1000;
                        zombieRepousse = 0;
                    }
                    // De vrais dégâts, toujours : un zombie qui n'a jamais perdu un point ne passe pas.
                    return vieZombieMin < 20 && zombieRepousse >= 0.35;
                },
                () -> "vie minimale vue " + vieZombieMin + ", repoussement maximal " + zombieRepousse + ", mort " + zombieMort + ", plus pres de la cloture z="
                        + zombiePlusProche,
                () -> "zombie contre la cloture (essai " + (essaisZombie + 1) + ") : vie minimale vue " + vieZombieMin + "/20 (hostile : il peut en mourir : " + zombieMort + "), repousse de "
                        + String.format("%.2f", zombieRepousse) + " bloc apres avoir touche la barre (z le plus proche " + String.format("%.2f", zombiePlusProche)
                        + ", barre en z=" + (elec1.getZ() + 0.5));
        pas("Zombies retirés, courant coupé : source retirée, réserve vidée", () -> {
            commande("kill @e[type=minecraft:zombie,distance=..60]");
            commande("setblock " + xyz(src1) + " minecraft:air");
            commande("data merge block " + xyz(elec1) + " {energy:0}");
        }, () -> etat(elec1) == MachineState.NO_POWER && zombieProche(elec1) == null && ecoule() >= 1,
                () -> "etat " + etat(elec1) + ", zombie " + zombieProche(elec1), null);
        pasC("Un zombie neuf sans IA, posé contre la clôture sans courant", () -> {
            zombieVu = false;
            vieZombie2Min = 1000;
            commande(invoquerZombie(true));
        }, () -> {
            Zombie z = zombieProche(elec1);
            if (z != null && !zombieVu) {
                zombieVu = true;
                departZombie = z.position();
            }
            return zombieVu;
        }, () -> "zombie non vu", null);
        pasD(30, "Sans courant, plus rien : le zombie reste 10 s contre la clôture sans une égratignure", () -> {},
                () -> {
                    Zombie z = zombieProche(elec1);
                    if (z != null) {
                        vieZombie2Min = Math.min(vieZombie2Min, z.getHealth());
                    }
                    return ecoule() >= 10 && z != null && vieZombie2Min == z.getMaxHealth() && z.position().distanceTo(departZombie) < 0.05
                            && etat(elec1) == MachineState.NO_POWER;
                },
                () -> "vie minimale " + vieZombie2Min + ", zombie " + zombieProche(elec1) + ", etat " + etat(elec1),
                () -> "clôture sans courant : le zombie sans IA reste contre la barre (à 0,025 bloc) pendant " + String.format("%.0f", ecoule())
                        + " s, garde ses " + vieZombie2Min + " points de vie et ne bouge pas (etat NO_POWER)");
        pas("Zombie retiré", () -> commande("kill @e[type=minecraft:zombie,distance=..60]"), () -> zombieProche(elec1) == null, () -> "zombie encore la", null);
    }

    // ================================================================================================
    // Clôture : un vrai joueur non opérateur, sous First Aid
    // ================================================================================================

    /** Le joueur pousse contre la clôture (touche avancer tenue) jusqu'à {@code chocs} repoussements et {@code secondes} s. */
    private static void pousser(String legende, int chocs, int secondes) {
        pasCD(120, legende, () -> {
            coups = 0;
            vieJoueurMin = mc.player.getHealth();
            zPrecedent = mc.player.getZ();
            derniereLecture = System.nanoTime();
            mc.options.keyUp.setDown(true);
            LOG.info("[e2e] avant les chocs : {}", releve());
        }, () -> {
            if (mc.player.isDeadOrDying()) {
                joueurMort = true;
                return true;
            }
            vieJoueurMin = Math.min(vieJoueurMin, mc.player.getHealth());
            double z = mc.player.getZ();
            if (z - zPrecedent > 0.15) {
                // Un repoussement : la clôture vient de frapper (blessé ou non).
                coups++;
                LOG.info("[e2e] choc {} (repoussement de {} bloc) : {}", coups, String.format("%.2f", z - zPrecedent), releve());
                derniereLecture = System.nanoTime();
            } else if (System.nanoTime() - derniereLecture > 2_000_000_000L) {
                derniereLecture = System.nanoTime();
                LOG.info("[e2e] releve ({} chocs) : {}", coups, releve());
            }
            zPrecedent = z;
            return coups >= chocs && ecoule() >= secondes;
        }, () -> "chocs vus " + coups + " sur " + chocs + ", vie minimale " + vieJoueurMin + ", mort " + joueurMort,
                () -> coups + " chocs (repoussements) en " + String.format("%.0f", ecoule()) + " s, vie minimale " + vieJoueurMin
                        + (joueurMort ? " ; LE JOUEUR EST MORT" : ""));
        pasC("Le joueur lâche la touche", () -> mc.options.keyUp.setDown(false), () -> ecoule() >= 1.5, () -> "", () -> "apres : " + releve());
    }

    private static void joueur() {
        pasC("Le joueur devant la clôture du joueur, regard au nord, courant en marche", () -> {
            commande("tp " + nom() + " " + (elec2.getX() + 2.5) + " " + o.getY() + " " + (elec2.getZ() + 1.45) + " 180 0");
        }, () -> etat(elec2) == MachineState.RUNNING && Math.abs(mc.player.getZ() - (elec2.getZ() + 1.45)) < 0.3 && ecoule() >= 1,
                () -> "joueur en " + mc.player.position() + ", electrificateur " + etat(elec2), null);
        pasC("Le joueur n'est plus opérateur (console RCON : deop)", () -> rcon("deop " + nom()),
                () -> !operateur() && ecoule() >= 1, () -> "toujours operateur : " + operateur(),
                () -> "deop par RCON, hasPermissions(2) cote client = " + operateur() + " : un vrai joueur ordinaire, en survie, gamemode "
                        + mc.gameMode.getPlayerMode());
        pasC("État de départ : vie vanilla et membres de First Aid", () -> {
            joueurMort = false;
            String m = modeleFirstAid();
            teteAvant = membre(m, "head");
            corpsAvant = membre(m, "body");
            LOG.info("[e2e] First Aid, modele brut du serveur : {}", m);
        }, () -> ecoule() >= 1, () -> "", () -> "depart : " + releve());
        // À pleine vie d'abord : un choc blesse (un point), il n'y a pas encore de plancher à tenir.
        pousser("À pleine vie : trois chocs blessent d'un point chacun", 3, 3);
        pasC("Un joueur blessé, vivant, tête et torse intacts à une marge près", () -> {}, () -> {
            String m = modeleFirstAid();
            return !joueurMort && mc.player.isAlive() && mc.player.getHealth() < 20f && membre(m, "head") >= Math.min(teteAvant, 1.0) - 0.001
                    && membre(m, "body") >= Math.min(corpsAvant, 1.0) - 0.001;
        }, () -> "mort " + joueurMort + ", vie " + mc.player.getHealth() + ", " + parties(modeleFirstAid()),
                () -> "apres 3 chocs a pleine vie : " + releve());
        // Faible vie : la faim (« starve ») est répartie par First Aid sans tuer (tryNoKill) tant qu'un membre peut encore encaisser.
        pasCD(120, "Vie abaissée par /damage minecraft:starve (console RCON) jusqu'à une tête à 1,5 point ou moins et 9 de vie vanilla, ou 5 de vie vanilla, sans mourir", () -> {
            degats = 0;
            dernierDegat = 0;
        }, () -> {
            if (mc.player.isDeadOrDying()) {
                joueurMort = true;
                return true;
            }
            String m = modeleFirstAid();
            boolean teteBasse = membre(m, "head") <= 1.5 && mc.player.getHealth() <= 9.0f;
            if (teteBasse || mc.player.getHealth() <= 5.0f || degats >= 60) {
                return ecoule() >= 1;
            }
            if (System.nanoTime() - dernierDegat > 700_000_000L) {
                dernierDegat = System.nanoTime();
                degats++;
                rcon("damage " + nom() + " 1 minecraft:starve");
                LOG.info("[e2e] /damage {} : {}", degats, releve());
            }
            return false;
        }, () -> "vie client " + mc.player.getHealth() + " apres " + degats + " /damage",
                () -> "apres " + degats + " /damage de 1 point (starve) : " + releve() + (joueurMort ? " ; LE JOUEUR EST MORT PENDANT LA MISE EN PLACE" : ""));
        pasC("Le joueur a survécu à la mise en place", () -> {
            if (joueurMort) {
                throw new IllegalStateException("joueur mort avant les chocs : " + releve());
            }
        }, () -> !mc.player.isDeadOrDying(), () -> "joueur mort", () -> "vivant, " + releve());
        pasC("Le joueur se replace contre la clôture (console RCON : il n'est plus opérateur)", () -> {
            rcon("tp " + nom() + " " + (elec2.getX() + 2.5) + " " + o.getY() + " " + (elec2.getZ() + 1.45) + " 180 0");
        },() -> ecoule() >= 1 && Math.abs(mc.player.getZ() - (elec2.getZ() + 1.45)) < 0.3, () -> "joueur en " + mc.player.position(), null);
        pasC("État juste avant les chocs à faible vie", () -> {
            vieJoueurAvant = mc.player.getHealth();
            faAvant = modeleFirstAid();
            sommeFaAvant = somme(faAvant);
            teteAvant = membre(faAvant, "head");
            corpsAvant = membre(faAvant, "body");
        }, () -> true, () -> "", () -> "faible vie, avant les chocs : " + releve());
        pousser("Chocs répétés à faible vie : le joueur pousse contre la clôture", 15, 15);
        pasC("Le plancher de vie tient : le joueur est vivant et sa vie n'a pas franchi le plancher", () -> {
            faApres = modeleFirstAid();
            sommeFaApres = somme(faApres);
        }, () -> !joueurMort && mc.player.isAlive() && vieJoueurMin >= Math.min(vieJoueurAvant, PLANCHER) - 0.01
                && membre(faApres, "head") > 0 && membre(faApres, "body") > 0 && coups >= 15
                && membre(faApres, "head") >= Math.min(teteAvant, 1.0) - 0.001 && membre(faApres, "body") >= Math.min(corpsAvant, 1.0) - 0.001,
                () -> "mort " + joueurMort + ", vivant " + mc.player.isAlive() + ", vie avant " + vieJoueurAvant + ", minimale " + vieJoueurMin
                        + " (plancher " + PLANCHER + ") ; First Aid avant " + parties(faAvant) + " (somme " + sommeFaAvant + ") apres " + parties(faApres)
                        + " (somme " + sommeFaApres + ")",
                () -> "joueur reel non operateur sous First Aid, " + coups + " chocs a faible vie : vivant, vie vanilla minimale " + vieJoueurMin
                        + " pour un depart a " + vieJoueurAvant + " (plancher " + PLANCHER + ") ; membres avant " + parties(faAvant) + " (somme "
                        + sommeFaAvant + "), apres " + parties(faApres) + " (somme " + sommeFaApres + ")");
        pasC("Le joueur redevient opérateur (console RCON : op)", () -> rcon("op " + nom()),
                () -> operateur() && ecoule() >= 1.5, () -> "operateur " + operateur(), () -> "op par RCON, hasPermissions(2) = " + operateur());
        pas("Rangement : joueur soigné, clôture du joueur sans courant et sans réserve", () -> {
            commande("effect give " + nom() + " minecraft:instant_health 1 10 true");
            commande("setblock " + xyz(src2) + " minecraft:air");
            commande("data merge block " + xyz(elec2) + " {energy:0}");
        }, () -> etat(elec2) == MachineState.NO_POWER, () -> "etat " + etat(elec2), null);
    }

    // ================================================================================================
    // Couveuse : compteur de coupure relu après un arrêt
    // ================================================================================================

    private static void couveusePersistance() {
        allerA("Le joueur devant la seconde couveuse", () -> incB);
        ouvrir("Ouverture de la seconde couveuse", () -> incB, IncubatorMenu.class);
        donner("Un œuf en main pour la seconde couveuse", Items.EGG, 1);
        pas("Un œuf dans la seconde couveuse", () -> deplacer(fenteMenu(est(Items.EGG), true)),
                () -> couveuse().getSlot(0).getItem().is(Items.EGG) && etat(incB) == MachineState.RUNNING,
                () -> "case 0 " + couveuse().getSlot(0).getItem() + ", etat " + etat(incB), null);
        pasD(60, "Coupure en cours : source retirée, réserve vidée, le compteur monte", () -> {
            commande("setblock " + xyz(srcB) + " minecraft:air");
            commande("data merge block " + xyz(incB) + " {energy:0}");
        }, () -> etat(incB) == MachineState.NO_POWER && couveuse().outage() >= 300,
                () -> "etat " + etat(incB) + ", compteur " + couveuse().outage() + "/" + couveuse().outageTotal(), null);
        pasC("Compteur de coupure retenu, positions écrites ; le serveur s'arrête en pleine coupure", () -> {
            outageAvantArret = couveuse().outage();
            try {
                List<String> lignes = new ArrayList<>();
                lignes.add("origine=" + xyz(o));
                lignes.add("depart=" + xyz(depart));
                lignes.add("coupure=" + outageAvantArret);
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, () -> couveuse().outage() < couveuse().outageTotal() - 300, () -> "compteur trop haut : " + couveuse().outage(),
                () -> "a retrouver apres relance : seconde couveuse " + incB.toShortString() + " sans courant, compteur de coupure "
                        + outageAvantArret + "/" + couveuse().outageTotal() + " ticks, un oeuf dedans (le serveur est arrete tout de suite)");
        retour();
    }

    /** Le joueur rentre là où il était avant l'étape : à la relance, les étapes des lots précédents le retrouvent chez lui. */
    private static void retour() {
        pas("Le joueur referme le menu et rentre à son point de départ", () -> {
            mc.player.closeContainer();
            commande("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && mc.player.blockPosition().distSqr(depart) < 9, () -> "joueur en " + mc.player.blockPosition(), null);
    }

    private static Path fichier() {
        return mc.gameDirectory.toPath().resolve(FICHIER);
    }

    /** Avant le {@code /stop}, même après un échec : touches relâchées, joueur rendu opérateur. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        mc = minecraft;
        minecraft.options.keyUp.setDown(false);
        if (o != null && !operateur()) {
            try {
                rcon("op " + nom());
            } catch (RuntimeException e) {
                LOG.error("[e2e] rcon impossible avant l'arret", e);
            }
        }
    }

    // ================================================================================================
    // Après un arrêt et une relance du serveur
    // ================================================================================================

    static void relance() {
        Map<String, String> lu = new LinkedHashMap<>();
        pasC("Relecture des positions et du compteur de coupure", () -> {
            try {
                for (String l : Files.readAllLines(fichier(), StandardCharsets.UTF_8)) {
                    int i = l.indexOf('=');
                    lu.put(l.substring(0, i), l.substring(i + 1));
                }
            } catch (IOException e) {
                throw new IllegalStateException("premiere phase absente : " + fichier(), e);
            }
            String[] t = lu.get("origine").split(" ");
            placer(new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2])));
            String[] d = lu.get("depart").split(" ");
            depart = new BlockPos(Integer.parseInt(d[0]), Integer.parseInt(d[1]), Integer.parseInt(d[2]));
            outageAvantArret = Integer.parseInt(lu.get("coupure"));
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            poste(incB);
        // Le joueur se reconnecte tout près de là où il s'est arrêté : seule la fin de la téléportation (2 s) rend le clic sûr.
        }, () -> pres(incB) && ecoule() >= 2.5 && mc.level.getBlockState(incB).is(FoodRegistry.INCUBATOR.get()),
                () -> "joueur en " + mc.player.blockPosition() + ", bloc " + mc.level.getBlockState(incB),
                () -> "relance : seconde couveuse relue du disque en " + incB.toShortString() + ", compteur d'avant l'arret " + outageAvantArret);
        ouvrir("Relance : ouverture de la seconde couveuse", () -> incB, IncubatorMenu.class);
        pasC("Relance : le compteur de coupure a été relu, l'œuf est toujours là", () -> {},
                () -> couveuse().getSlot(0).getItem().is(Items.EGG) && couveuse().outage() >= outageAvantArret
                        && couveuse().outage() < couveuse().outageTotal() && etat(incB) == MachineState.NO_POWER,
                () -> "case 0 " + couveuse().getSlot(0).getItem() + ", compteur " + couveuse().outage() + " (avant l'arret " + outageAvantArret
                        + "), seuil " + couveuse().outageTotal() + ", etat " + etat(incB),
                () -> "relance : compteur de coupure " + couveuse().outage() + " ticks, a l'arret " + outageAvantArret
                        + " (il repart de la valeur sauvegardee, sans perte) ; l'oeuf est la, etat " + etat(incB));
        pas("Relance : le courant revient, le compteur retombe à zéro, l'œuf est gardé", () -> commande("setblock " + xyz(srcB) + " " + CREATIVE),
                () -> etat(incB) == MachineState.RUNNING && couveuse().outage() == 0 && couveuse().getSlot(0).getItem().is(Items.EGG),
                () -> "etat " + etat(incB) + ", compteur " + couveuse().outage() + ", case 0 " + couveuse().getSlot(0).getItem(),
                () -> "relance : source remise, etat RUNNING, compteur " + couveuse().outage() + ", oeuf garde");
        fermer("Relance : fermeture de la couveuse");
        pas("Rangement de l'aire du lot 5", () -> {
            commande("kill @e[type=minecraft:chicken,distance=..200]");
            commande("fill " + xyz(o.offset(-4, 0, -6)) + " " + xyz(o.offset(26, 6, 14)) + " air");
            commande("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
            commande("difficulty peaceful");
            commande("gamerule doMobSpawning true");
            commande("gamerule naturalRegeneration true");
        }, () -> mc.level.getBlockState(incB).isAir() && mc.level.getBlockState(cantine).isAir(), () -> "blocs encore la",
                () -> "aire videe, regles du monde rendues");
    }
}
