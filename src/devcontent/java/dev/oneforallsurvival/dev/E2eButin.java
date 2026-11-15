package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.SafeMenu;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Étape « butin des mobs » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot E4 vu par un vrai
 * client contre un serveur dédié avec le pack complet. Elle suit l'étape du commerce (E2) et précède celle du trésor
 * (E3) au premier passage, pour que la réécriture des références d'E1 et d'E2 faite à la fin d'E3 compte déjà les pièces
 * du butin ; à la relance, elle passe en dernier, après tous les contrôles exacts des autres étapes.
 *
 * <p>Une aire close et couverte (aucun mob du dehors, pas de soleil), loin de tout (à 640 blocs du départ : hors de la
 * distance de simulation des autres aires, car l'étape du trésor met le monde en paisible, ce qui retirerait le zombie
 * gardé pour la relance). Les mobs naissent par {@code e2ebutin apparaitre} (le même événement d'apparition que le jeu),
 * par un vrai bloc de générateur, ou par un œuf posé par Dev d'un vrai clic droit ; les vaches sont nourries de blé par
 * de vrais clics droits ; Dev tue par de vrais coups d'épée, en survie, sans être opérateur. La table est « certaine »
 * (un zombie sauvage donne 3 cuivres), réglée par {@code e2ebutin certain on} (côté serveur : {@link E2eButinServeur}).
 *
 * <p>Seuls la ligne « Butin des créatures » de {@code /eco stats} et {@code /eco loot} sont comparés, jamais tout
 * {@code /eco stats}. Avant l'étape du trésor, Dev range ses pièces dans son coffre-fort (l'étape du trésor part d'un Dev
 * qui ne porte rien) et ne retire de son inventaire que ce que l'étape lui a ajouté.
 */
final class E2eButin {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-butin.txt";
    private static final double DELAI = 25;
    private static final int Y_AIRE = 150;
    /** Ce que donne un zombie sauvage avec la table certaine. */
    private static final long BUTIN_ZOMBIE = 3;
    private static final Pattern CLE_VALEUR = Pattern.compile("(\\w+)=(\\[[^\\]]*\\]|[^ ]+)");

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai) {}

    /** Un mob tel que le décrit {@code e2ebutin mobs} ou {@code mob}. */
    private record Bete(String uuid, String type, boolean sauvage, String origine, boolean bebe, boolean garde, boolean amour,
            boolean vivant, double x, double y, double z) {
        static Bete lire(String s) {
            String[] t = s.split(":");
            String[] p = t[8].split(",");
            return new Bete(t[0], t[1], Boolean.parseBoolean(t[2]), t[3], Boolean.parseBoolean(t[4]), Boolean.parseBoolean(t[5]),
                    Boolean.parseBoolean(t[6]), Boolean.parseBoolean(t[7]), Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]));
        }

        String court() {
            return type + "(" + uuid.substring(0, 8) + ", sauvage=" + sauvage + ", origine=" + origine + ", bebe=" + bebe + ")";
        }
    }

    private static final List<Pas> PAS = new ArrayList<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;

    private static Minecraft mc;
    private static BlockPos depart;
    private static BlockPos o;
    private static boolean airePrete;

    // Mémoire d'un pas à l'autre.
    private static String reponse = "";
    private static Map<String, Integer> inventaireAvant = Map.of();
    private static String difficulteAvant = "peaceful";
    private static String naissancesAvant = "true";
    private static String zombieA;
    private static String zombieB;
    private static String zombieC;
    private static String zombieD;
    private static final List<String> VACHES = new ArrayList<>();
    private static String veau;
    private static Map<String, String> registreAvant = Map.of();
    private static long porteAvant;
    private static long gain;
    private static long coffreAvant;
    private static long dernierCoup;
    private static int coups;
    private static long mortNs;

    private E2eButin() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape du butin";
    }

    // ================================================================================================
    // Disposition : intérieur de l'aire de (0, 0, 0) à (12, 3, 8) depuis O ; coque de pierre autour
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static String boite() {
        return xyz(p(-1, -1, -1)) + " " + xyz(p(13, 4, 9));
    }

    private static BlockPos centre() {
        return p(6, 0, 4);
    }

    private static BlockPos generateur() {
        return p(2, 0, 7);
    }

    private static BlockPos poseOeuf() {
        return p(6, 0, 7);
    }

    // ================================================================================================
    // Déroulement (même mécanique que les étapes de la monnaie, du commerce et du trésor)
    // ================================================================================================

    static boolean avancer(Minecraft minecraft) {
        mc = minecraft;
        if (index >= PAS.size()) {
            return true;
        }
        if (airePrete && (mc.player.isDeadOrDying() || mc.player.getY() < o.getY() - 3)) {
            E2eClient.fail("le joueur est mort ou tombe dans le vide : vie " + mc.player.getHealth() + ", position " + mc.player.position()
                    + ", pas " + legende());
            return false;
        }
        Pas pas = PAS.get(index);
        if (!actionFaite) {
            LOG.info("[e2e] pas : {}", pas.legende());
            try {
                pas.action().run();
            } catch (RuntimeException e) {
                LOG.error("[e2e] exception dans l'action : {}", pas.legende(), e);
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
                    LOG.error("[e2e] controle en echec : {}", pas.legende(), e);
                    E2eClient.fail(pas.legende() + " : " + e.getMessage());
                    return false;
                }
                E2eClient.pass(texte);
            }
            index++;
            actionFaite = false;
        } else if (ecoule() > pas.delai()) {
            String detail;
            try {
                detail = pas.echec().get();
            } catch (RuntimeException e) {
                detail = "(" + e + ")";
            }
            E2eClient.fail(pas.legende() + " : " + detail);
        }
        return false;
    }

    private static double ecoule() {
        return (System.nanoTime() - debut) / 1e9;
    }

    private static void pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, DELAI));
    }

    private static void pasD(double delai, String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec,
            Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, delai));
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

    private static Map<String, String> lire(String texte) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = CLE_VALEUR.matcher(texte);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static String sansCrochets(String s) {
        return s != null && s.startsWith("[") && s.endsWith("]") ? s.substring(1, s.length() - 1) : s;
    }

    private static List<Bete> mobs() {
        String liste = sansCrochets(lire(rcon("e2ebutin mobs " + boite())).get("liste"));
        List<Bete> out = new ArrayList<>();
        if (liste != null) {
            for (String s : liste.split(";")) {
                if (!s.isBlank()) {
                    out.add(Bete.lire(s));
                }
            }
        }
        return out;
    }

    /** Le mob, lu sur le serveur (vivant ou non), ou {@code null} s'il n'est plus dans le monde. */
    private static Bete mob(String uuid) {
        Map<String, String> r = lire(rcon("e2ebutin mob " + uuid));
        return "true".equals(r.get("absent")) ? null : Bete.lire(r.get("m"));
    }

    private static Bete chercher(Predicate<Bete> voulu) {
        return mobs().stream().filter(voulu).findFirst().orElse(null);
    }

    private static Map<String, String> sol() {
        return lire(rcon("e2ebutin sol " + boite()));
    }

    private static Map<String, String> registre() {
        return lire(rcon("e2ebutin registre"));
    }

    private static long num(Map<String, String> m, String cle) {
        return Long.parseLong(m.get(cle));
    }

    private static Path fichier() {
        return mc.gameDirectory.toPath().resolve(FICHIER);
    }

    private static boolean pres(BlockPos bloc) {
        return Math.abs(mc.player.getX() - (bloc.getX() + 0.5)) < 1.0 && Math.abs(mc.player.getZ() - (bloc.getZ() + 0.5)) < 1.0
                && Math.abs(mc.player.getY() - bloc.getY()) < 2.5;
    }

    private static void placerJoueur(BlockPos bloc, float yaw, float pitch) {
        rcon("tp " + nom() + " " + (bloc.getX() + 0.5) + " " + bloc.getY() + " " + (bloc.getZ() + 0.5) + " " + yaw + " " + pitch);
    }

    /** Dev à 2 blocs du mob, du côté du centre de l'aire, tourné vers lui. */
    private static void approcher(Bete b) {
        double cx = o.getX() + 6.5;
        double cz = o.getZ() + 4.5;
        double dx = cx - b.x();
        double dz = cz - b.z();
        double n = Math.sqrt(dx * dx + dz * dz);
        if (n < 0.5) {
            dx = 1;
            dz = 0;
            n = 1;
        }
        double px = b.x() + dx / n * 2.0;
        double pz = b.z() + dz / n * 2.0;
        float yaw = (float) Math.toDegrees(Math.atan2(-(b.x() - px), b.z() - pz));
        rcon(String.format(java.util.Locale.ROOT, "tp %s %.2f %d %.2f %.1f 20", nom(), px, o.getY(), pz, yaw));
    }

    private static Entity vu(String uuid) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getUUID().toString().equals(uuid)) {
                return e;
            }
        }
        return null;
    }

    private static String fmt(long cuivres) {
        return CoinMath.format(cuivres).getString();
    }

    private static long valeurPortee() {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s) + Purse.total(s);
        }
        return n + CoinMath.valueOf(mc.player.getOffhandItem());
    }

    private static int compter(net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Les objets de l'inventaire, par identifiant (sac, barre, armure, seconde main). */
    private static Map<String, Integer> inventaire() {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<ItemStack> tout = new ArrayList<>(mc.player.getInventory().items);
        tout.addAll(mc.player.getInventory().armor);
        tout.addAll(mc.player.getInventory().offhand);
        for (ItemStack s : tout) {
            if (!s.isEmpty()) {
                out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
        }
        return out;
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
                return voulu.test(mc.player.getInventory().getItem(cible)) ? cible : -1;
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
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, 8, ClickType.SWAP, mc.player);
                mc.player.getInventory().selected = 8;
                return;
            }
        }
        throw new IllegalStateException("aucune case libre dans l'inventaire");
    }

    private static void clic(BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        LOG.info("[e2e] clic droit en {} face {}, main {} : {}", pos, face, mc.player.getMainHandItem(), res);
    }

    private static void fermer() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        mc.setScreen(null);
    }

    private static boolean ecranOuvert(Class<?> menu) {
        return mc.screen instanceof AbstractContainerScreen<?> s && menu.isInstance(s.getMenu());
    }

    private static CoinAccountMenu menuPieces() {
        return (CoinAccountMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu();
    }

    private static void clicElement(String id) {
        CoinAccountScreen<?> s = (CoinAccountScreen<?>) mc.screen;
        int[] c = s.elementCenter(id).orElseThrow(() -> new IllegalStateException("aucun element " + id));
        boolean pris = s.mouseClicked(c[0], c[1], 0);
        LOG.info("[e2e] clic gauche sur {} de {} en ({}, {}) : {}", id, s.getClass().getSimpleName(), c[0], c[1], pris);
    }

    private static boolean chunksPrets(BlockPos centre) {
        for (int dx = -16; dx <= 16; dx += 16) {
            for (int dz = -16; dz <= 16; dz += 16) {
                if (!mc.level.getChunkSource().hasChunk((centre.getX() + dx) >> 4, (centre.getZ() + dz) >> 4)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Le dernier mot d'une réponse de la console (« The difficulty is Easy », « ... is currently set to: true »). */
    private static String dernierMot(String texte) {
        String[] t = texte.trim().split("\\s+");
        return t[t.length - 1].toLowerCase(java.util.Locale.ROOT);
    }

    // ================================================================================================
    // Les gestes répétés
    // ================================================================================================

    /** Dev frappe le mob à l'épée, un vrai coup toutes les 0,9 s (pleine charge), jusqu'à sa mort lue sur le serveur. */
    private static void tuer(String legende, Supplier<String> uuid, Supplier<String> apres) {
        pasD(40, legende, () -> {
            coups = 0;
            dernierCoup = 0;
            tenir(s -> s.is(Items.DIAMOND_SWORD));
        }, () -> {
            Bete b = mob(uuid.get());
            if (b == null || !b.vivant()) {
                mortNs = System.nanoTime();
                return true;
            }
            if (System.nanoTime() - dernierCoup > 900_000_000L) {
                if (dernierCoup == 0 || coups % 3 == 0) {
                    approcher(b);
                }
                Entity cible = vu(uuid.get());
                if (cible != null && mc.player.distanceTo(cible) < 3.5) {
                    mc.gameMode.attack(mc.player, cible);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    coups++;
                } else {
                    approcher(b);
                }
                dernierCoup = System.nanoTime();
            }
            return false;
        }, () -> "mob " + mob(uuid.get()) + ", " + coups + " coup(s)", apres);
    }

    /** Un mob tué qui ne doit rien donner : aucune pièce au sol, registre inchangé. */
    private static void rienAuSol(String quoi) {
        pas("Après la mort de " + quoi + " : rien au sol, registre inchangé", () -> {}, () -> System.nanoTime() - mortNs > 1_500_000_000L,
                () -> "", () -> {
                    Map<String, String> s = sol();
                    Map<String, String> r = registre();
                    if (!"0".equals(s.get("n")) || !r.get("total").equals(registreAvant.get("total"))
                            || !r.get("morts").equals(registreAvant.get("morts"))) {
                        throw new IllegalStateException("sol " + s + " ; registre " + r + " (avant " + registreAvant + ")");
                    }
                    return quoi + " tue par Dev a l'epee : aucune piece au sol (" + s.get("n") + "), registre inchange (total "
                            + r.get("total") + ", " + r.get("morts") + " mort(s) payante(s))";
                });
    }

    /**
     * Dev tue un zombie sauvage (marqué) : des pièces tombent, il les ramasse ; la hausse de la ligne « Butin des créatures »
     * de {@code /eco stats} égale ce qu'il a ramassé, et {@code /eco loot} le nomme.
     */
    private static void tuerEtRamasser(String quoi, Supplier<String> uuid) {
        pas("Avant de tuer " + quoi + " : registre et ce que porte Dev relevés", () -> {
            registreAvant = registre();
            porteAvant = valeurPortee();
        }, () -> ecoule() >= 0.3, () -> "", null);
        tuer("Dev tue " + quoi + " à l'épée (vrais coups)", uuid, null);
        // Les pièces peuvent rouler jusqu'à Dev et être ramassées aussitôt : au sol, ou déjà dans son inventaire.
        pas("Des pièces tombent à la mort de " + quoi, () -> {}, () -> !"0".equals(sol().get("n")) || valeurPortee() > porteAvant,
                () -> "sol " + sol(), () -> {
                    Map<String, String> s = sol();
                    return "a la mort de " + quoi + " (" + coups + " coups d'epee) : " + s.get("n") + " pile(s) de pieces au sol ("
                            + s.get("valeur") + " cuivres), " + (valeurPortee() - porteAvant) + " deja ramasse(s)";
                });
        pas("Dev ramasse les pièces", () -> {
            String positions = sansCrochets(sol().get("positions"));
            if (!positions.isBlank()) {
                String[] pos = positions.split(";")[0].split(",");
                rcon("tp " + nom() + " " + pos[0] + " " + o.getY() + " " + pos[2]);
            }
        }, () -> "0".equals(sol().get("n")) && valeurPortee() > porteAvant && ecoule() >= 1.0,
                () -> "sol " + sol() + ", Dev porte " + valeurPortee() + " (avant " + porteAvant + ")", () -> {
                    gain = valeurPortee() - porteAvant;
                    Map<String, String> r = registre();
                    String stats = rcon("eco stats");
                    String loot = rcon("eco loot");
                    String ligne = sansCrochets(r.get("ligne"));
                    String ligneDev = sansCrochets(r.get("ligneDev"));
                    long hausse = num(r, "total") - num(registreAvant, "total");
                    if (gain != BUTIN_ZOMBIE || hausse != gain || num(r, "morts") != num(registreAvant, "morts") + 1
                            || num(r, "dev") != num(registreAvant, "dev") + gain || !stats.contains(ligne) || !loot.contains(ligneDev)) {
                        throw new IllegalStateException("ramasse " + gain + " ; registre " + r + " (avant " + registreAvant + ") ; stats "
                                + stats.replace('\n', '|') + " ; loot " + loot.replace('\n', '|'));
                    }
                    return "Dev ramasse " + gain + " cuivres (" + fmt(gain) + ") ; « Butin des creatures » de /eco stats : « " + ligne.trim()
                            + " » (+" + hausse + ") ; /eco loot le nomme : « " + ligneDev.trim() + " » ; " + loot.trim().replace('\n', '|');
                });
    }

    /** Les deux vaches nourries de blé par de vrais clics droits, puis le veau attendu, puis grandi. */
    private static void elevage(String quand) {
        for (int i = 0; i < 2; i++) {
            int n = i;
            pas(quand + "Dev nourrit la vache " + (n + 1) + " de blé (vrai clic droit)", () -> {
                tenir(s -> s.is(Items.WHEAT));
                approcher(mob(VACHES.get(n)));
                clicNs = 0;
            }, () -> {
                Bete b = mob(VACHES.get(n));
                if (b.amour()) {
                    return true;
                }
                if (ecoule() >= 1.0 && System.nanoTime() - clicNs > 1_000_000_000L) {
                    approcher(b);
                    Entity vache = vu(VACHES.get(n));
                    if (vache != null) {
                        mc.gameMode.interactAt(mc.player, vache, new EntityHitResult(vache), InteractionHand.MAIN_HAND);
                        var r = mc.gameMode.interact(mc.player, vache, InteractionHand.MAIN_HAND);
                        LOG.info("[e2e] clic droit sur la vache {} : {}", n + 1, r);
                    }
                    clicNs = System.nanoTime();
                }
                return false;
            }, () -> "vache " + mob(VACHES.get(n)), () -> "vache " + (n + 1) + " nourrie de ble par un vrai clic droit : "
                    + mob(VACHES.get(n)).court() + ", en amour (ble en main : " + compter(Items.WHEAT) + " restant)");
        }
        pasD(90, quand + "Les deux vaches font un veau", () -> {}, () -> chercher(b -> "cow".equals(b.type()) && b.bebe()
                && !VACHES.contains(b.uuid()) && b.vivant()) != null,
                () -> "mobs " + mobs(), () -> {
                    Bete b = chercher(x -> "cow".equals(x.type()) && x.bebe() && !VACHES.contains(x.uuid()) && x.vivant());
                    veau = b.uuid();
                    Bete v1 = mob(VACHES.get(0));
                    Bete v2 = mob(VACHES.get(1));
                    if (b.sauvage() || !"aucune".equals(b.origine()) || !v1.sauvage() || !v2.sauvage()) {
                        throw new IllegalStateException("veau " + b + " ; parents " + v1 + ", " + v2);
                    }
                    return "veau ne de l'elevage en " + String.format(java.util.Locale.ROOT, "%.1f", ecoule()) + " s : " + b.court()
                            + " (aucun evenement d'apparition, aucune marque) ; les parents restent sauvages";
                });
        pas(quand + "Le veau grandit (e2ebutin grandir)", () -> reponse = rcon("e2ebutin grandir " + veau), () -> !mob(veau).bebe(),
                () -> "veau " + mob(veau), () -> {
                    Bete b = mob(veau);
                    if (b.sauvage()) {
                        throw new IllegalStateException("veau grandi marque : " + b);
                    }
                    return "veau grandi : " + b.court() + " ; toujours sans marque";
                });
    }

    private static long clicNs;

    /** Dev, en survie, sans être opérateur, reçoit ce qu'il faut pour les gestes. */
    private static void outils(String quand) {
        pas(quand + "Dev, en survie et non opérateur, reçoit une épée de diamant et du blé", () -> {
            rcon("gamemode survival " + nom());
            rcon("deop " + nom());
            rcon("give " + nom() + " minecraft:diamond_sword 1");
            rcon("give " + nom() + " minecraft:wheat 4");
            placerJoueur(centre(), 0, 20);
        }, () -> ecoule() >= 1.5 && !operateur() && compter(Items.DIAMOND_SWORD) >= 1 && compter(Items.WHEAT) >= 4 && pres(centre()),
                () -> "operateur " + operateur() + ", epee " + compter(Items.DIAMOND_SWORD) + ", ble " + compter(Items.WHEAT) + ", joueur en "
                        + mc.player.blockPosition(), () -> {
                    airePrete = true;
                    return "Dev en survie (" + mc.gameMode.getPlayerMode() + "), operateur = " + operateur() + ", epee et ble en main";
                });
    }

    /** Range ce que l'étape a ajouté à l'inventaire de Dev (jamais plus), d'après le relevé du début. */
    private static String rangerInventaire() {
        Map<String, Integer> maintenant = inventaire();
        List<String> retires = new ArrayList<>();
        for (Map.Entry<String, Integer> e : maintenant.entrySet()) {
            int plus = e.getValue() - inventaireAvant.getOrDefault(e.getKey(), 0);
            if (plus > 0 && !e.getKey().startsWith("oneforallsurvival:")) {
                rcon("clear " + nom() + " " + e.getKey() + " " + plus);
                retires.add(e.getKey() + " x" + plus);
            }
        }
        return String.join(", ", retires);
    }

    // ================================================================================================
    // Premier passage
    // ================================================================================================

    static void premierePhase() {
        Map<String, String> avant = new LinkedHashMap<>();
        pas("Le joueur, en spectateur, se rend à l'aire du butin (loin de tout ; les chunks se chargent)", () -> {
            fermer();
            depart = mc.player.blockPosition();
            inventaireAvant = inventaire();
            BlockPos spawn = mc.level.getSharedSpawnPos();
            int x = depart.getX() + 100;
            int z = depart.getZ() + 640;
            if (Math.abs(x - spawn.getX()) <= 40 && Math.abs(z - spawn.getZ()) <= 40) {
                x = depart.getX() - 180;
            }
            o = new BlockPos(x, Y_AIRE, z);
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 6.5) + " " + (o.getY() + 2) + " " + (o.getZ() + 4.5));
        }, () -> ecoule() >= 4 && chunksPrets(centre()), () -> "chunks de l'aire non recus, joueur en " + mc.player.blockPosition(),
                () -> "aire du butin en " + o.toShortString() + " (joueur parti de " + depart.toShortString() + ") ; Dev porte "
                        + valeurPortee() + " cuivres, inventaire " + inventaireAvant);
        pas("Origines du monde neuf : un animal apparu à la génération des chunks est marqué sauvage", () -> {
            reponse = rcon("e2ebutin origines");
        }, () -> ecoule() >= 0.3, () -> "", () -> {
            Map<String, String> r = lire(reponse);
            if (Long.parseLong(r.get("generationMarques")) < 1 || !r.get("generation").equals(r.get("generationMarques"))) {
                throw new IllegalStateException(reponse);
            }
            return "origines : " + reponse.replace(" FIN_E2E", "").trim() + " (tous les animaux nes a la generation des chunks sont marques)";
        });
        pas("Aire close et couverte (coque de pierre) ; règles relevées ; difficulté facile, naissances coupées ; table certaine", () -> {
            difficulteAvant = dernierMot(rcon("difficulty"));
            naissancesAvant = dernierMot(rcon("gamerule doMobSpawning"));
            rcon("difficulty easy");
            rcon("gamerule doMobSpawning false");
            rcon("kill @e[type=!player,x=" + (o.getX() - 1) + ",y=" + (o.getY() - 1) + ",z=" + (o.getZ() - 1) + ",dx=14,dy=5,dz=10]");
            rcon("fill " + boite() + " stone hollow");
            avant.putAll(registre());
            reponse = rcon("e2ebutin certain on");
        }, () -> mc.level.getBlockState(p(-1, 2, 4)).is(Blocks.STONE) && mc.level.getBlockState(p(6, 4, 4)).is(Blocks.STONE)
                && mc.level.getBlockState(p(6, 1, 4)).isAir() && ecoule() >= 1.0,
                () -> "mur " + mc.level.getBlockState(p(-1, 2, 4)) + ", toit " + mc.level.getBlockState(p(6, 4, 4)), () -> {
                    if (!reponse.contains("CERTAIN on")) {
                        throw new IllegalStateException(reponse);
                    }
                    return "aire close de pierre en " + boite() + " ; difficulte avant " + difficulteAvant + ", naissances " + naissancesAvant
                            + " ; table certaine (" + reponse.replace(" FIN_E2E", "").trim() + ") ; registre " + avant;
                });
        outils("");
        pas("Zombies A et D (apparition NATURAL, D gardé), deux vaches NATURAL", () -> {
            zombieA = lire(rcon("e2ebutin apparaitre minecraft:zombie " + xyz(p(2, 0, 2)) + " NATURAL")).get("m");
            zombieD = lire(rcon("e2ebutin apparaitre minecraft:zombie " + xyz(p(11, 0, 7)) + " NATURAL garde")).get("m");
            VACHES.clear();
            VACHES.add(Bete.lire(lire(rcon("e2ebutin apparaitre minecraft:cow " + xyz(p(9, 0, 2)) + " NATURAL")).get("m")).uuid());
            VACHES.add(Bete.lire(lire(rcon("e2ebutin apparaitre minecraft:cow " + xyz(p(10, 0, 3)) + " NATURAL")).get("m")).uuid());
            Bete a = Bete.lire(zombieA);
            Bete d = Bete.lire(zombieD);
            zombieA = a.uuid();
            zombieD = d.uuid();
            reponse = rcon("e2ebutin figer " + boite());
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            Bete a = mob(zombieA);
            Bete d = mob(zombieD);
            Bete v1 = mob(VACHES.get(0));
            Bete v2 = mob(VACHES.get(1));
            if (!a.sauvage() || !"NATURAL".equals(a.origine()) || !d.sauvage() || !d.garde() || !v1.sauvage() || !v2.sauvage()
                    || v1.bebe() || v2.bebe()) {
                throw new IllegalStateException(a + " ; " + d + " ; " + v1 + " ; " + v2);
            }
            return "A " + a.court() + " ; D " + d.court() + ", garde=" + d.garde() + " ; vaches " + v1.court() + ", " + v2.court()
                    + " ; zombies sans IA (" + reponse.replace(" FIN_E2E", "").trim() + ")";
        });
        pasD(40, "Zombie B : un vrai bloc de générateur, puis le bloc retiré", () -> {
            rcon("setblock " + xyz(generateur()) + " minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}},"
                    + "Delay:20s,MinSpawnDelay:400s,MaxSpawnDelay:800s,SpawnCount:1s,SpawnRange:1s,MaxNearbyEntities:16s,"
                    + "RequiredPlayerRange:16s}");
        }, () -> chercher(b -> "zombie".equals(b.type()) && "SPAWNER".equals(b.origine())) != null,
                () -> "bloc " + mc.level.getBlockState(generateur()) + ", mobs " + mobs(), () -> {
                    rcon("setblock " + xyz(generateur()) + " air");
                    rcon("e2ebutin figer " + boite());
                    Bete b = chercher(x -> "zombie".equals(x.type()) && "SPAWNER".equals(x.origine()));
                    zombieB = b.uuid();
                    long sortis = mobs().stream().filter(x -> "SPAWNER".equals(x.origine())).count();
                    if (b.sauvage() || sortis != 1) {
                        throw new IllegalStateException(b + " ; sortis du generateur " + sortis);
                    }
                    return "B sorti du generateur en " + String.format(java.util.Locale.ROOT, "%.1f", ecoule()) + " s : " + b.court()
                            + " ; bloc retire";
                });
        pas("Zombie C : Dev pose un œuf d'apparition d'un vrai clic droit", () -> {
            rcon("give " + nom() + " minecraft:zombie_spawn_egg 1");
        }, () -> ecoule() >= 1.0 && compter(Items.ZOMBIE_SPAWN_EGG) == 1, () -> "oeufs " + compter(Items.ZOMBIE_SPAWN_EGG), () -> {
            tenir(s -> s.is(Items.ZOMBIE_SPAWN_EGG));
            placerJoueur(poseOeuf().north(2), 0, 50);
            return "oeuf d'apparition recu";
        });
        pas("Clic droit sur le sol avec l'œuf", () -> {
            tenir(s -> s.is(Items.ZOMBIE_SPAWN_EGG));
            clic(poseOeuf().below(), Direction.UP);
        }, () -> chercher(b -> "zombie".equals(b.type()) && "SPAWN_EGG".equals(b.origine())) != null,
                () -> "oeufs " + compter(Items.ZOMBIE_SPAWN_EGG) + ", mobs " + mobs(), () -> {
                    rcon("e2ebutin figer " + boite());
                    Bete c = chercher(x -> "zombie".equals(x.type()) && "SPAWN_EGG".equals(x.origine()));
                    zombieC = c.uuid();
                    if (c.sauvage()) {
                        throw new IllegalStateException("C marque : " + c);
                    }
                    return "C ne de l'oeuf pose par Dev : " + c.court() + " ; oeufs restants " + compter(Items.ZOMBIE_SPAWN_EGG);
                });
        elevage("");
        tuerEtRamasser("le zombie A", () -> zombieA);
        pas("Registre relevé avant les morts qui ne donnent rien", () -> registreAvant = registre(), () -> ecoule() >= 0.3, () -> "", null);
        tuer("Dev tue le zombie B (générateur)", () -> zombieB, null);
        rienAuSol("le zombie B (generateur)");
        tuer("Dev tue le zombie C (œuf)", () -> zombieC, null);
        rienAuSol("le zombie C (oeuf)");
        tuer("Dev tue le veau grandi", () -> veau, null);
        rienAuSol("le veau grandi (elevage)");
        pas("État d'avant l'arrêt écrit (ligne « Butin des créatures », /eco loot, zombie D)", () -> {}, () -> ecoule() >= 0.3, () -> "", () -> {
            Map<String, String> r = registre();
            Bete d = mob(zombieD);
            if (d == null || !d.vivant() || !d.sauvage() || !d.garde()) {
                throw new IllegalStateException("zombie D " + d);
            }
            String loot = rcon("eco loot").trim().replace('\n', '|');
            List<String> lignes = new ArrayList<>();
            lignes.add("origine=" + xyz(o));
            lignes.add("depart=" + xyz(depart));
            lignes.add("zombieD=" + zombieD);
            lignes.add("vaches=" + String.join(",", VACHES));
            lignes.add("veau=" + veau);
            lignes.add("ligne=" + sansCrochets(r.get("ligne")));
            lignes.add("total=" + r.get("total"));
            lignes.add("morts=" + r.get("morts"));
            lignes.add("loot=" + loot);
            try {
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return "avant l'arret : " + FICHIER + " ecrit : zombie D " + d.court() + " ; ligne « " + sansCrochets(r.get("ligne")).trim()
                    + " » ; /eco loot " + loot;
        });
        rangerAvantTresor();
    }

    /** Dev quitte l'aire, range ses pièces dans son coffre-fort (l'étape du trésor part d'un Dev qui ne porte rien). */
    private static void rangerAvantTresor() {
        pas("Dev quitte l'aire du butin et va à son coffre-fort ; table rendue", () -> {
            airePrete = false;
            reponse = rcon("e2ebutin certain off");
            BlockPos coffre = E2eMonnaie.coffreFortNote(mc);
            rcon("tp " + nom() + " " + (coffre.getX() + 0.5) + " " + coffre.getY() + " " + (coffre.getZ() + 2.5) + " 180 40");
        }, () -> {
            BlockPos coffre = E2eMonnaie.coffreFortNote(mc);
            return ecoule() >= 2 && pres(coffre.south(2)) && mc.level.getBlockState(coffre).is(EconomyRegistry.SAFE.get());
        }, () -> "joueur en " + mc.player.blockPosition(), () -> "Dev devant son coffre-fort ; " + reponse.replace(" FIN_E2E", "").trim());
        pas("Clic droit sur son coffre-fort", () -> {
            tenirVide();
            clic(E2eMonnaie.coffreFortNote(mc), Direction.SOUTH);
        }, () -> ecranOuvert(SafeMenu.class) && ecoule() >= 1.0, () -> "ecran " + mc.screen, null);
        pas("Clic sur « Déposer tout » : Dev ne porte plus rien", () -> {
            coffreAvant = menuPieces().total();
            porteAvant = valeurPortee();
            clicElement("deposit_all");
        }, () -> ecranOuvert(SafeMenu.class) && valeurPortee() == 0 && menuPieces().total() == coffreAvant + porteAvant && ecoule() >= 0.5,
                () -> "porte " + valeurPortee() + ", coffre-fort " + menuPieces().total(),
                () -> "Dev range " + porteAvant + " cuivres (le butin) : coffre-fort " + coffreAvant + " -> " + menuPieces().total());
        pas("Dev ne garde que ses objets d'avant l'étape ; règles du monde rendues ; il rentre, opérateur", () -> {
            fermer();
            reponse = rangerInventaire();
            rcon("difficulty " + difficulteAvant);
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("op " + nom());
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && operateur() && mc.player.blockPosition().distSqr(depart) < 9 && inventaire().equals(inventaireAvant),
                () -> "inventaire " + inventaire() + " (avant " + inventaireAvant + "), operateur " + operateur(),
                () -> "objets de l'etape retires (" + reponse + ") ; inventaire comme avant l'etape ; difficulte " + difficulteAvant
                        + ", naissances " + naissancesAvant + " ; Dev rentre, operateur");
    }

    // ================================================================================================
    // Après un arrêt et une relance du serveur (en dernier)
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
        pas("Relance : état d'avant l'arrêt relu ; /eco loot et la ligne « Butin des créatures » identiques", () -> {
            lu.putAll(lireFichier());
            fermer();
            o = position(lu.get("origine"));
            depart = mc.player.blockPosition();
            inventaireAvant = inventaire();
            zombieD = lu.get("zombieD");
            VACHES.clear();
            VACHES.addAll(List.of(lu.get("vaches").split(",")));
        }, () -> ecoule() >= 0.3, () -> "", () -> {
            Map<String, String> r = registre();
            String stats = rcon("eco stats");
            String loot = rcon("eco loot").trim().replace('\n', '|');
            if (!loot.equals(lu.get("loot")) || !sansCrochets(r.get("ligne")).equals(lu.get("ligne")) || !stats.contains(lu.get("ligne"))
                    || !r.get("total").equals(lu.get("total")) || !r.get("morts").equals(lu.get("morts"))) {
                throw new IllegalStateException("loot " + loot + " (avant " + lu.get("loot") + ") ; registre " + r + " ; stats "
                        + stats.replace('\n', '|'));
            }
            return "relance du butin : /eco loot identique (" + loot + ") ; ligne « " + lu.get("ligne").trim() + " » relue dans /eco stats";
        });
        pas("Relance : table certaine remise (l'override ne survit pas à l'arrêt) ; difficulté facile ; Dev va à l'aire", () -> {
            difficulteAvant = dernierMot(rcon("difficulty"));
            naissancesAvant = dernierMot(rcon("gamerule doMobSpawning"));
            reponse = rcon("e2ebutin certain on");
            rcon("difficulty easy");
            rcon("gamerule doMobSpawning false");
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 6.5) + " " + (o.getY() + 2) + " " + (o.getZ() + 4.5));
        }, () -> ecoule() >= 4 && chunksPrets(centre()) && mc.level.getBlockState(p(6, 4, 4)).is(Blocks.STONE),
                () -> "chunks non recus, joueur en " + mc.player.blockPosition(), () -> "relance : aire relue en " + o.toShortString()
                        + " ; " + reponse.replace(" FIN_E2E", "").trim() + " ; difficulte avant " + difficulteAvant);
        pas("Relance : le zombie D, marqué avant l'arrêt, est relu depuis le chunk", () -> {}, () -> mob(zombieD) != null,
                () -> "zombie D absent ; mobs " + mobs(), () -> {
                    Bete d = mob(zombieD);
                    if (!d.vivant() || !d.sauvage() || !d.garde()) {
                        throw new IllegalStateException("zombie D " + d);
                    }
                    rcon("e2ebutin figer " + boite());
                    return "relance : zombie D relu du disque : " + d.court() + ", garde=" + d.garde();
                });
        outils("Relance : ");
        tuerEtRamasser("le zombie D (relu)", () -> zombieD);
        pas("Relance : les parents sont rendus prêts à un nouvel élevage (e2ebutin grandir)", () -> {
            VACHES.forEach(v -> rcon("e2ebutin grandir " + v));
        }, () -> ecoule() >= 0.5, () -> "", () -> "parents : " + mob(VACHES.get(0)).court() + ", " + mob(VACHES.get(1)).court());
        elevage("Relance : ");
        pas("Relance : registre relevé avant la mort du second veau", () -> registreAvant = registre(), () -> ecoule() >= 0.3, () -> "", null);
        tuer("Relance : Dev tue le second veau grandi", () -> veau, null);
        rienAuSol("le second veau grandi (ne apres la relance)");
        pas("Relance : table rendue, aire rangée, objets de l'étape retirés ; Dev rentre, opérateur", () -> {
            airePrete = false;
            reponse = rcon("e2ebutin certain off");
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
            rcon("kill @e[type=!player,x=" + (o.getX() - 1) + ",y=" + (o.getY() - 1) + ",z=" + (o.getZ() - 1) + ",dx=14,dy=5,dz=10]");
            rcon("fill " + boite() + " air");
            String retires = rangerInventaire();
            rcon("difficulty " + difficulteAvant);
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("op " + nom());
            reponse = reponse.replace(" FIN_E2E", "").trim() + " ; retires " + retires;
        }, () -> ecoule() >= 1.5 && operateur() && mc.player.blockPosition().distSqr(depart) < 9, () -> "joueur en " + mc.player.blockPosition(),
                () -> "relance du butin finie : " + reponse + " ; Dev garde les " + valeurPortee() + " cuivres du butin (son coffre-fort a ete range par l'etape du tresor)");
    }

    // ================================================================================================
    // Arrêt
    // ================================================================================================

    /** Avant le {@code /stop}, même après un échec : table rendue, joueur opérateur, règles du monde rendues. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null || o == null) {
            return;
        }
        mc = minecraft;
        try {
            rcon("op " + nom());
            rcon("e2ebutin certain off");
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("difficulty " + difficulteAvant);
        } catch (RuntimeException e) {
            LOG.error("[e2e] rcon impossible avant l'arret", e);
        }
    }
}
