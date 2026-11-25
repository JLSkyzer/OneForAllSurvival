package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.OperatorActionPacket.Action;
import dev.oneforallsurvival.phone.net.OperatorView;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout;
import dev.oneforallsurvival.phone.net.client.OperatorPanelScreen;
import java.awt.image.BufferedImage;
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
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Étape « téléphone » de {@link E2eClient} (lot T2, tâche 13 ; développement seulement, jamais empaquetée) : le réseau
 * téléphonique vu par un vrai client contre un serveur dédié avec le pack complet.
 *
 * <p><b>Ordre.</b> Au premier passage, juste avant l'étape du trésor (E3) : elle note le tarif du lendemain avant que le
 * trésor n'avance l'horloge de l'économie de deux minuits, qui le font entrer en vigueur au démarrage de la relance. À la
 * relance, après l'électricité (E6) et avant les quêtes, qui restent les dernières. Elle ne crée aucune pièce : ses joueurs
 * simulés ({@code Fondatrice}, {@code Usager}, {@code Voisin}) entrent sans somme de départ ; elle ne prend pas
 * {@code Abonne}, dont l'étape de l'électricité attend la première entrée (leçon du 2026-11-15).
 *
 * <p><b>Aire</b> à part, close, couverte, éclairée, loin du départ : la boîte A (Mairie, l'allée de Dev) et la boîte B
 * (Lagon), chaque antenne sur la première colonne de son chunk, Lagon à 48 blocs (un autre chunk). Dev (ingénieur, survie,
 * non opérateur) pose Mairie d'un vrai clic, la nomme et règle tout au panneau par de vrais clics ; la console bâtit,
 * alimente et fait jouer les joueurs simulés ({@link E2eTelephoneServeur}).
 */
final class E2eTelephone {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-telephone.txt";
    private static final String CAPTURES = "captures-t2";
    private static final double DELAI = 25;
    private static final int Y_AIRE = 150;
    private static final String MAIRIE = "Mairie";
    private static final String LAGON = "Lagon";
    private static final String FONDATRICE = "Fondatrice";
    private static final String USAGER = "Usager";
    private static final String VOISIN = "Voisin";
    private static final long TARIF = 7;
    private static final String OUTIL = "oneforallsurvival:maintenance_tool";
    private static final String ANTENNE = "oneforallsurvival:antenna";
    private static final String PANNEAU = "message.oneforallsurvival.operator_panel.";
    private static final Pattern CLE_VALEUR = Pattern.compile("(\\w+)=(\\[[^\\]]*\\]|\\{[^}]*\\}|[^ ]+)");

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;

    private static Minecraft mc;
    private static BlockPos depart;
    private static BlockPos o;
    private static boolean airePrete;
    private static final List<String> CHAT = new ArrayList<>();

    private static String reponse = "";
    private static List<String> casesAvant = List.of();
    private static int tenueAvant;
    private static long porteAvant;
    private static String difficulteAvant = "peaceful";
    private static String naissancesAvant = "true";
    private static String roleDevAvant = "citoyen";
    private static String maireAvant = "aucun";
    /** La seconde main de Dev était vide au départ : l'étape y met l'outil de maintenance, elle la vide en sortant. */
    private static boolean secondeMainAvant;
    /** Vrai entre la mise en survie de Dev et son retour : un échec dans cet intervalle laisse ses droits à rendre. */
    private static boolean enCours;
    private static int chatAvant;
    private static int cartesAvant;
    private static String idDemandeUsager = "";
    private static String idDemandeVoisin = "";
    private static String idAnonyme = "";
    private static String idM2 = "";
    private static String idM = "";
    private static long fondee;
    private static int largeurAvant;
    private static int hauteurAvant;
    private static int echelleAvant;
    private static final Map<String, String> VU = new LinkedHashMap<>();

    private E2eTelephone() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape du téléphone";
    }

    static void recevoir(Component message) {
        cles(message, CHAT);
    }

    private static void cles(Component c, List<String> out) {
        if (c.getContents() instanceof TranslatableContents t) {
            out.add(t.getKey());
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component inner) {
                    cles(inner, out);
                }
            }
        }
        for (Component enfant : c.getSiblings()) {
            cles(enfant, out);
        }
    }

    // ================================================================================================
    // Disposition : boîte A, intérieur (0, 0, 0) à (14, 3, 8) ; boîte B, intérieur (48, 0, 0) à (54, 3, 8)
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static BlockPos mairie() {
        return p(2, 0, 4);
    }

    /**
     * La source de Mairie, à deux blocs de l'antenne vers +x (un bloc d'air entre elles, même chunk) : elle ne la touche
     * pas, l'antenne ne reçoit que par le fil (revue de la tâche 13, C1 : collée, elle la remplissait directement).
     */
    private static BlockPos sourceMairie() {
        return p(4, 0, 2);
    }

    private static BlockPos mairie2() {
        return p(5, 0, 1);
    }

    private static BlockPos lagon() {
        return p(50, 0, 4);
    }

    private static BlockPos sourceLagon() {
        return p(50, 0, 3);
    }

    private static BlockPos alleeA() {
        return p(5, 0, 4);
    }

    private static BlockPos alleeB() {
        return p(53, 0, 4);
    }

    // ================================================================================================
    // Déroulement (même mécanique que les étapes des quêtes, du trésor et de l'électricité)
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

    private static String court(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
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
        return s != null && s.length() >= 2 && (s.startsWith("[") || s.startsWith("{")) ? s.substring(1, s.length() - 1) : s;
    }

    private static List<String> liste(String s) {
        List<String> out = new ArrayList<>();
        for (String x : sansCrochets(s == null ? "" : s).split(";")) {
            if (!x.isBlank()) {
                out.add(x);
            }
        }
        return out;
    }

    private static long num(Map<String, String> m, String cle) {
        String v = m.get(cle);
        if (v == null) {
            throw new IllegalStateException("cle " + cle + " absente de " + m);
        }
        return Long.parseLong(v);
    }

    private static String sans(String reponseRcon) {
        return reponseRcon.replace(" FIN_E2E", "").trim();
    }

    private static Map<String, String> operateurLu(String op) {
        return lire(rcon("e2etel operateur " + op));
    }

    private static Map<String, String> antenneLue(BlockPos pos) {
        return lire(rcon("e2etel antenne " + xyz(pos)));
    }

    private static Map<String, String> etatDe(String qui) {
        return lire(rcon("e2etel etat " + qui));
    }

    /** Les antennes d'un opérateur lu : position → [en service, détail, FE du jour, principale]. */
    private static Map<String, String[]> antennesDe(Map<String, String> op) {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (String a : liste(op.get("antennes"))) {
            String[] t = a.split(":");
            out.put(t[0], new String[] {t[1], t[2], t[3], t[4]});
        }
        return out;
    }

    private static Path fichier() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FICHIER);
    }

    private static Map<String, String> lireFichier() {
        Map<String, String> lu = new LinkedHashMap<>();
        try {
            for (String l : Files.readAllLines(fichier(), StandardCharsets.UTF_8)) {
                int i = l.indexOf('=');
                if (i > 0) {
                    lu.put(l.substring(0, i), l.substring(i + 1));
                }
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

    private static boolean pres(BlockPos bloc) {
        return Math.abs(mc.player.getX() - (bloc.getX() + 0.5)) < 1.0 && Math.abs(mc.player.getZ() - (bloc.getZ() + 0.5)) < 1.0
                && Math.abs(mc.player.getY() - bloc.getY()) < 2.5;
    }

    private static void placerJoueur(BlockPos bloc, float yaw, float pitch) {
        rcon("tp " + nom() + " " + (bloc.getX() + 0.5) + " " + bloc.getY() + " " + (bloc.getZ() + 0.5) + " " + yaw + " " + pitch);
    }

    private static String tr(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    private static String fmt(long cuivres) {
        return CoinMath.format(cuivres).getString();
    }

    private static long valeurPortee() {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s) + dev.oneforallsurvival.economy.Purse.total(s);
        }
        return n + CoinMath.valueOf(mc.player.getOffhandItem());
    }

    private static List<String> cases() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            boolean argent = CoinMath.valueOf(s) > 0 || s.getItem() instanceof dev.oneforallsurvival.economy.PurseItem;
            out.add(s.isEmpty() || argent ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()) + "x" + s.getCount());
        }
        return out;
    }

    /** Retire case par case ce que l'étape a mis dans une case vide au départ, et rend la case tenue (leçon du 2026-11-19). */
    private static String rangerInventaire() {
        List<String> maintenant = cases();
        List<String> retires = new ArrayList<>();
        for (int i = 0; i < 36 && i < casesAvant.size(); i++) {
            if (casesAvant.get(i).isEmpty() && !maintenant.get(i).isEmpty()) {
                rcon("item replace entity " + nom() + " container." + i + " with minecraft:air");
                retires.add(maintenant.get(i) + " (case " + i + ")");
            }
        }
        mc.player.getInventory().selected = tenueAvant;
        return String.join(", ", retires);
    }

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

    private static void tenir(String id) {
        int f = fente(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id));
        if (f < 0) {
            throw new IllegalStateException(id + " absent de l'inventaire");
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

    private static int compter(String id) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void clic(BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        LOG.info("[e2e] clic droit en {} face {}, main {}, seconde main {} : {}", pos, face, mc.player.getMainHandItem(),
                mc.player.getOffhandItem(), res);
    }

    private static void fermer() {
        if (mc.screen instanceof OperatorPanelScreen s) {
            s.onClose();
        }
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        mc.setScreen(null);
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

    private static String dernierMot(String texte) {
        String[] t = texte.trim().split("\\s+");
        return t[t.length - 1].toLowerCase(java.util.Locale.ROOT);
    }

    private static int chatCompte(String cle, int depuis) {
        int n = 0;
        for (int i = depuis; i < CHAT.size(); i++) {
            if (CHAT.get(i).equals(cle)) {
                n++;
            }
        }
        return n;
    }

    private static String capturer(String nom) {
        Path dossier = mc.gameDirectory.toPath().resolve(CAPTURES);
        Path fichier = dossier.resolve(nom + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(dossier);
            int l = image.getWidth();
            int h = image.getHeight();
            BufferedImage img = new BufferedImage(l, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < l; x++) {
                    int px = image.getPixelRGBA(x, y);
                    img.setRGB(x, y, ((px & 0xFF) << 16) | (px & 0xFF00) | ((px >> 16) & 0xFF));
                }
            }
            ImageIO.write(img, "png", fichier.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("capture impossible : " + fichier, e);
        }
        LOG.info("[e2e] capture {}", fichier);
        return fichier.toString();
    }

    // ================================================================================================
    // Le panneau de l'opérateur (vrais clics : méthodes de l'écran, comme la souris)
    // ================================================================================================

    private static OperatorPanelScreen panneau() {
        if (!(mc.screen instanceof OperatorPanelScreen s)) {
            throw new IllegalStateException("panneau ferme : ecran " + mc.screen);
        }
        return s;
    }

    private static boolean panneauOuvert() {
        return mc.screen instanceof OperatorPanelScreen;
    }

    private static OperatorView vue() {
        return panneau().view();
    }

    private static void cliquerEn(@Nullable double[] c, String quoi) {
        if (c == null) {
            throw new IllegalStateException("commande absente du panneau : " + quoi);
        }
        mc.screen.mouseMoved(c[0], c[1]);
        boolean pris = mc.screen.mouseClicked(c[0], c[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        mc.screen.mouseReleased(c[0], c[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        LOG.info("[e2e] clic gauche sur {} du panneau en ({}, {}) : {}", quoi, (int) c[0], (int) c[1], pris);
    }

    private static void onglet(OperatorPanelLayout.Tab t) {
        cliquerEn(panneau().centreOf(t, null, ""), "onglet " + t);
    }

    private static void bouton(Action action, String arg) {
        cliquerEn(panneau().centreOf(null, action, arg), action + " " + arg);
    }

    private static void taper(String texte) {
        for (char c : texte.toCharArray()) {
            mc.screen.charTyped(c, 0);
        }
    }

    private static String antennesVues(OperatorView v) {
        StringBuilder sb = new StringBuilder();
        for (OperatorView.AntennaLine a : v.antennas()) {
            sb.append(court(a.pos())).append(' ').append(a.state()).append(' ').append(a.load().standby()).append('+')
                    .append(a.load().clients()).append('+').append(a.load().traffic()).append(a.main() ? " principale" : "").append("; ");
        }
        return sb.toString().trim();
    }

    /** Dev, à {@code ou}, ouvre l'antenne en {@code antenne} d'un vrai clic droit, main vide. */
    private static void ouvrirPanneau(String quand, Supplier<BlockPos> ou, float yaw, Supplier<BlockPos> antenne) {
        pas(quand + "Dev se place devant l'antenne", () -> {
            fermer();
            placerJoueur(ou.get(), yaw, 20);
        }, () -> ecoule() >= 0.8 && pres(ou.get()), () -> "joueur en " + mc.player.blockPosition(), null);
        pas(quand + "Clic droit sur l'antenne, main vide : le panneau s'ouvre", () -> {
            tenirVide();
            clic(antenne.get(), Direction.EAST);
        }, () -> panneauOuvert() && ecoule() >= 1.0, () -> "ecran " + mc.screen, () -> "panneau ouvert : « " + vue().name() + " », "
                + vue().antennas().size() + " antenne(s), droits " + vue().rights());
    }

    /** Le texte du panneau à cette taille : textes coupés et boutons hors du cadre, avec les textes du jeu. */
    private static String miseEnPage(int l, int h) {
        OperatorPanelLayout.Texts textes = new OperatorPanelLayout.Texts() {
            @Override
            public String t(String key, Object... args) {
                return I18n.get(key, args);
            }

            @Override
            public String coins(long amount) {
                return CoinMath.format(amount).getString();
            }

            @Override
            public String number(long value) {
                return Long.toString(value);
            }
        };
        OperatorPanelLayout.Layout lay = OperatorPanelLayout.of(l, h, mc.font::width, vue(), textes, panneau().status());
        int coupes = 0;
        int dehors = 0;
        for (OperatorPanelLayout.Pane pane : lay.panes()) {
            for (OperatorPanelLayout.Row r : pane.rows()) {
                for (OperatorPanelLayout.Text t : r.texts()) {
                    coupes += t.cut() ? 1 : 0;
                }
                for (OperatorPanelLayout.Button b : r.buttons()) {
                    coupes += b.label().cut() ? 1 : 0;
                    dehors += lay.frame().contains(b.box()) ? 0 : 1;
                }
            }
        }
        // Revue de la tâche 13 : un bouton hors du cadre fait échouer le pas (un texte coupé, lui, a sa bulle).
        if (dehors > 0 || lay.frame().w() > l || lay.frame().h() > h) {
            throw new IllegalStateException(dehors + " bouton(s) hors du cadre a " + l + " x " + h + " (cadre " + lay.frame() + ")");
        }
        return "cadre " + lay.frame().w() + "x" + lay.frame().h() + ", " + coupes + " texte(s) coupe(s) (en bulle), " + dehors
                + " bouton(s) hors du cadre";
    }

    // ================================================================================================
    // Les gestes répétés
    // ================================================================================================

    private static void relever(String quand) {
        pas(quand + "Départ de l'étape : position, inventaire, règles du monde, rôle de Dev et maire relevés", () -> {
            fermer();
            depart = mc.player.blockPosition();
            casesAvant = cases();
            tenueAvant = mc.player.getInventory().selected;
            porteAvant = valeurPortee();
            difficulteAvant = dernierMot(rcon("difficulty"));
            naissancesAvant = dernierMot(rcon("gamerule doMobSpawning"));
            Map<String, String> m = lire(rcon("e2etresor maire"));
            roleDevAvant = m.getOrDefault("roleDev", "citoyen");
            maireAvant = m.getOrDefault("nom", "aucun");
            secondeMainAvant = mc.player.getOffhandItem().isEmpty();
        }, () -> ecoule() >= 0.3, () -> "", () -> "depart " + depart.toShortString() + " ; porte " + porteAvant + " ; difficulte "
                + difficulteAvant + ", naissances " + naissancesAvant + ", role de Dev " + roleDevAvant + ", maire " + maireAvant);
    }

    private static void allerALAire(String quand) {
        pas(quand + "Le joueur, en spectateur, se rend à l'aire du téléphone (loin de tout ; les chunks se chargent)", () -> {
            fermer();
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 7.5) + " " + (o.getY() + 1) + " " + (o.getZ() + 4.5));
        }, () -> ecoule() >= 4 && chunksPrets(alleeA()), () -> "chunks de l'aire non recus, joueur en " + mc.player.blockPosition(),
                () -> "aire du telephone en " + o.toShortString() + " (joueur parti de " + depart.toShortString() + ")");
    }

    private static void survie(String quand) {
        pas(quand + "Dev, ingénieur, en survie et non opérateur", () -> {
            enCours = true;
            if (DEV.equals(maireAvant)) {
                rcon("role mayor remove");
            }
            rcon("role set " + nom() + " ingenieur");
            rcon("gamemode survival " + nom());
            rcon("deop " + nom());
            placerJoueur(alleeA(), 90, 20);
        }, () -> ecoule() >= 1.5 && !operateur() && pres(alleeA())
                && mc.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SURVIVAL,
                () -> "operateur " + operateur() + ", joueur en " + mc.player.blockPosition() + ", mode " + mc.gameMode.getPlayerMode(),
                () -> {
                    airePrete = true;
                    Map<String, String> m = lire(rcon("e2etresor maire"));
                    return "Dev en survie, operateur = " + operateur() + ", role " + m.get("roleDev") + ", maire " + m.get("nom");
                });
    }

    private static final String DEV = "Dev";

    private static void rentrer(String quand) {
        pas(quand + "Dev quitte l'aire : objets de l'étape retirés case par case, règles du monde, rôle et maire rendus ; il "
                + "rentre, opérateur", () -> {
            airePrete = false;
            fermer();
            reponse = rangerInventaire();
            if (secondeMainAvant) {
                rcon("item replace entity " + nom() + " weapon.offhand with minecraft:air");
            }
            rcon("difficulty " + difficulteAvant);
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("role set " + nom() + " " + roleDevAvant);
            if (DEV.equals(maireAvant)) {
                rcon("role mayor set " + nom());
            }
            rcon("op " + nom());
            rcon("gamemode survival " + nom());
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && operateur() && mc.player.blockPosition().distSqr(depart) < 9 && cases().equals(casesAvant)
                && (!secondeMainAvant || mc.player.getOffhandItem().isEmpty()),
                () -> "cases " + cases() + " (avant " + casesAvant + "), operateur " + operateur(), () -> {
                    if (valeurPortee() != porteAvant) {
                        throw new IllegalStateException("Dev porte " + valeurPortee() + " (avant " + porteAvant + ")");
                    }
                    Map<String, String> m = lire(rcon("e2etresor maire"));
                    enCours = false;
                    return "objets de l'etape retires case par case (" + reponse + ") ; chaque case du sac et de la barre comme au depart ; "
                            + "seconde main vide ; Dev porte " + valeurPortee() + " cuivres comme avant ; role " + m.get("roleDev")
                            + ", maire " + m.get("nom") + " ; il rentre, operateur";
                });
    }

    // ================================================================================================
    // Premier passage
    // ================================================================================================

    static void premierePhase() {
        relever("");
        pas("Origine de l'aire choisie (antennes sur la première colonne de leur chunk)", () -> {
            int x = depart.getX() + 640;
            x -= Math.floorMod(x + 2, 16);
            o = new BlockPos(x, Y_AIRE, depart.getZ() + 640);
        }, () -> true, () -> "", () -> "Mairie en " + court(mairie()) + " (x mod 16 = " + Math.floorMod(mairie().getX(), 16) + "), Lagon en "
                + court(lagon()) + " (x mod 16 = " + Math.floorMod(lagon().getX(), 16) + ")");
        allerALAire("");
        pas("Deux aires closes, couvertes et éclairées (A : Mairie et l'allée de Dev ; B : Lagon, un autre chunk)", () -> {
            rcon("difficulty peaceful");
            rcon("gamerule doMobSpawning false");
            rcon("kill @e[type=!player,x=" + (o.getX() - 1) + ",y=" + (o.getY() - 1) + ",z=" + (o.getZ() - 1) + ",dx=57,dy=6,dz=11]");
            rcon("fill " + xyz(p(-1, -1, -1)) + " " + xyz(p(15, 4, 9)) + " stone hollow");
            rcon("fill " + xyz(p(47, -1, -1)) + " " + xyz(p(55, 4, 9)) + " stone hollow");
            for (int x = 1; x <= 13; x += 4) {
                rcon("setblock " + xyz(p(x, 4, 4)) + " glowstone");
                rcon("setblock " + xyz(p(x, 4, 7)) + " glowstone");
            }
            rcon("setblock " + xyz(p(52, 4, 4)) + " glowstone");
        }, () -> ecoule() >= 1.0 && mc.level.getBlockState(p(-1, 2, 4)).is(net.minecraft.world.level.block.Blocks.STONE)
                && mc.level.getBlockState(p(15, 4, 4)).is(net.minecraft.world.level.block.Blocks.STONE),
                () -> "murs " + mc.level.getBlockState(p(-1, 2, 4)), () -> "aires closes : A " + court(p(-1, -1, -1)) + " a "
                        + court(p(15, 4, 9)) + ", B " + court(p(47, -1, -1)) + " a " + court(p(55, 4, 9)));

        // --- Lagon : fondée par Fondatrice (simulée), sa source d'énergie créative, sa propre ligne.
        pas("Fondatrice (simulée) entre, pose son antenne et fonde « " + LAGON + " » par la console ; sa source est posée", () -> {
            reponse = rcon("e2etel entrer " + FONDATRICE + " " + xyz(p(52, 0, 6))) + " | "
                    + rcon("e2etel fonder " + FONDATRICE + " " + xyz(lagon()) + " " + LAGON) + " | "
                    + rcon("e2etel source " + xyz(sourceLagon()) + " true") + " | "
                    + rcon("e2etel cartes " + LAGON + " 40");
        }, () -> ecoule() >= 1.0, () -> "", () -> {
            String[] r = reponse.split(" \\| ");
            if (!"true".equals(lire(r[0]).get("sansDepart")) || !"FOUNDED".equals(lire(r[1]).get("resultat"))
                    || !r[2].contains("creative_energy") || num(lire(r[3]), "total") != 40) {
                throw new IllegalStateException(reponse);
            }
            return "Lagon : " + sans(reponse);
        });
        pasD(20, "Lagon en service (sa source la nourrit)", () -> {},
                () -> ecoule() >= 1.0 && "1".equals(operateurLu(LAGON).get("enService")),
                () -> "Lagon " + operateurLu(LAGON) + " ; antenne " + antenneLue(lagon()),
                () -> "Lagon en service : " + antenneLue(lagon()));
        pas("Fondatrice prend un téléphone, s'émet une carte de Lagon et la glisse", () -> {
            reponse = rcon("e2etel telephone " + FONDATRICE) + " | " + rcon("e2etel emettre " + FONDATRICE) + " | "
                    + rcon("e2etel glisser " + FONDATRICE);
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            Map<String, String> e = etatDe(FONDATRICE);
            if (!"ACTIVE".equals(e.get("sim")) || !LAGON.equals(e.get("operateur")) || num(e, "barres") != 4) {
                throw new IllegalStateException(reponse + " ; " + e);
            }
            return "Fondatrice : " + sans(reponse) + " ; son telephone : sim " + e.get("sim") + ", " + e.get("operateur") + ", "
                    + e.get("barres") + " barres";
        });

        // --- Dev ingénieur : sans outil, pas de panneau sur l'antenne d'un autre ; avec l'outil, le panneau.
        survie("");
        pas("Dev, outil de maintenance absent des deux mains, se place devant l'antenne de Lagon", () -> {
            placerJoueur(alleeB(), 90, 20);
        }, () -> ecoule() >= 0.8 && pres(alleeB()), () -> "joueur en " + mc.player.blockPosition(), () -> {
            if (compter(OUTIL) > 0 || !mc.player.getOffhandItem().isEmpty()) {
                throw new IllegalStateException("Dev porte deja un outil de maintenance");
            }
            return "Dev devant Lagon, sans outil";
        });
        pasD(10, "Clic droit sur l'antenne de Lagon sans outil : aucun panneau (« Antenne de Lagon »)", () -> {
            chatAvant = CHAT.size();
            tenirVide();
            clic(lagon(), Direction.EAST);
        }, () -> ecoule() >= 2.0 && chatCompte("message.oneforallsurvival.antenna.of", chatAvant) >= 1,
                () -> "le clic n'a rien rendu (aucun « antenna.of ») : chat " + CHAT.subList(chatAvant, CHAT.size()), () -> {
            Map<String, String> e = etatDe(DEV);
            if (panneauOuvert() || !"false".equals(e.get("panneau"))) {
                throw new IllegalStateException("panneau ouvert sans outil : ecran " + mc.screen + " ; " + e);
            }
            return "sans outil : aucun panneau (client " + mc.screen + ", serveur panneau=" + e.get("panneau") + ") ; message "
                    + CHAT.subList(chatAvant, CHAT.size()) + " (« " + tr("message.oneforallsurvival.antenna.of", LAGON) + " »)";
        });
        pas("Dev reçoit l'outil de maintenance dans sa main secondaire", () ->
                rcon("item replace entity " + nom() + " weapon.offhand with " + OUTIL),
                () -> mc.player.getOffhandItem().is(dev.oneforallsurvival.repair.RepairRegistry.MAINTENANCE_TOOL.get()),
                () -> "seconde main " + mc.player.getOffhandItem(), () -> "outil de maintenance en seconde main");
        ouvrirPanneau("Avec l'outil : ", E2eTelephone::alleeB, 90, E2eTelephone::lagon);
        pas("Panneau de Lagon pour l'ingénieur : ni cartes ni tarif à lui ; deux pages de cartes", () -> {}, () -> ecoule() >= 0.5,
                () -> "", () -> {
                    OperatorView v = vue();
                    if (!LAGON.equals(v.name()) || v.rights().cards() || v.rights().tariff() || v.rights().till() || v.cardPages() != 2) {
                        throw new IllegalStateException("vue " + v.name() + " droits " + v.rights() + " pages " + v.cardPages());
                    }
                    return "panneau de Lagon ouvert par l'ingenieur outille : droits " + v.rights() + ", " + v.cardTotal() + " cartes sur "
                            + v.cardPages() + " pages ; antennes " + antennesVues(v);
                });
        pas("Onglet « Cartes » (vrai clic)", () -> onglet(OperatorPanelLayout.Tab.CARDS),
                () -> panneauOuvert() && panneau().tab() == OperatorPanelLayout.Tab.CARDS && ecoule() >= 0.5, () -> "onglet " + panneau().tab(),
                () -> "onglet Cartes : page " + (vue().cardPage() + 1) + " sur " + vue().cardPages() + " ; capture "
                        + capturer("t2_panneau_lagon_cartes_page1"));
        pas("Page suivante des cartes (vrai clic, après la molette jusqu'en bas de la liste)", () -> {
            // La molette (trois lignes par cran) jusqu'à ce que le bouton de page soit à l'écran, en bas des 32 cartes.
            double[] c = null;
            for (int i = 0; i < 80 && c == null; i++) {
                mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, -1);
                c = panneau().centreOf(null, Action.PAGE, "");
            }
            // Sur la première page, le seul bouton de page est « Suivante ».
            cliquerEn(c, "page suivante");
        }, () -> panneauOuvert() && vue().cardPage() == 1 && ecoule() >= 0.8, () -> "page " + vue().cardPage(),
                () -> "page " + (vue().cardPage() + 1) + " sur " + vue().cardPages() + " : " + vue().cards().size() + " carte(s) ; capture "
                        + capturer("t2_panneau_lagon_cartes_page2"));
        pas("Échap ferme le panneau de Lagon", () -> panneau().keyPressed(GLFW.GLFW_KEY_ESCAPE, 1, 0),
                () -> !panneauOuvert() && ecoule() >= 0.8 && "false".equals(etatDe(DEV).get("panneau")), () -> "ecran " + mc.screen,
                () -> "Echap : panneau ferme, le serveur le sait");

        // --- Mairie : pose par Dev (vrai clic), nom tapé au panneau.
        pas("Dev reçoit deux antennes et se place dans l'allée de l'aire A", () -> {
            rcon("give " + nom() + " " + ANTENNE + " 2");
            placerJoueur(alleeA(), 90, 30);
        }, () -> ecoule() >= 1.0 && pres(alleeA()) && compter(ANTENNE) >= 2, () -> "antennes " + compter(ANTENNE), null);
        pas("Dev pose son antenne d'un vrai clic droit sur le sol", () -> {
            tenir(ANTENNE);
            clic(mairie().below(), Direction.UP);
        }, () -> ecoule() >= 1.0 && mc.level.getBlockState(mairie()).is(PhoneRegistry.ANTENNA.get()),
                () -> "bloc " + mc.level.getBlockState(mairie()), () -> "antenne posee par Dev en " + court(mairie()) + " ; serveur : "
                        + sans(rcon("e2etel antenne " + xyz(mairie()))));
        ouvrirPanneau("Antenne neuve : ", E2eTelephone::alleeA, 90, E2eTelephone::mairie);
        pas("Antenne neuve : le panneau demande un nom ; Dev tape « " + MAIRIE + " » et appuie sur Entrée", () -> {
            if (!vue().operatorId().isEmpty() || !vue().rights().name()) {
                throw new IllegalStateException("antenne deja fondee ou sans droit au nom : " + vue().rights());
            }
            VU.put("neuve", capturer("t2_panneau_antenne_neuve"));
            taper(MAIRIE);
            panneau().keyPressed(GLFW.GLFW_KEY_ENTER, 28, 0);
        }, () -> panneauOuvert() && MAIRIE.equals(vue().name()) && ecoule() >= 1.0, () -> "vue « " + vue().name() + " »", () -> {
            Map<String, String> m = operateurLu(MAIRIE);
            if (!("[" + MAIRIE + "]").equals(m.get("nom")) || !DEV.equals(m.get("fondateur"))) {
                throw new IllegalStateException("operateur " + m);
            }
            fondee = System.currentTimeMillis();
            return "nom tape (" + MAIRIE.length() + " caracteres) et Entree : le serveur fonde " + m.get("nom") + " au nom de " + m.get("fondateur")
                    + " ; ligne d'etat « " + panneau().status() + " » ; captures " + VU.get("neuve") + ", " + capturer("t2_panneau_mairie_fondee");
        });

        // --- Le fil de Crafts & Additions de la source à l'antenne.
        // Revue de la tâche 13 (C1) : la source ne touche pas l'antenne ; sans fil, rien ne passe (réserve vide, hors
        // service) ; la bobine tirée, l'antenne se remplit par le fil seul.
        pasD(15, "Source d'énergie créative à deux blocs de l'antenne, connecteurs posés sur le dessus, sans fil : rien ne passe (3 s)", () -> {
            fermer();
            reponse = rcon("e2etel source " + xyz(sourceMairie()) + " true") + " | " + rcon("e2etel fil " + xyz(sourceMairie()) + " "
                    + xyz(mairie()) + " 1");
        }, () -> ecoule() >= 3.0, () -> reponse, () -> {
            Map<String, String> a = antenneLue(mairie());
            String journal = reponse.toLowerCase(java.util.Locale.ROOT);
            if (!"false".equals(a.get("enService")) || num(a, "reserve") != 0 || !"0".equals(operateurLu(MAIRIE).get("enService"))
                    || !reponse.contains("creative_energy") || journal.split("connector_sur", -1).length - 1 != 2) {
                throw new IllegalStateException("sans fil : " + a + " ; " + reponse);
            }
            return "sans fil (source a " + court(sourceMairie()) + ", antenne a " + court(mairie()) + ", un bloc d'air entre elles) : "
                    + sans(reponse) + " ; antenne : enService=" + a.get("enService") + ", reserve=" + a.get("reserve") + ", etat " + a.get("etat");
        });
        pasD(30, "Bobine de cuivre tirée d'un connecteur à l'autre : l'antenne de Mairie se remplit par le fil et entre en service", () ->
                reponse = rcon("e2etel fil " + xyz(sourceMairie()) + " " + xyz(mairie()) + " 2"),
                () -> ecoule() >= 1.0 && "1".equals(operateurLu(MAIRIE).get("enService")) && num(antenneLue(mairie()), "reserve") > 0,
                () -> reponse + " ; antenne " + antenneLue(mairie()), () -> {
                    String journal = reponse.toLowerCase(java.util.Locale.ROOT);
                    if (!journal.contains("connector:pull,connector:push") || journal.split("->_consume", -1).length - 1 < 2) {
                        throw new IllegalStateException("bobine : " + reponse);
                    }
                    return "fil tire (deux clics de bobine CONSUME) : " + sans(reponse) + " ; Mairie en service par le fil : "
                            + sans(rcon("e2etel antenne " + xyz(mairie())));
                });
        // --- Le tarif du lendemain.
        ouvrirPanneau("Tarif : ", E2eTelephone::alleeA, 90, E2eTelephone::mairie);
        pas("Onglet « Tarif » (vrai clic)", () -> onglet(OperatorPanelLayout.Tab.TARIFF),
                () -> panneauOuvert() && panneau().tab() == OperatorPanelLayout.Tab.TARIFF && ecoule() >= 0.5, () -> "onglet " + panneau().tab(),
                null);
        pas("Clic dans le champ du tarif ; Dev tape " + TARIF, () -> {
            cliquerEn(panneau().centreOf(null, null, ""), "champ du tarif");
            taper(Long.toString(TARIF));
        }, () -> ecoule() >= 0.5, () -> "", null);
        pas("Clic sur « Enregistrer » : le tarif vaut au lendemain", () -> bouton(Action.SET_TARIFF, ""),
                () -> panneauOuvert() && vue().nextTariff() != null && vue().nextTariff() == TARIF && ecoule() >= 1.0,
                () -> "demain " + vue().nextTariff(), () -> {
                    String attendu = tr(PANNEAU + "tariff_set", CoinMath.format(TARIF));
                    Map<String, String> m = operateurLu(MAIRIE);
                    if (!attendu.equals(panneau().status()) || vue().tariff() != 0 || num(m, "tarifDemain") != TARIF || num(m, "tarif") != 0) {
                        throw new IllegalStateException("ligne « " + panneau().status() + " » (attendu « " + attendu + " ») ; tarif du jour "
                                + vue().tariff() + " ; serveur " + m);
                    }
                    VU.put("jourTarif", lire(rcon("e2etel jour")).get("jour"));
                    return "tarif tape (" + TARIF + ") et enregistre : ligne d'etat « " + panneau().status() + " », tarif du jour "
                            + fmt(vue().tariff()) + " ; serveur : tarif " + m.get("tarif") + ", demain " + m.get("tarifDemain") + " (jour "
                            + VU.get("jourTarif") + ") ; capture " + capturer("t2_panneau_tarif_au_lendemain");
                });

        // --- Demandes de carte (Usager et Voisin, par leur feuille « Carte SIM »).
        pas("Usager et Voisin (simulés) entrent dans l'aire A, prennent un téléphone et demandent une carte à Mairie", () -> {
            reponse = rcon("e2etel entrer " + USAGER + " " + xyz(p(10, 0, 6))) + " | " + rcon("e2etel telephone " + USAGER) + " | "
                    + rcon("e2etel demander " + USAGER + " " + MAIRIE) + " | " + rcon("e2etel entrer " + VOISIN + " " + xyz(p(12, 0, 6)))
                    + " | " + rcon("e2etel telephone " + VOISIN) + " | " + rcon("e2etel demander " + VOISIN + " " + MAIRIE);
        }, () -> ecoule() >= 1.5 && panneauOuvert() && vue().requests().size() == 2, () -> reponse + " ; demandes vues "
                + (panneauOuvert() ? vue().requests().size() : -1), () -> {
                    for (OperatorView.RequestLine r : vue().requests()) {
                        if (USAGER.equals(r.playerName())) {
                            idDemandeUsager = r.id().toString();
                        } else if (VOISIN.equals(r.playerName())) {
                            idDemandeVoisin = r.id().toString();
                        }
                    }
                    String[] r = reponse.split(" \\| ");
                    if (!"SIM_REQUESTED".equals(lire(r[2]).get("dernier")) || !"SIM_REQUESTED".equals(lire(r[5]).get("dernier"))
                            || idDemandeUsager.isEmpty() || idDemandeVoisin.isEmpty()) {
                        throw new IllegalStateException(reponse);
                    }
                    return "demandes faites par la feuille (REQUEST_SIM) : " + sans(reponse) + " ; le panneau de Dev les montre a son relevé";
                });
        pas("Onglet « Cartes » (vrai clic)", () -> onglet(OperatorPanelLayout.Tab.CARDS),
                () -> panneauOuvert() && panneau().tab() == OperatorPanelLayout.Tab.CARDS && ecoule() >= 0.5, () -> "onglet",
                () -> "onglet Cartes : demandes " + vue().requests().size() + " ; capture " + capturer("t2_panneau_mairie_demandes"));
        pas("Vrai clic sur « Émettre une SIM sans nom »", () -> {
            cartesAvant = vue().cardTotal();
            bouton(Action.ISSUE_ANONYMOUS, "");
        }, () -> panneauOuvert() && vue().cardTotal() == cartesAvant + 1 && ecoule() >= 1.0, () -> "cartes " + vue().cardTotal(), () -> {
            for (OperatorView.CardLine c : vue().cards()) {
                if (c.holderName().isEmpty()) {
                    idAnonyme = c.simId().toString();
                }
            }
            String attendu = tr(PANNEAU + "issued_anonymous");
            if (idAnonyme.isEmpty() || !attendu.equals(panneau().status())) {
                throw new IllegalStateException("ligne « " + panneau().status() + " » ; cartes " + vue().cards());
            }
            return "SIM sans nom emise : ligne d'etat « " + panneau().status() + " » ; " + vue().cardTotal() + " carte(s) ; elle est dans "
                    + "l'inventaire de Dev (" + compter("oneforallsurvival:sim_card") + " carte(s))";
        });
        pas("Vrai clic sur « Émettre » pour la demande d'Usager", () -> bouton(Action.ISSUE, idDemandeUsager),
                () -> panneauOuvert() && vue().requests().size() == 1 && ecoule() >= 1.0, () -> "demandes " + vue().requests().size(), () -> {
                    String attendu = tr(PANNEAU + "issued", USAGER);
                    if (!attendu.equals(panneau().status())) {
                        throw new IllegalStateException("ligne « " + panneau().status() + " »");
                    }
                    return "carte emise pour Usager : « " + panneau().status() + " » ; demandes restantes " + vue().requests().size();
                });
        pas("Vrai clic sur « Refuser » pour la demande de Voisin", () -> bouton(Action.REFUSE, idDemandeVoisin),
                () -> panneauOuvert() && vue().requests().isEmpty() && ecoule() >= 1.0, () -> "demandes " + vue().requests().size(), () -> {
                    String attendu = tr(PANNEAU + "refused", VOISIN);
                    if (!attendu.equals(panneau().status())) {
                        throw new IllegalStateException("ligne « " + panneau().status() + " »");
                    }
                    return "demande de Voisin refusee : « " + panneau().status() + " » ; capture " + capturer("t2_panneau_mairie_cartes_emises");
                });

        // --- La carte d'Usager : tendue de la main à la main, une copie créative, glissée ; la copie est morte.
        pas("Dev tend la carte à Usager ; une copie créative en est faite chez Voisin ; Usager glisse la sienne", () -> {
            reponse = rcon("e2etel donner " + DEV + " " + USAGER) + " | " + rcon("e2etel copier " + USAGER + " " + VOISIN) + " | "
                    + rcon("e2etel glisser " + USAGER);
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            String[] r = reponse.split(" \\| ");
            Map<String, String> g = lire(r[2]);
            if (!"true".equals(lire(r[0]).get("remis")) || !"true".equals(lire(r[1]).get("mise")) || !"ACTIVE".equals(g.get("validite"))) {
                throw new IllegalStateException(reponse);
            }
            return sans(reponse);
        });
        pas("Voisin glisse la copie dans son téléphone : refusée, une seule ligne", () -> reponse = rcon("e2etel glisser " + VOISIN),
                () -> ecoule() >= 0.5, () -> "", () -> {
                    Map<String, String> g = lire(reponse);
                    Map<String, String> u = etatDe(USAGER);
                    Map<String, String> v = etatDe(VOISIN);
                    // Le geste a bien eu lieu et a été refusé pour une carte morte (revue, I2) : ni « REFUS », ni « ABSENT ».
                    if (!reponse.startsWith("GLISSER") || !"REFUSED".equals(g.get("issue")) || !"SIM_UNKNOWN".equals(g.get("dernier"))
                            || "ACTIVE".equals(g.get("validite")) || !"ACTIVE".equals(u.get("sim")) || !"NONE".equals(v.get("sim"))
                            || !v.get("cartes").contains(":false")) {
                        throw new IllegalStateException(reponse + " ; Usager " + u + " ; Voisin " + v);
                    }
                    return "copie refusee : " + sans(reponse) + " ; Usager : sim " + u.get("sim") + " (" + u.get("operateur") + ", "
                            + u.get("barres") + " barres) ; Voisin : sim " + v.get("sim") + ", copie " + v.get("cartes") + " (non authentique)";
                });

        // --- Remplacer la SIM sans nom (dans l'inventaire de Dev).
        ouvrirPanneau("Remplacer : ", E2eTelephone::alleeA, 90, E2eTelephone::mairie);
        pas("Onglet « Cartes » (vrai clic)", () -> onglet(OperatorPanelLayout.Tab.CARDS),
                () -> panneauOuvert() && panneau().tab() == OperatorPanelLayout.Tab.CARDS && ecoule() >= 0.5, () -> "onglet", null);
        pas("Vrai clic sur « Remplacer » pour la SIM sans nom : une nouvelle pile, l'ancienne ne vaut plus rien", () -> {
            cartesAvant = compter("oneforallsurvival:sim_card");
            bouton(Action.REPLACE, idAnonyme);
        }, () -> panneauOuvert() && compter("oneforallsurvival:sim_card") == cartesAvant + 1 && ecoule() >= 1.0,
                () -> "cartes de Dev " + compter("oneforallsurvival:sim_card"), () -> {
                    Map<String, String> e = etatDe(DEV);
                    List<String> c = liste(e.get("cartes"));
                    long vivantes = c.stream().filter(x -> x.endsWith(":true")).count();
                    long mortes = c.stream().filter(x -> x.endsWith(":false")).count();
                    if (vivantes != 1 || mortes != 1) {
                        throw new IllegalStateException("cartes de Dev " + e.get("cartes"));
                    }
                    return "« " + panneau().status() + " » ; Dev porte " + c.size() + " cartes : 1 authentique (la nouvelle), 1 morte (l'ancienne)";
                });

        // --- Désigner l'antenne principale.
        pas("Dev pose une seconde antenne de Mairie (vrai clic, sans source)", () -> {
            fermer();
            tenir(ANTENNE);
            clic(mairie2().below(), Direction.UP);
        }, () -> ecoule() >= 1.0 && mc.level.getBlockState(mairie2()).is(PhoneRegistry.ANTENNA.get())
                && antennesDe(operateurLu(MAIRIE)).size() == 2,
                () -> "bloc " + mc.level.getBlockState(mairie2()) + " ; " + operateurLu(MAIRIE),
                () -> "seconde antenne de Mairie en " + court(mairie2()) + " : " + operateurLu(MAIRIE).get("antennes"));
        ouvrirPanneau("Désigner : ", E2eTelephone::alleeA, 90, E2eTelephone::mairie);
        pas("Vrai clic sur « Désigner » pour la seconde antenne", () -> {
            for (OperatorView.AntennaLine a : vue().antennas()) {
                if (a.pos().equals(mairie2())) {
                    idM2 = a.id().toString();
                } else {
                    idM = a.id().toString();
                }
            }
            onglet(OperatorPanelLayout.Tab.NETWORK);
            bouton(Action.SET_MAIN, idM2);
        }, () -> panneauOuvert() && vue().antennas().stream().anyMatch(a -> a.main() && a.pos().equals(mairie2())) && ecoule() >= 1.0,
                () -> "antennes " + antennesVues(vue()), () -> {
                    String[] m2 = antennesDe(operateurLu(MAIRIE)).get(court(mairie2()));
                    if (m2 == null || !"true".equals(m2[3])) {
                        throw new IllegalStateException(operateurLu(MAIRIE).toString());
                    }
                    return "« " + panneau().status() + " » ; serveur : principale en " + court(mairie2()) + " ; capture "
                            + capturer("t2_panneau_designer");
                });
        pas("Vrai clic sur « Désigner » pour la première : elle redevient la principale", () -> bouton(Action.SET_MAIN, idM),
                () -> panneauOuvert() && vue().antennas().stream().anyMatch(a -> a.main() && a.pos().equals(mairie())) && ecoule() >= 1.0,
                () -> "antennes " + antennesVues(vue()), () -> "principale rendue a " + court(mairie()) + " : " + antennesVues(vue()));

        // --- Le panneau à 320 × 240.
        pas("Le panneau à l'interface de 320 × 240 (fenêtre 640 × 480, échelle 2 ; en mémoire seulement)", () -> {
            largeurAvant = mc.getWindow().getWidth();
            hauteurAvant = mc.getWindow().getHeight();
            echelleAvant = mc.options.guiScale().get();
            onglet(OperatorPanelLayout.Tab.CARDS);
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 640, 480);
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
        }, () -> ecoule() >= 1.5 && mc.getWindow().getGuiScaledWidth() == 320 && mc.getWindow().getGuiScaledHeight() == 240,
                () -> "interface " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight(), () -> {
                    String cartes = capturer("t2_panneau_320x240_cartes");
                    return "320 x 240 : " + miseEnPage(320, 240) + " ; capture " + cartes;
                });
        pas("À 320 × 240 : onglets « Réseau » et « Tarif »", () -> onglet(OperatorPanelLayout.Tab.NETWORK), () -> ecoule() >= 0.8, () -> "",
                () -> {
                    String reseau = capturer("t2_panneau_320x240_reseau");
                    onglet(OperatorPanelLayout.Tab.TARIFF);
                    return "capture " + reseau;
                });
        pas("À 320 × 240 : « Tarif », puis retour à la taille d'avant", () -> {}, () -> ecoule() >= 0.8, () -> "", () -> {
            String tarif = capturer("t2_panneau_320x240_tarif");
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), largeurAvant, hauteurAvant);
            mc.options.guiScale().set(echelleAvant);
            mc.resizeDisplay();
            return "capture " + tarif + " ; fenetre rendue a " + largeurAvant + " x " + hauteurAvant + ", echelle " + echelleAvant;
        });

        // --- Consommation : panneau = console = veille + 6 x cartes actives.
        pasD(10, "Consommation de Mairie : panneau = console = veille + 6 × cartes actives", () -> {}, () -> ecoule() >= 2.0
                && mc.getWindow().getWidth() == largeurAvant, () -> "", () -> {
            Map<String, String> m = operateurLu(MAIRIE);
            long actives = num(m, "actives");
            String[] a = antennesDe(m).get(court(mairie()));
            OperatorView.AntennaLine vueA = vue().antennas().stream().filter(x -> x.pos().equals(mairie())).findFirst().orElseThrow();
            String console = a[1];
            String panneauVu = vueA.load().standby() + "+" + vueA.load().clients() + "+" + vueA.load().traffic();
            long attendu = 40 + 6 * actives;
            if (!console.equals(panneauVu) || vueA.load().total() != attendu) {
                throw new IllegalStateException("panneau " + panneauVu + " ; console " + console + " ; actives " + actives);
            }
            VU.put("conso", panneauVu);
            return "Mairie : panneau " + panneauVu + " FE/t = console " + console + " = 40 + 6 x " + actives + " cartes actives = " + attendu
                    + " FE/t ; capture " + capturer("t2_panneau_consommation");
        });

        // --- Les barres d'Usager à 100 blocs.
        pas("Usager replacé à 100 blocs de Mairie (teleportTo) : 3 barres relues", () -> {
            fermer();
            reponse = rcon("e2etel placer " + USAGER + " " + xyz(mairie().east(100)));
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            Map<String, String> r = lire(reponse);
            if (num(r, "barres") != 3 || !"ACTIVE".equals(r.get("sim"))) {
                throw new IllegalStateException(reponse);
            }
            String retour = sans(rcon("e2etel placer " + USAGER + " " + xyz(p(10, 0, 6))));
            return "a 100 blocs : " + sans(reponse) + " ; ramene dans l'aire : " + retour;
        });

        // --- Le trajet d'un appel : Mairie → Lagon, puis Lagon hors tension.
        pas("Trajet d'Usager (Mairie) vers Fondatrice (Lagon), les deux antennes en service : il passe", () ->
                reponse = rcon("e2etel appel " + USAGER + " " + FONDATRICE), () -> ecoule() >= 0.3, () -> "", () -> {
                    if (!"oui".equals(lire(reponse).get("trajet"))) {
                        throw new IllegalStateException(reponse);
                    }
                    return "temoin : " + sans(reponse);
                });
        pasD(30, "La source de Lagon est coupée par la console (réserve vidée) : Lagon hors service", () ->
                reponse = rcon("e2etel source " + xyz(sourceLagon()) + " false") + " | " + rcon("e2etel vider " + xyz(lagon())),
                () -> ecoule() >= 1.0 && "0".equals(operateurLu(LAGON).get("enService")), () -> reponse + " ; " + antenneLue(lagon()),
                () -> "source de Lagon coupee : " + sans(reponse) + " ; antenne " + sans(rcon("e2etel antenne " + xyz(lagon()))));
        pas("Trajet Mairie → Lagon : en échec, « Antenne de Lagon hors tension »", () ->
                reponse = rcon("e2etel appel " + USAGER + " " + FONDATRICE), () -> ecoule() >= 0.3, () -> "", () -> {
                    Map<String, String> r = lire(reponse);
                    if (!"CALLEE_ANTENNA_DOWN".equals(r.get("raison")) || !LAGON.equals(r.get("operateur"))) {
                        throw new IllegalStateException(reponse);
                    }
                    return sans(reponse) + " : « " + tr(r.get("cle"), LAGON) + " »";
                });
        pasD(30, "La source de Lagon est rétablie : Lagon de nouveau en service", () ->
                reponse = rcon("e2etel source " + xyz(sourceLagon()) + " true"),
                () -> ecoule() >= 1.0 && "1".equals(operateurLu(LAGON).get("enService")), () -> reponse + " ; " + antenneLue(lagon()),
                () -> "Lagon retablie : " + sans(rcon("e2etel antenne " + xyz(lagon()))));

        // --- Le joueur sans droit.
        pas("Voisin (sans droit) tente d'ouvrir puis de casser l'antenne de Mairie : refusé", () ->
                reponse = rcon("e2etel intrus " + VOISIN + " " + xyz(mairie())), () -> ecoule() >= 0.5, () -> "", () -> {
                    Map<String, String> r = lire(reponse);
                    if (!"false".equals(r.get("panneau")) || !"false".equals(r.get("casse")) || !"true".equals(r.get("antenneLa"))
                            || !r.get("messagesClic").contains("message.oneforallsurvival.antenna.of")) {
                        throw new IllegalStateException(reponse);
                    }
                    return "refuse : " + sans(reponse);
                });

        // --- Avant l'arrêt : la seconde antenne retirée, l'état écrit, tous les joueurs simulés partis.
        pas("La seconde antenne de Mairie est retirée par la console ; Mairie garde une antenne", () ->
                rcon("setblock " + xyz(mairie2()) + " air"), () -> ecoule() >= 1.0 && antennesDe(operateurLu(MAIRIE)).size() == 1,
                () -> operateurLu(MAIRIE).toString(), () -> "Mairie : " + operateurLu(MAIRIE).get("antennes"));
        pas("Le réseau vu en jeu (Mairie, son fil et sa source)", () -> {
            fermer();
            placerJoueur(p(6, 0, 6), 120, 15);
        }, () -> ecoule() >= 2.0 && pres(p(6, 0, 6)), () -> "joueur en " + mc.player.blockPosition(), () -> "capture " + capturer("t2_mairie_en_jeu"));
        pas("Avant l'arrêt : opérateurs, cartes et antennes écrits dans " + FICHIER, () -> {}, () -> ecoule() >= 0.5, () -> "", () -> {
            Map<String, String> m = operateurLu(MAIRIE);
            Map<String, String> l = operateurLu(LAGON);
            Map<String, String> u = etatDe(USAGER);
            Map<String, String> j = lire(rcon("e2etel jour"));
            long relance = num(j, "decalage") + 24 * 60;
            List<String> lignes = new ArrayList<>();
            lignes.add("origine=" + xyz(o));
            lignes.add("mairie=" + xyz(mairie()));
            lignes.add("lagon=" + xyz(lagon()));
            lignes.add("idMairie=" + m.get("id"));
            lignes.add("idLagon=" + l.get("id"));
            lignes.add("tarifDemain=" + m.get("tarifDemain"));
            lignes.add("tarif=" + m.get("tarif"));
            lignes.add("jour=" + j.get("jour"));
            lignes.add("activesMairie=" + m.get("actives"));
            lignes.add("activesLagon=" + l.get("actives"));
            lignes.add("anonyme=" + idAnonyme);
            lignes.add("consommation=" + VU.get("conso"));
            lignes.add("usagerSim=" + u.get("sim"));
            lignes.add("fondee=" + fondee);
            try {
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            if (E2eClient.telephoneSeul()) {
                LOG.info("[e2e] horloge pour la relance : -Pe2eHorloge={}", relance);
            }
            return "ecrit dans " + fichier() + " : " + String.join(" ; ", lignes) + (E2eClient.telephoneSeul()
                    ? " ; relance a lancer avec -Pe2eHorloge=" + relance + " (un jour de plus)" : "");
        });
        pas("Fondatrice, Usager et Voisin se déconnectent", () -> reponse = rcon("e2etel quitter " + FONDATRICE) + " | "
                + rcon("e2etel quitter " + USAGER) + " | " + rcon("e2etel quitter " + VOISIN), () -> ecoule() >= 0.5, () -> "", () -> {
                    if (reponse.split("PARTI", -1).length - 1 != 3) {
                        throw new IllegalStateException(reponse);
                    }
                    return "deconnectes : " + sans(reponse);
                });
        rentrer("");
    }

    // ================================================================================================
    // Relance
    // ================================================================================================

    static void relance() {
        relever("Relance : ");
        pas("Relance : état de la première phase relu", () -> {
            Map<String, String> f = lireFichier();
            o = position(f.get("origine"));
        }, () -> true, () -> "", () -> "premiere phase : " + lireFichier());
        pasD(30, "Relance : Mairie et Lagon en service sans joueur à côté (tickets revalidés au démarrage)", () -> {},
                () -> ecoule() >= 1.0 && "true".equals(antenneLue(mairie()).get("enService"))
                        && "true".equals(antenneLue(lagon()).get("enService")),
                () -> "Mairie " + antenneLue(mairie()) + " ; Lagon " + antenneLue(lagon()), () -> {
                    Map<String, String> a = antenneLue(mairie());
                    Map<String, String> b = antenneLue(lagon());
                    if (num(a, "plusPres") < 300 || num(b, "plusPres") < 300 || !"true".equals(a.get("ticket"))
                            || !"true".equals(b.get("ticket"))) {
                        throw new IllegalStateException(a + " ; " + b);
                    }
                    return "Mairie : " + sans(rcon("e2etel antenne " + xyz(mairie()))) + " ; Lagon : " + sans(rcon("e2etel antenne " + xyz(lagon())));
                });
        pas("Relance : opérateurs, cartes et tarif appliqués (calculés sur l'état relu)", () -> {}, () -> true, () -> "", () -> {
            Map<String, String> f = lireFichier();
            Map<String, String> m = operateurLu(MAIRIE);
            Map<String, String> l = operateurLu(LAGON);
            String jour = lire(rcon("e2etel jour")).get("jour");
            boolean passe = java.time.LocalDate.parse(jour).isAfter(java.time.LocalDate.parse(f.get("jour")));
            long tarifAttendu = passe ? Long.parseLong(f.get("tarifDemain")) : Long.parseLong(f.get("tarif"));
            String demainAttendu = passe ? "-" : f.get("tarifDemain");
            if (!passe || num(m, "tarif") != tarifAttendu || !demainAttendu.equals(m.get("tarifDemain"))
                    || num(m, "actives") != Long.parseLong(f.get("activesMairie"))
                    || num(l, "actives") != Long.parseLong(f.get("activesLagon")) || !m.get("cartes").contains(f.get("anonyme") + ":-:ACTIVE")) {
                throw new IllegalStateException("jour " + jour + " (premiere phase " + f.get("jour") + ") ; Mairie " + m + " ; Lagon " + l);
            }
            return "jour " + jour + " (premiere phase " + f.get("jour") + ") : tarif de Mairie " + m.get("tarif") + " (attendu " + tarifAttendu
                    + "), demain " + m.get("tarifDemain") + " ; cartes actives Mairie " + m.get("actives") + ", Lagon " + l.get("actives")
                    + " ; SIM sans nom toujours ACTIVE";
        });
        pas("Relance : Usager et Voisin reviennent ; la ligne d'Usager vit (jeton relu), la copie de Voisin reste morte", () -> {
            reponse = rcon("e2etel entrer " + USAGER + " " + xyz(p(10, 0, 6))) + " | " + rcon("e2etel entrer " + VOISIN + " "
                    + xyz(p(12, 0, 6))) + " | " + rcon("e2etel glisser " + VOISIN);
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            Map<String, String> u = etatDe(USAGER);
            Map<String, String> v = etatDe(VOISIN);
            Map<String, String> g = lire(reponse.split(" \\| ")[2]);
            if (!"ACTIVE".equals(u.get("sim")) || num(u, "barres") != 4 || !MAIRIE.equals(u.get("operateur")) || "ACTIVE".equals(g.get("validite"))
                    || !"REFUSED".equals(g.get("issue")) || !"SIM_UNKNOWN".equals(g.get("dernier"))
                    || !v.get("cartes").contains(":false")) {
                throw new IllegalStateException(reponse + " ; Usager " + u + " ; Voisin " + v);
            }
            return "Usager : sim " + u.get("sim") + ", " + u.get("operateur") + ", " + u.get("barres") + " barres ; Voisin : copie "
                    + v.get("cartes") + ", glissee de nouveau : " + g.get("issue") + " / " + g.get("validite");
        });
        pas("Relance : un maire simulé tente « /operator remove Lagon » sans motif lisible : refusé", () ->
                reponse = rcon("e2etel maire motifvide " + LAGON), () -> ecoule() >= 0.3, () -> "", () -> {
                    Map<String, String> r = lire(reponse);
                    if (!"true".equals(r.get("elu")) || !"0".equals(r.get("demande")) || !"true".equals(r.get("existe"))
                            || !r.get("clesDemande").contains("remove_reason_required")) {
                        throw new IllegalStateException(reponse);
                    }
                    return "motif vide refuse : " + sans(reponse) + " (« " + tr("command.oneforallsurvival.operator.remove_reason_required") + " »)";
                });
        pas("Relance : le maire simulé supprime « Lagon » (/operator remove … puis confirm)", () -> {
            // La caisse de Lagon avant la suppression : ce que le fondateur doit recevoir (0 au lot T2, aucun dépôt).
            VU.put("caisseLagon", operateurLu(LAGON).get("caisse"));
            reponse = rcon("e2etel maire supprimer " + LAGON + " fin de l'essai du telephone");
        }, () -> ecoule() >= 0.3, () -> "", () -> {
            Map<String, String> r = lire(reponse);
            if (!"1".equals(r.get("demande")) || !"1".equals(r.get("confirme")) || !"false".equals(r.get("existe"))
                    || !r.get("clesConfirme").contains("command.oneforallsurvival.operator.removed") || !"0".equals(VU.get("caisseLagon"))) {
                throw new IllegalStateException(reponse + " ; caisse avant " + VU.get("caisseLagon"));
            }
            return "Lagon supprime : " + sans(reponse) + " ; caisse avant la suppression " + VU.get("caisseLagon");
        });
        pas("Relance : toutes les cartes de Lagon sont désactivées au registre", () -> {}, () -> ecoule() >= 0.3, () -> "", () -> {
            Map<String, String> f = lireFichier();
            Map<String, String> c = lire(rcon("e2etel cartesde " + f.get("idLagon")));
            long attendu = Long.parseLong(f.get("activesLagon"));
            if (num(c, "total") != attendu || num(c, "desactivees") != attendu || num(c, "actives") != 0 || num(c, "suspendues") != 0
                    || !"false".equals(c.get("operateurExiste"))) {
                throw new IllegalStateException("cartes de Lagon : " + c + " (" + attendu + " attendues, toutes desactivees)");
            }
            return "cartes de Lagon relues au registre : " + sans(rcon("e2etel cartesde " + f.get("idLagon")));
        });
        pasD(20, "Relance : l'antenne de Lagon s'arrête (STOPPED), désinscrite, ticket rendu", () -> {},
                () -> ecoule() >= 1.0 && "STOPPED".equals(antenneLue(lagon()).get("etat")), () -> antenneLue(lagon()).toString(), () -> {
                    Map<String, String> a = antenneLue(lagon());
                    if (!"false".equals(a.get("inscrite")) || !"false".equals(a.get("ticket")) || !"false".equals(a.get("enService"))) {
                        throw new IllegalStateException("antenne de Lagon " + a);
                    }
                    return "Lagon : " + sans(rcon("e2etel antenne " + xyz(lagon())));
                });
        pas("Relance : Fondatrice revient : sa carte de Lagon est désactivée ; l'avis et la caisse (0) lui arrivent", () ->
                reponse = rcon("e2etel entrer " + FONDATRICE + " " + xyz(p(52, 0, 6))), () -> ecoule() >= 0.5, () -> "", () -> {
                    Map<String, String> e = lire(reponse);
                    Map<String, String> s = etatDe(FONDATRICE);
                    Map<String, String> att = lire(rcon("e2etel attente " + FONDATRICE));
                    if (!e.get("connexion").contains("message.oneforallsurvival.operator.removed_founder")
                            || !"DEACTIVATED".equals(s.get("sim")) || num(att, "valeur") != 0 || num(e, "porte") != 0) {
                        throw new IllegalStateException(reponse + " ; telephone " + s + " ; attente " + att);
                    }
                    return "Fondatrice a la connexion : " + e.get("connexion") + " ; son telephone : sim " + s.get("sim") + " ; en attente "
                            + att.get("valeur") + " cuivre, porte " + e.get("porte") + " (caisse de Lagon : 0, aucun depot au lot T2)";
                });
        pas("Relance : Fondatrice, Usager et Voisin se déconnectent", () -> reponse = rcon("e2etel quitter " + FONDATRICE) + " | "
                + rcon("e2etel quitter " + USAGER) + " | " + rcon("e2etel quitter " + VOISIN), () -> ecoule() >= 0.5, () -> "",
                () -> "deconnectes : " + sans(reponse));
    }

    /**
     * Avant le {@code /stop}, même après un échec (revue de la tâche 13, I1) : Dev redevient opérateur d'abord (sinon
     * le {@code /stop} est refusé et le serveur reste allumé), puis son mode, la difficulté, la règle des naissances, son
     * rôle, le maire et sa seconde main sont rendus, et les joueurs simulés de l'étape sortent.
     */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null || depart == null) {
            return;
        }
        mc = minecraft;
        try {
            rcon("op " + nom());
            rcon("e2etel quitter " + FONDATRICE);
            rcon("e2etel quitter " + USAGER);
            rcon("e2etel quitter " + VOISIN);
            if (!enCours) {
                // L'étape est finie (ou pas commencée) : ses réglages sont déjà rendus ; ceux des étapes suivantes restent.
                return;
            }
            rcon("gamemode survival " + nom());
            rcon("difficulty " + difficulteAvant);
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("role set " + nom() + " " + roleDevAvant);
            if (DEV.equals(maireAvant)) {
                rcon("role mayor set " + nom());
            }
            if (secondeMainAvant) {
                rcon("item replace entity " + nom() + " weapon.offhand with minecraft:air");
            }
        } catch (RuntimeException e) {
            LOG.error("[e2e] rcon impossible avant l'arret", e);
        }
    }
}