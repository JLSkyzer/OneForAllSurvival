package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.light.LightRegistry;
import dev.oneforallsurvival.light.LighthouseBlock;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.town.ShowerHeadBlock;
import dev.oneforallsurvival.town.TownRegistry;
import dev.oneforallsurvival.town.WaterHeaterMenu;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Étape « vie de la ville » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot 6 vu par un vrai
 * client contre un serveur dédié, avec le pack complet et First Aid.
 *
 * <p>Une vraie chaîne d'eau : un puits d'eau, une pompe de Create, le dessalinisateur, une seconde pompe, le chauffe-eau,
 * une troisième pompe (qui monte) et la pomme de douche, le tout relié par des tuyaux de Create ({@code create:fluid_pipe})
 * et mû par trois moteurs créatifs (par un engrenage chacun). Avant les moteurs rien ne passe ; après, l'eau arrive. Un
 * joueur qui n'est <b>pas opérateur</b> (console RCON : {@code deop}) se tient sous une pomme sèche (rien), puis sous la
 * pomme reliée : le Bien-être n'arrive qu'après cinq secondes d'eau chaude continues, sans rendre un point de vie (vie
 * vanilla et membres de First Aid relevés avant et après), sans autre effet bénéfique, et une douche de plus ne
 * dépasse pas la durée maximale. Le phare, sur une tour de pierre, s'allume, s'éteint sous un toit, se rallume.
 *
 * <p>Avant l'arrêt du serveur, courant et moteurs sont retirés, les cuves du chauffe-eau et de la douche sont relevées
 * et écrites dans un fichier ; à la relance le même client les relit (écran du chauffe-eau, console RCON) et rejoue le
 * phare. Rien n'est abrégé : le temps du monde n'est jamais accéléré.
 *
 * <p>L'aire est une dalle à 70 blocs à l'ouest et 60 au sud du joueur, en hauteur, loin des aires des lots précédents.
 */
final class E2eVille {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-ville.txt";
    private static final double DELAI = 25;
    private static final int ECHECS_MAX = 8;
    private static final int Y_DALLE = 150;
    private static final String CREATIVE = "createaddition:creative_energy";
    private static final String POMPE = "create:mechanical_pump";
    private static final String TUYAU = "create:fluid_pipe";
    private static final String ENGRENAGE = "create:cogwheel";
    private static final String MOTEUR = "create:creative_motor";
    /** Le Bien-être dure 6 minutes (config {@code showerWellBeingMinutes}) : 7200 ticks. */
    private static final int DUREE_MAX = 7200;
    /** L'effet ne vient qu'après {@code showerSeconds} (5) secondes continues : jamais avant 3,8 s sous la pomme. */
    private static final double PREMIER_EFFET_MIN = 3.8;
    private static final Pattern NOMBRE = Pattern.compile("data: (-?[0-9]+(?:[.][0-9]+)?)");
    private static final Pattern PARTIE = Pattern.compile("([A-Za-z]+): [{]health: (-?[0-9.]+)f?");
    private static final String CLE_FA = "firstaid:cap_adv_dmg_mdl";

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai, boolean critique) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static final Map<String, Object[]> CACHE = new java.util.HashMap<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;
    private static int echecsDuPas;

    private static Minecraft mc;
    private static BlockPos depart;
    private static BlockPos o;

    // Ce que les pas se transmettent.
    private static boolean effetVu;
    private static double secondesAvantEffet = -1;
    private static boolean effetTropTot;
    private static int dureeMaxVue;
    private static int dureeMinVue = Integer.MAX_VALUE;
    private static int amplificateur = -1;
    private static float vieAvant;
    private static double faAvant;
    private static long derniereLecture;
    private static float derniereVie = -1;
    private static long vieStableDepuis;
    private static int tankAvantDouche;
    private static int potableChauffeEau;
    private static int chaudeChauffeEau;
    private static int tankDouche;
    private static int stableA = -1;
    private static int stableB = -1;
    private static long dernierReleve;

    private E2eVille() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape";
    }

    static void recevoir(Component message) {
        // Les réponses utiles passent par la console RCON, pas par le chat.
    }

    // ================================================================================================
    // Disposition (relative à l'origine O : l'extrémité ouest de la chaîne d'eau, au niveau du sol)
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static BlockPos eau() {
        return p(1, 0, 0);
    }

    private static BlockPos pompeA() {
        return p(3, 0, 0);
    }

    private static BlockPos desal() {
        return p(5, 0, 0);
    }

    private static BlockPos sourceDesal() {
        return p(5, 0, -1);
    }

    private static BlockPos pompeB() {
        return p(7, 0, 0);
    }

    private static BlockPos chauffeEau() {
        return p(9, 0, 0);
    }

    private static BlockPos sourceChauffeEau() {
        return p(9, 0, -1);
    }

    private static BlockPos pompeC() {
        return p(9, 2, 0);
    }

    private static BlockPos douche() {
        return p(11, 2, 0);
    }

    private static BlockPos pommeSeche() {
        return p(14, 2, 0);
    }

    private static BlockPos phare() {
        return p(18, 3, 0);
    }

    private static BlockPos sourcePhare() {
        return p(17, 3, 0);
    }

    private static BlockPos toit() {
        return p(18, 5, 0);
    }

    private static BlockPos[] moteurs() {
        return new BlockPos[] {p(2, 1, 0), p(6, 1, 0), p(8, 3, 0)};
    }

    private static BlockPos[] engrenages() {
        return new BlockPos[] {p(3, 1, 0), p(7, 1, 0), p(8, 2, 0)};
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
        Pas pas = PAS.get(index);
        if (!actionFaite) {
            LOG.info("[e2e] pas : {}", pas.legende());
            pas.action().run();
            actionFaite = true;
            debut = System.nanoTime();
            return false;
        }
        if (pas.pret().getAsBoolean()) {
            if (pas.succes() != null) {
                E2eClient.pass(pas.succes().get());
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
                    E2eClient.fail("trop d'echecs dans l'etape de la ville : arret");
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

    private static boolean operateur() {
        return mc.player.hasPermissions(2);
    }

    private static String rcon(String commandeConsole) {
        return E2eReseau.rcon(commandeConsole);
    }

    /** Un nombre lu par la console : {@code data get block <pos> <chemin>} ; -1 si la réponse n'en contient pas. */
    private static double donnee(BlockPos pos, String chemin) {
        String cle = xyz(pos) + " " + chemin;
        long maintenant = System.nanoTime();
        Object[] vu = CACHE.get(cle);
        // Les pas interrogent à chaque tick : une lecture par console au plus toutes les 600 ms et par valeur.
        if (vu != null && maintenant - (long) vu[0] < 600_000_000L) {
            return (double) vu[1];
        }
        Matcher m = NOMBRE.matcher(rcon("data get block " + cle));
        double valeur = m.find() ? Double.parseDouble(m.group(1)) : -1;
        CACHE.put(cle, new Object[] {maintenant, valeur});
        return valeur;
    }

    private static int tank(BlockPos pos, String chemin) {
        return (int) donnee(pos, chemin);
    }

    /** Le joueur est placé par la console (elle marche aussi quand il n'est plus opérateur). */
    private static void placerJoueur(double x, double z, float yaw, float pitch) {
        rcon("tp " + nom() + " " + x + " " + o.getY() + " " + z + " " + yaw + " " + pitch);
    }

    private static boolean pres(double x, double z) {
        return Math.abs(mc.player.getX() - x) < 1.0 && Math.abs(mc.player.getZ() - z) < 1.0;
    }

    private static MachineState etat(BlockPos pos) {
        var s = mc.level.getBlockState(pos);
        return s.hasProperty(ElectricMachineBlock.STATE) ? s.getValue(ElectricMachineBlock.STATE) : null;
    }

    private static boolean phareAllume() {
        var s = mc.level.getBlockState(phare());
        return s.is(LightRegistry.LIGHTHOUSE.get()) && s.getValue(LighthouseBlock.LIT);
    }

    private static boolean doucheActive(BlockPos pos) {
        var s = mc.level.getBlockState(pos);
        return s.is(TownRegistry.SHOWER_HEAD.get()) && s.getValue(ShowerHeadBlock.ACTIVE);
    }

    private static AbstractContainerMenu menu() {
        return mc.player.containerMenu;
    }

    private static boolean ouvert(Class<? extends AbstractContainerMenu> type) {
        return type.isInstance(menu()) && mc.screen instanceof AbstractContainerScreen<?>;
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

    private static WaterHeaterMenu menuChauffeEau() {
        return (WaterHeaterMenu) menu();
    }

    private static void fermer(String legende) {
        pas(legende, () -> mc.player.closeContainer(), () -> mc.screen == null && menu() == mc.player.inventoryMenu,
                () -> "menu encore ouvert : " + menu().getClass().getSimpleName(), null);
    }

    // ---------------------------------------------------------------------------------------------- vie, First Aid

    private static double vieServeur() {
        Matcher m = Pattern.compile("(-?[0-9.]+)f").matcher(rcon("data get entity " + nom() + " Health"));
        return m.find() ? Double.parseDouble(m.group(1)) : -1;
    }

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

    private static List<String> parties(String modele) {
        Matcher m = PARTIE.matcher(modele);
        List<String> l = new ArrayList<>();
        while (m.find()) {
            l.add(m.group(1) + "=" + m.group(2));
        }
        return l;
    }

    private static String releve() {
        String fa = modeleFirstAid();
        return "vie client " + mc.player.getHealth() + " | vie serveur " + vieServeur() + " | First Aid membres " + parties(fa)
                + " (somme " + somme(fa) + ")";
    }

    private static MobEffectInstance bienEtre() {
        return mc.player.getEffect(TownRegistry.WELL_BEING.get());
    }

    private static String effetsBenefiques() {
        List<String> noms = new ArrayList<>();
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (e.getEffect().isBeneficial()) {
                noms.add(e.getEffect().getDescriptionId() + "(niveau " + e.getAmplifier() + ", durée " + e.getDuration() + ")");
            }
        }
        return noms.toString();
    }

    // ================================================================================================
    // Première phase
    // ================================================================================================

    static void premierePhase() {
        preparation();
        chaine();
        doucheSeche();
        ecranChauffeEau();
        doucheChaude();
        phareCycle("");
        persistance();
    }

    private static void preparation() {
        pasC("Aire de la ville sur une dalle, règles du monde par de vraies commandes", () -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            depart = mc.player.blockPosition();
            BlockPos spawn = mc.level.getSharedSpawnPos();
            int x = depart.getX() - 70;
            int z = depart.getZ() + 60;
            if (Math.abs(x - spawn.getX()) <= 40 && Math.abs(z - spawn.getZ()) <= 40) {
                x = depart.getX() + 160;
            }
            o = new BlockPos(x, Y_DALLE, z);
            commande("difficulty easy");
            commande("gamerule doMobSpawning false");
            commande("gamerule naturalRegeneration false");
            commande("fill " + xyz(p(-4, 0, -8)) + " " + xyz(p(24, 9, 8)) + " air");
            commande("fill " + xyz(p(-4, -1, -8)) + " " + xyz(p(24, -1, 8)) + " stone");
            commande("gamemode survival " + nom());
            // Le Bien-être d'un passage précédent (6 minutes) est sauvegardé avec le joueur : il part sans.
            commande("effect clear " + nom() + " oneforallsurvival:well_being");
            commande("tp " + nom() + " " + (o.getX() + 9.5) + " " + o.getY() + " " + (o.getZ() + 2.5) + " 180 0");
        }, () -> mc.level.getBlockState(o.below()).is(net.minecraft.world.level.block.Blocks.STONE) && mc.player.blockPosition().getY() >= Y_DALLE - 1
                && bienEtre() == null && ecoule() >= 1.5,
                () -> "joueur en " + mc.player.blockPosition() + ", dalle " + mc.level.getBlockState(o.below()),
                () -> "difficulty easy, gamerule (naissances et regeneration naturelle coupees), /fill, /tp : aire en " + o.toShortString()
                        + " (joueur parti de " + depart.toShortString() + ")");
    }

    private static void chaine() {
        pasC("La chaîne d'eau posée par /setblock, sans moteur : puits, tuyaux, pompes, dessalinisateur, chauffe-eau, douches, phare", () -> {
            // Ordre : tuyaux et pompes d'abord, machines ensuite (les tuyaux relisent leurs voisins quand une machine arrive).
            // Un puits de quatre sources : la pompe vide la source du bout, les trois autres la refont (règle des deux sources voisines).
            for (int[] mur : new int[][] {{-1, 0}, {0, -1}, {0, 1}, {1, -2}, {1, 2}, {2, -1}, {2, 1}}) {
                commande("setblock " + xyz(p(mur[0], 0, mur[1])) + " minecraft:stone");
            }
            for (int[] source : new int[][] {{0, 0}, {1, -1}, {1, 1}, {1, 0}}) {
                commande("setblock " + xyz(p(source[0], 0, source[1])) + " minecraft:water");
            }
            commande("setblock " + xyz(p(2, 0, 0)) + " " + TUYAU);
            commande("setblock " + xyz(pompeA()) + " " + POMPE + "[facing=east]");
            commande("setblock " + xyz(p(4, 0, 0)) + " " + TUYAU);
            commande("setblock " + xyz(p(6, 0, 0)) + " " + TUYAU);
            commande("setblock " + xyz(pompeB()) + " " + POMPE + "[facing=east]");
            commande("setblock " + xyz(p(8, 0, 0)) + " " + TUYAU);
            commande("setblock " + xyz(p(9, 1, 0)) + " " + TUYAU);
            commande("setblock " + xyz(pompeC()) + " " + POMPE + "[facing=up]");
            commande("setblock " + xyz(p(9, 3, 0)) + " " + TUYAU);
            commande("setblock " + xyz(p(10, 3, 0)) + " " + TUYAU);
            commande("setblock " + xyz(p(11, 3, 0)) + " " + TUYAU);
            commande("setblock " + xyz(desal()) + " oneforallsurvival:desalinator");
            commande("setblock " + xyz(sourceDesal()) + " " + CREATIVE);
            commande("setblock " + xyz(chauffeEau()) + " oneforallsurvival:water_heater");
            commande("setblock " + xyz(sourceChauffeEau()) + " " + CREATIVE);
            commande("setblock " + xyz(douche()) + " oneforallsurvival:shower_head");
            commande("setblock " + xyz(pommeSeche()) + " oneforallsurvival:shower_head");
            // La tour du phare : trois blocs de pierre, le phare, sa source ; au ciel libre, bien au-dessus de la mer.
            for (int dy = 0; dy <= 2; dy++) {
                commande("setblock " + xyz(p(18, dy, 0)) + " minecraft:stone_bricks");
            }
            commande("setblock " + xyz(phare()) + " oneforallsurvival:lighthouse");
            commande("setblock " + xyz(sourcePhare()) + " " + CREATIVE);
        }, () -> mc.level.getBlockState(desal()).is(dev.oneforallsurvival.food.FoodRegistry.DESALINATOR.get())
                && mc.level.getBlockState(chauffeEau()).is(TownRegistry.WATER_HEATER.get())
                && mc.level.getBlockState(douche()).is(TownRegistry.SHOWER_HEAD.get())
                && mc.level.getBlockState(pommeSeche()).is(TownRegistry.SHOWER_HEAD.get())
                && mc.level.getBlockState(phare()).is(LightRegistry.LIGHTHOUSE.get())
                && mc.level.getBlockState(pompeC()).getBlock() == bloc(POMPE) && mc.level.getBlockState(eau()).getBlock() == net.minecraft.world.level.block.Blocks.WATER
                && mc.level.getBlockState(sourcePhare()).getBlock() == bloc(CREATIVE) && ecoule() >= 1.5,
                () -> "blocs absents : " + mc.level.getBlockState(desal()) + " " + mc.level.getBlockState(chauffeEau()) + " "
                        + mc.level.getBlockState(pompeC()) + " " + mc.level.getBlockState(phare()),
                () -> "puits de quatre sources -> tuyau -> pompe -> tuyau -> dessalinisateur -> tuyau -> pompe -> tuyau -> chauffe-eau -> tuyau -> pompe (montante) -> "
                        + "3 tuyaux -> pomme de douche ; une seconde pomme sans tuyau ; un phare sur 3 blocs de pierre ; chaque machine contre une source creative");
        pasCD(8, "Sans moteur, rien ne passe : les trois cuves d'aval restent vides", () -> {},
                () -> ecoule() >= 4 && tank(desal(), "water.Amount") == 0 && tank(chauffeEau(), "potable.Amount") == 0
                        && tank(chauffeEau(), "hot.Amount") == 0 && tank(douche(), "tank.Amount") == 0,
                () -> "cuve d'eau du dessalinisateur " + tank(desal(), "water.Amount") + ", chauffe-eau potable " + tank(chauffeEau(), "potable.Amount")
                        + " chaude " + tank(chauffeEau(), "hot.Amount") + ", douche " + tank(douche(), "tank.Amount"),
                () -> "avant les moteurs, apres " + String.format("%.0f", ecoule()) + " s : dessalinisateur 0 mB d'eau, chauffe-eau 0 / 0, douche 0 (rien ne coule sans pompe)");
        pasC("Trois moteurs créatifs de Create, chacun sur un engrenage qui entraîne sa pompe", () -> {
            commande("setblock " + xyz(engrenages()[0]) + " " + ENGRENAGE + "[axis=x]");
            commande("setblock " + xyz(moteurs()[0]) + " " + MOTEUR + "[facing=east]");
            commande("setblock " + xyz(engrenages()[1]) + " " + ENGRENAGE + "[axis=x]");
            commande("setblock " + xyz(moteurs()[1]) + " " + MOTEUR + "[facing=east]");
            commande("setblock " + xyz(engrenages()[2]) + " " + ENGRENAGE + "[axis=y]");
            commande("setblock " + xyz(moteurs()[2]) + " " + MOTEUR + "[facing=down]");
        }, () -> mc.level.getBlockState(moteurs()[2]).getBlock() == bloc(MOTEUR) && ecoule() >= 3
                && Math.abs(donnee(pompeA(), "Speed")) > 0 && Math.abs(donnee(pompeB(), "Speed")) > 0 && Math.abs(donnee(pompeC(), "Speed")) > 0,
                () -> "vitesses des pompes : " + donnee(pompeA(), "Speed") + " " + donnee(pompeB(), "Speed") + " " + donnee(pompeC(), "Speed"),
                () -> "les trois pompes tournent (vitesses lues par la console : " + donnee(pompeA(), "Speed") + ", " + donnee(pompeB(), "Speed") + ", "
                        + donnee(pompeC(), "Speed") + " tr/min)");
        pasD(40, "L'eau arrive par le tuyau dans le dessalinisateur (le premier maillon)", () -> {},
                () -> tank(desal(), "water.Amount") > 0 || tank(desal(), "potable.Amount") > 0,
                () -> "dessalinisateur : eau " + tank(desal(), "water.Amount") + ", potable " + tank(desal(), "potable.Amount"),
                () -> "dessalinisateur : cuve d'eau " + tank(desal(), "water.Amount") + " mB, potable " + tank(desal(), "potable.Amount")
                        + " mB, venue du puits par la pompe A et deux tuyaux");
    }

    private static net.minecraft.world.level.block.Block bloc(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation(id));
    }

    // ---------------------------------------------------------------------------------------------- douches

    private static void doucheSeche() {
        pasC("Le joueur n'est plus opérateur (console RCON : deop) et se tient sous la pomme sans tuyau", () -> {
            placerJoueur(o.getX() + 14.5, o.getZ() + 0.5, 0, 0);
            rcon("deop " + nom());
            effetVu = false;
        }, () -> !operateur() && pres(o.getX() + 14.5, o.getZ() + 0.5) && ecoule() >= 1.5,
                () -> "operateur " + operateur() + ", joueur en " + mc.player.position(),
                () -> "deop par RCON, hasPermissions(2) cote client = " + operateur() + " ; joueur sous la pomme seche en " + mc.player.blockPosition()
                        + ", gamemode " + mc.gameMode.getPlayerMode());
        pasC("Sous la pomme sans eau chaude pendant 8 s (plus que les 5 s exigées) : aucun effet, la pomme reste éteinte", () -> {
            effetVu = bienEtre() != null;
        }, () -> {
            effetVu |= bienEtre() != null;
            return ecoule() >= 8;
        }, () -> "effet vu " + effetVu,
                () -> {
                    int cuve = tank(pommeSeche(), "tank.Amount");
                    if (effetVu || cuve != 0 || doucheActive(pommeSeche())) {
                        throw new IllegalStateException("effet vu " + effetVu + ", cuve " + cuve + ", pomme active " + doucheActive(pommeSeche()));
                    }
                    return "sans eau chaude : 8 s sous la pomme, aucun Bien-etre, cuve de la pomme 0 mB, pomme eteinte (le meme joueur, a la meme hauteur, "
                            + "l'obtient sous la pomme reliee, plus bas)";
                });
    }

    private static void ecranChauffeEau() {
        pasC("Le joueur devant le chauffe-eau, clic droit : son écran s'ouvre", () -> {
            placerJoueur(o.getX() + 9.5, o.getZ() + 2.5, 180, 20);
        }, () -> pres(o.getX() + 9.5, o.getZ() + 2.5) && ecoule() >= 1.5, () -> "joueur en " + mc.player.position(), null);
        pasC("Ouverture du chauffe-eau par un vrai clic droit", () -> {
            tenirVide();
            clic(chauffeEau());
        }, () -> ouvert(WaterHeaterMenu.class), () -> "menu " + menu().getClass().getSimpleName() + ", ecran " + mc.screen,
                () -> "ecran du chauffe-eau ouvert par un vrai clic droit, sans etre operateur");
        pasD(60, "Le chauffe-eau reçoit l'eau potable du dessalinisateur par sa pompe, l'écran la montre, et il chauffe", () -> {},
                () -> ouvert(WaterHeaterMenu.class) && menuChauffeEau().hotAmount() > 0 && menuChauffeEau().energy() > 0,
                () -> ouvert(WaterHeaterMenu.class) ? "potable " + menuChauffeEau().potableAmount() + ", chaude " + menuChauffeEau().hotAmount()
                        + ", energie " + menuChauffeEau().energy() + ", etat " + menuChauffeEau().stateOrdinal() : "menu ferme",
                () -> "ecran du chauffe-eau : eau potable " + menuChauffeEau().potableAmount() + " mB, eau chaude " + menuChauffeEau().hotAmount()
                        + " mB, energie " + menuChauffeEau().energy() + "/" + menuChauffeEau().capacity() + " FE, etat "
                        + MachineState.values()[menuChauffeEau().stateOrdinal()]);
        fermer("Fermeture du chauffe-eau");
        pasCD(150, "L'eau chaude monte par la pompe C et trois tuyaux jusqu'à la pomme reliée : 300 mB au moins", () -> {},
                () -> tank(douche(), "tank.Amount") >= 300,
                () -> "cuve de la pomme " + tank(douche(), "tank.Amount") + " mB, chauffe-eau potable " + tank(chauffeEau(), "potable.Amount") + " chaude "
                        + tank(chauffeEau(), "hot.Amount") + ", dessalinisateur eau " + tank(desal(), "water.Amount") + " potable "
                        + tank(desal(), "potable.Amount"),
                () -> "pomme reliee : cuve de " + tank(douche(), "tank.Amount") + " mB d'eau chaude arrivee du chauffe-eau (apres "
                        + String.format("%.0f", ecoule()) + " s) ; chauffe-eau potable " + tank(chauffeEau(), "potable.Amount") + " chaude "
                        + tank(chauffeEau(), "hot.Amount") + " ; dessalinisateur potable " + tank(desal(), "potable.Amount"));
    }

    private static void doucheChaude() {
        pasC("Le joueur est blessé (console RCON : /damage starve) et sa vie se stabilise", () -> {
            placerJoueur(o.getX() + 14.5, o.getZ() + 3.5, 0, 0);
            rcon("damage " + nom() + " 8 minecraft:starve");
            derniereVie = -1;
            vieStableDepuis = 0;
        }, () -> {
            float vie = mc.player.getHealth();
            if (vie != derniereVie) {
                derniereVie = vie;
                vieStableDepuis = System.nanoTime();
                return false;
            }
            return ecoule() >= 2 && System.nanoTime() - vieStableDepuis > 2_000_000_000L;
        }, () -> "vie instable : " + mc.player.getHealth() + " | " + releve(),
                () -> {
                    vieAvant = mc.player.getHealth();
                    faAvant = somme(modeleFirstAid());
                    return "depart de la douche : " + releve() + " ; effets benefiques " + effetsBenefiques();
                });
        pasC("Le joueur se place sous la pomme reliée : pas d'effet avant 3,8 s, le Bien-être vient ensuite", () -> {
            effetVu = false;
            effetTropTot = false;
            secondesAvantEffet = -1;
            tankAvantDouche = tank(douche(), "tank.Amount");
            placerJoueur(o.getX() + 11.5, o.getZ() + 0.5, 0, 0);
        }, () -> {
            if (bienEtre() != null && !effetVu) {
                effetVu = true;
                secondesAvantEffet = ecoule();
                effetTropTot = secondesAvantEffet < PREMIER_EFFET_MIN;
                dureeMaxVue = 0;
                dureeMinVue = Integer.MAX_VALUE;
            }
            return effetVu;
        }, () -> "aucun Bien-etre apres " + String.format("%.1f", ecoule()) + " s sous la pomme reliee ; cuve " + tank(douche(), "tank.Amount")
                + ", pomme active " + doucheActive(douche()) + ", joueur en " + mc.player.position(),
                () -> {
                    if (effetTropTot) {
                        throw new IllegalStateException("l'effet est venu trop tot : " + secondesAvantEffet + " s");
                    }
                    MobEffectInstance e = bienEtre();
                    amplificateur = e.getAmplifier();
                    return "Bien-etre recu apres " + String.format("%.1f", secondesAvantEffet) + " s d'eau chaude continue sous la pomme (seuil de 5 s, "
                            + "tolerance de la mesure : le temps du client, pas du serveur), niveau " + e.getAmplifier() + ", duree " + e.getDuration()
                            + " ticks, pomme active " + doucheActive(douche());
                });
        pasC("Sept secondes de plus sous la pomme : la durée ne dépasse jamais 6 minutes (pas de cumul)", () -> {}, () -> {
            MobEffectInstance e = bienEtre();
            if (e != null) {
                dureeMaxVue = Math.max(dureeMaxVue, e.getDuration());
                dureeMinVue = Math.min(dureeMinVue, e.getDuration());
            }
            return ecoule() >= 7;
        }, () -> "duree max vue " + dureeMaxVue, () -> {
            if (dureeMaxVue > DUREE_MAX || bienEtre() == null) {
                throw new IllegalStateException("duree max vue " + dureeMaxVue + " pour un plafond de " + DUREE_MAX + ", effet " + bienEtre());
            }
            return "une douche de plus ne cumule pas : duree maximale vue " + dureeMaxVue + " ticks pour un plafond de " + DUREE_MAX
                    + " (6 min), minimale " + dureeMinVue + ", l'effet reste actif";
        });
        pasC("Le Bien-être ne soigne pas : vie vanilla, membres de First Aid et autres effets bénéfiques", () -> {}, () -> ecoule() >= 1,
                () -> "", () -> {
                    float vie = mc.player.getHealth();
                    double fa = somme(modeleFirstAid());
                    List<String> benefiques = new ArrayList<>();
                    for (MobEffectInstance e : mc.player.getActiveEffects()) {
                        if (e.getEffect().isBeneficial()) {
                            benefiques.add(e.getEffect().getDescriptionId());
                        }
                    }
                    String bonne = TownRegistry.WELL_BEING.get().getDescriptionId();
                    if (vie > vieAvant + 0.01f || fa > faAvant + 0.01 || benefiques.size() != 1 || !benefiques.get(0).equals(bonne)) {
                        throw new IllegalStateException("vie " + vieAvant + " -> " + vie + ", membres " + faAvant + " -> " + fa + ", benefiques " + benefiques);
                    }
                    return "le Bien-etre ne rend aucun point de vie : vie client " + vieAvant + " -> " + vie + ", somme des membres de First Aid " + faAvant
                            + " -> " + fa + " (naturalRegeneration coupee), seul effet benefique : " + effetsBenefiques() + " ; cuve de la pomme "
                            + tankAvantDouche + " -> " + tank(douche(), "tank.Amount") + " mB";
                });
        pasC("Le joueur redevient opérateur (console RCON : op)", () -> {
            rcon("op " + nom());
            placerJoueur(o.getX() + 15.5, o.getZ() + 3.5, 0, 0);
        }, () -> operateur() && ecoule() >= 1.5, () -> "operateur " + operateur(), () -> "op par RCON, hasPermissions(2) = " + operateur());
    }

    // ---------------------------------------------------------------------------------------------- phare

    /**
     * Allumé en tour libre, éteint sous un toit, rallumé sans toit. {@code prefixe} distingue la relance dans les lignes.
     */
    private static void phareCycle(String prefixe) {
        pasC(prefixe + "Le joueur regarde le phare, de nuit ou de jour", () -> {
            placerJoueur(o.getX() + 14.5, o.getZ() + 5.5, 180, -10);
        }, () -> pres(o.getX() + 14.5, o.getZ() + 5.5) && ecoule() >= 1.5, () -> "joueur en " + mc.player.position(), null);
        pasCD(20, prefixe + "Le phare est allumé en tour libre (ciel libre, bien au-dessus de la mer)", () -> {},
                () -> phareAllume() && etat(phare()) == MachineState.RUNNING,
                () -> "bloc " + mc.level.getBlockState(phare()),
                () -> "phare allume en tour libre : y = " + phare().getY() + " (mer a 63), etat " + etat(phare()) + ", lit vrai, lumiere "
                        + mc.level.getBlockState(phare()).getLightEmission());
        pasCD(20, prefixe + "Un toit de pierre au-dessus : il s'éteint et ne marche plus", () -> commande("setblock " + xyz(toit()) + " minecraft:stone"),
                () -> !phareAllume() && etat(phare()) != MachineState.RUNNING && mc.level.getBlockState(toit()).is(net.minecraft.world.level.block.Blocks.STONE),
                () -> "bloc " + mc.level.getBlockState(phare()) + ", toit " + mc.level.getBlockState(toit()),
                () -> "sous un toit : lit faux, etat " + etat(phare()) + " (la source creative est toujours la : il ne consomme rien faute de vouloir travailler)");
        pasCD(20, prefixe + "Le toit est retiré : le phare se rallume", () -> commande("setblock " + xyz(toit()) + " minecraft:air"),
                () -> phareAllume() && etat(phare()) == MachineState.RUNNING && mc.level.getBlockState(toit()).isAir(),
                () -> "bloc " + mc.level.getBlockState(phare()),
                () -> "toit retire : phare rallume, etat " + etat(phare()) + ", lit vrai");
    }

    // ---------------------------------------------------------------------------------------------- persistance

    private static void persistance() {
        // Les cuves du chauffe-eau doivent être pleines de quelque chose à l'arrêt : on coupe d'abord la douche (l'eau chaude s'y
        // accumule), puis le courant du chauffe-eau (l'eau potable s'y accumule), puis tout le reste.
        pasCD(40, "La douche est coupée (moteur de la pompe C retiré) : l'eau chaude s'accumule dans le chauffe-eau", () -> {
            commande("setblock " + xyz(moteurs()[2]) + " minecraft:air");
            commande("tp " + nom() + " " + (o.getX() + 9.5) + " " + o.getY() + " " + (o.getZ() + 2.5) + " 180 20");
        }, () -> ecoule() >= 4 && tank(chauffeEau(), "hot.Amount") >= 400,
                () -> "chauffe-eau chaude " + tank(chauffeEau(), "hot.Amount") + ", potable " + tank(chauffeEau(), "potable.Amount"),
                () -> "pompe C arretee : eau chaude du chauffe-eau " + tank(chauffeEau(), "hot.Amount") + " mB apres " + String.format("%.0f", ecoule()) + " s");
        pasCD(40, "Le chauffe-eau est privé de courant (source retirée, réserve vidée) : l'eau potable s'y accumule", () -> {
            commande("setblock " + xyz(sourceChauffeEau()) + " minecraft:air");
            commande("data merge block " + xyz(chauffeEau()) + " {energy:0}");
        }, () -> ecoule() >= 4 && tank(chauffeEau(), "potable.Amount") >= 100 && etat(chauffeEau()) != MachineState.RUNNING,
                () -> "chauffe-eau potable " + tank(chauffeEau(), "potable.Amount") + ", chaude " + tank(chauffeEau(), "hot.Amount") + ", etat "
                        + etat(chauffeEau()),
                () -> "chauffe-eau sans courant : etat " + etat(chauffeEau()) + ", eau potable " + tank(chauffeEau(), "potable.Amount")
                        + " mB arrivee par la pompe B, eau chaude " + tank(chauffeEau(), "hot.Amount") + " mB");
        pasC("Tout le reste est coupé : dessalinisateur sans courant, moteurs retirés, les cuves se figent", () -> {
            commande("setblock " + xyz(sourceDesal()) + " minecraft:air");
            commande("data merge block " + xyz(desal()) + " {energy:0}");
            commande("setblock " + xyz(moteurs()[0]) + " minecraft:air");
            commande("setblock " + xyz(moteurs()[1]) + " minecraft:air");
            stableA = -1;
            stableB = -1;
            dernierReleve = 0;
        }, () -> mc.level.getBlockState(moteurs()[0]).isAir() && mc.level.getBlockState(moteurs()[1]).isAir() && ecoule() >= 3,
                () -> "etat du chauffe-eau " + etat(chauffeEau()), () -> "plus de courant ni de moteur : chauffe-eau " + etat(chauffeEau()));
        pasC("Ouverture du chauffe-eau pour relever ses cuves", () -> {
            tenirVide();
            clic(chauffeEau());
        }, () -> ouvert(WaterHeaterMenu.class), () -> "menu " + menu().getClass().getSimpleName(), null);
        pasCD(30, "Les cuves ne bougent plus pendant 2 s (relevé à l'écran et par la console)", () -> {}, () -> {
            if (!ouvert(WaterHeaterMenu.class)) {
                return false;
            }
            long maintenant = System.nanoTime();
            if (maintenant - dernierReleve < 1_000_000_000L) {
                return false;
            }
            dernierReleve = maintenant;
            int a = menuChauffeEau().potableAmount() * 100000 + menuChauffeEau().hotAmount();
            int b = tank(douche(), "tank.Amount");
            boolean stable = a == stableA && b == stableB && menuChauffeEau().potableAmount() > 0 && menuChauffeEau().hotAmount() > 0;
            stableA = a;
            stableB = b;
            return stable;
        }, () -> "chauffe-eau potable " + menuChauffeEau().potableAmount() + " chaude " + menuChauffeEau().hotAmount() + ", douche "
                + tank(douche(), "tank.Amount"), () -> {
                    potableChauffeEau = menuChauffeEau().potableAmount();
                    chaudeChauffeEau = menuChauffeEau().hotAmount();
                    tankDouche = tank(douche(), "tank.Amount");
                    return "a retrouver apres relance : chauffe-eau eau potable " + potableChauffeEau + " mB, eau chaude " + chaudeChauffeEau
                            + " mB, pomme reliee " + tankDouche + " mB (energie " + menuChauffeEau().energy() + " FE)";
                });
        fermer("Fermeture du chauffe-eau");
        pasC("Positions et cuves écrites ; le joueur rentre à son point de départ (la zone se décharge)", () -> {
            try {
                List<String> lignes = new ArrayList<>();
                lignes.add("origine=" + xyz(o));
                lignes.add("depart=" + xyz(depart));
                lignes.add("potable=" + potableChauffeEau);
                lignes.add("chaude=" + chaudeChauffeEau);
                lignes.add("douche=" + tankDouche);
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            commande("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && mc.player.blockPosition().distSqr(depart) < 9, () -> "joueur en " + mc.player.blockPosition(), null);
    }

    private static Path fichier() {
        return mc.gameDirectory.toPath().resolve(FICHIER);
    }

    /** Avant le {@code /stop}, même après un échec : touches relâchées, joueur rendu opérateur, règles du monde rendues. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        mc = minecraft;
        if (o != null) {
            try {
                rcon("op " + nom());
                rcon("gamerule naturalRegeneration true");
                rcon("gamerule doMobSpawning true");
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
        pasC("Relecture des positions et des cuves écrites avant l'arrêt", () -> {
            try {
                for (String l : Files.readAllLines(fichier(), StandardCharsets.UTF_8)) {
                    int i = l.indexOf('=');
                    lu.put(l.substring(0, i), l.substring(i + 1));
                }
            } catch (IOException e) {
                throw new IllegalStateException("premiere phase absente : " + fichier(), e);
            }
            String[] t = lu.get("origine").split(" ");
            o = new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]));
            String[] d = lu.get("depart").split(" ");
            depart = new BlockPos(Integer.parseInt(d[0]), Integer.parseInt(d[1]), Integer.parseInt(d[2]));
            potableChauffeEau = Integer.parseInt(lu.get("potable"));
            chaudeChauffeEau = Integer.parseInt(lu.get("chaude"));
            tankDouche = Integer.parseInt(lu.get("douche"));
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            placerJoueur(o.getX() + 9.5, o.getZ() + 2.5, 180, 20);
        }, () -> pres(o.getX() + 9.5, o.getZ() + 2.5) && ecoule() >= 2.5 && mc.level.getBlockState(chauffeEau()).is(TownRegistry.WATER_HEATER.get()),
                () -> "joueur en " + mc.player.blockPosition() + ", bloc " + mc.level.getBlockState(chauffeEau()),
                () -> "relance : chauffe-eau relu du disque en " + chauffeEau().toShortString() + ", avant l'arret potable " + potableChauffeEau
                        + " chaude " + chaudeChauffeEau + " douche " + tankDouche);
        pasC("Relance : ouverture du chauffe-eau relu", () -> {
            tenirVide();
            clic(chauffeEau());
        }, () -> ouvert(WaterHeaterMenu.class), () -> "menu " + menu().getClass().getSimpleName(), null);
        pasC("Relance : les deux cuves du chauffe-eau sont relues à l'identique", () -> {},
                () -> ouvert(WaterHeaterMenu.class) && ecoule() >= 1.0,
                () -> "menu ferme", () -> {
                    int potable = menuChauffeEau().potableAmount();
                    int chaude = menuChauffeEau().hotAmount();
                    if (potable != potableChauffeEau || chaude != chaudeChauffeEau || chaude <= 0) {
                        throw new IllegalStateException("potable " + potable + " (avant " + potableChauffeEau + "), chaude " + chaude + " (avant "
                                + chaudeChauffeEau + ")");
                    }
                    return "relance : cuves du chauffe-eau relues du disque : eau potable " + potable + " mB (avant l'arret " + potableChauffeEau
                            + "), eau chaude " + chaude + " mB (avant " + chaudeChauffeEau + "), etat "
                            + MachineState.values()[menuChauffeEau().stateOrdinal()] + ", energie " + menuChauffeEau().energy() + " FE";
                });
        fermer("Relance : fermeture du chauffe-eau");
        pasC("Relance : la cuve de la pomme reliée est relue à l'identique", () -> {},
                () -> ecoule() >= 0.5, () -> "", () -> {
                    int cuve = tank(douche(), "tank.Amount");
                    if (cuve != tankDouche || cuve <= 0) {
                        throw new IllegalStateException("cuve de la pomme " + cuve + " (avant l'arret " + tankDouche + ")");
                    }
                    return "relance : cuve de la pomme de douche " + cuve + " mB (avant l'arret " + tankDouche + "), relue par la console RCON";
                });
        phareCycle("Relance : ");
        pas("Rangement de l'aire du lot 6", () -> {
            commande("fill " + xyz(p(-4, 0, -8)) + " " + xyz(p(24, 9, 8)) + " air");
            commande("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
            commande("difficulty peaceful");
            commande("gamerule doMobSpawning true");
            commande("gamerule naturalRegeneration true");
        }, () -> mc.level.getBlockState(chauffeEau()).isAir() && mc.level.getBlockState(phare()).isAir(), () -> "blocs encore la",
                () -> "aire videe, regles du monde rendues");
    }
}
