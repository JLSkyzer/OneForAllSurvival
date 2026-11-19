package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerPanelPacket;
import dev.oneforallsurvival.grid.supplier.PowerTillMenu;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelScreen;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.management.ManagementFactory;
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
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Étape « électricité » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot E6 (prélèvement et
 * fournisseur d'électricité) vu par un vrai client contre un serveur dédié avec le pack complet.
 *
 * <p><b>Ordre.</b> Au premier passage, elle suit l'étape du trésor (E3) : celle-ci avance l'horloge de l'économie de deux
 * minuits, qui factureraient l'électricité du jour de l'essai avant l'arrêt ; placée après, l'étape laisse ces deux
 * clôtures au démarrage de la relance (scénario d'acceptation). Elle réécrit à sa fin les références de relance des
 * étapes de la monnaie et du commerce (comme le fait l'étape du trésor), car ses joueurs simulés changent les comptes. À la
 * relance, elle passe après le butin et avant les quêtes (qui restent les dernières). Les étapes de la monnaie et du
 * commerce relisent {@code /eco stats} et le registre corrigés de ce que les prélèvements de minuit ont fait au démarrage
 * ({@link #statsCorrigees}, {@link #journalCorrige}) : seulement si les chiffres du serveur le prouvent au cuivre près.
 *
 * <p><b>Aire</b> close et couverte, éclairée, loin de tout. Dev (ingénieur, survie, non opérateur) pose son poste d'un vrai
 * clic, le nomme et fixe ses tarifs dans le panneau (texte tapé, vrais clics), jumelle au testeur, accepte, coupe et
 * rétablit au panneau ; la console pose l'alternateur, le moteur créatif, les maisons et le fil suspendu de Crafts &amp;
 * Additions ; Abonne, Fauche et un maire sont des joueurs simulés ({@link E2eElectriciteServeur}).
 */
final class E2eElectricite {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-electricite.txt";
    private static final String CAPTURES = "captures-e6";
    private static final double DELAI = 25;
    private static final int Y_AIRE = 150;
    private static final String NOM_FOURNISSEUR = "Lumière du Port";
    private static final long PRIX = 5;
    private static final long ABONNEMENT = 10;
    private static final long PLAFOND = 3;
    private static final String ABONNE = "Abonne";
    private static final String FAUCHE = "Fauche";
    private static final String GUI = "gui.oneforallsurvival.power_panel.";
    private static final Pattern CLE_VALEUR = Pattern.compile("(\\w+)=(\\[[^\\]]*\\]|[^ ]+)");

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

    /** Les clés de langue des messages reçus par Dev depuis la connexion (enfants et arguments compris). */
    private static final List<String> CHAT = new ArrayList<>();

    // Mémoire d'un pas à l'autre.
    private static String reponse = "";
    private static Map<String, Integer> inventaireAvant = Map.of();
    /** Ce que contenait chaque case (0 à 35) au départ, et la case tenue : l'étape les rend tels quels. */
    private static List<String> casesAvant = List.of();
    private static int tenueAvant;
    private static long porteAvant;
    private static String difficulteAvant = "peaceful";
    private static String naissancesAvant = "true";
    private static String roleDevAvant = "citoyen";
    private static long departAbonne;
    private static int chatAvant;
    private static final Map<String, Long> VU = new LinkedHashMap<>();
    private static long caisseAvant;
    private static int etatBoucle;
    private static long clicNs;
    private static long totalAvantClic;
    private static int retraits;

    private E2eElectricite() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape de l'électricité";
    }

    /** Tous les messages du client, dès la connexion (la relance relit les avis reçus à l'entrée). */
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
    // Disposition (depuis O ; intérieur de (0, 0, 0) à (14, 3, 8), allée de Dev en z = 4)
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static String boite() {
        return xyz(p(-1, -1, -1)) + " " + xyz(p(15, 4, 9));
    }

    private static BlockPos allee(int x) {
        return p(x, 0, 4);
    }

    private static BlockPos moteur() {
        return p(1, 0, 1);
    }

    private static BlockPos alternateur() {
        return p(2, 0, 1);
    }

    private static BlockPos poste() {
        return p(3, 0, 1);
    }

    /** La maison d'Abonne au nord de l'allée (z = 1), celle de Fauche au sud (z = 7). */
    private static int zMaison(String qui) {
        return ABONNE.equals(qui) ? 1 : 7;
    }

    private static BlockPos compteur(String qui) {
        return p(9, 0, zMaison(qui));
    }

    private static BlockPos disjoncteur(String qui) {
        return p(10, 0, zMaison(qui));
    }

    private static BlockPos machine(String qui) {
        return p(11, 0, zMaison(qui));
    }

    private static BlockPos coffreFort(String qui) {
        return p(13, 0, zMaison(qui));
    }

    /** La face d'un bloc d'une maison tournée vers l'allée. */
    private static Direction faceAllee(BlockPos bloc) {
        return bloc.getZ() < allee(0).getZ() ? Direction.SOUTH : Direction.NORTH;
    }

    // ================================================================================================
    // Déroulement (même mécanique que les étapes des quêtes et du trésor)
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

    private static List<String> liste(String s) {
        List<String> out = new ArrayList<>();
        for (String x : sansCrochets(s).split(";")) {
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

    private static Map<String, String> fournisseur() {
        return lire(rcon("e2eelec fournisseur Dev"));
    }

    /** Un client tel que l'écrit {@code e2eelec fournisseur} : abonné, joueur, état, FE du jour et d'hier, coupé. */
    private record Cl(String abonne, String joueur, String etat, long feJour, long feHier, boolean coupe, int disjoncteurs) {
        static Cl lire(String s) {
            String[] t = s.split(":");
            return new Cl(t[0], t[1], t[2], Long.parseLong(t[3]), Long.parseLong(t[4]), Boolean.parseBoolean(t[5]),
                    Integer.parseInt(t[6]));
        }
    }

    private static Cl client(Map<String, String> f, String abonne) {
        for (String s : liste(f.getOrDefault("clients", "[]"))) {
            Cl c = Cl.lire(s);
            if (c.abonne().equalsIgnoreCase(abonne)) {
                return c;
            }
        }
        return null;
    }

    private static Cl client(String abonne) {
        return client(fournisseur(), abonne);
    }

    /** Une facture telle que l'écrit {@code e2eelec factures} : id, débiteur, montant, statut, jour, FE, prix, abonnement. */
    private record Fa(String id, String debiteur, long montant, String statut, String jour, long fe, long prix, long abonnement,
            long regleeA) {
        static Fa lire(String s) {
            String[] t = s.split(":");
            return new Fa(t[0], t[1], Long.parseLong(t[2]), t[3], t[4], Long.parseLong(t[5]), Long.parseLong(t[6]), Long.parseLong(t[7]),
                    Long.parseLong(t[8]));
        }
    }

    private static List<Fa> factures(Map<String, String> r) {
        List<Fa> out = new ArrayList<>();
        for (String s : liste(r.get("liste"))) {
            out.add(Fa.lire(s));
        }
        return out;
    }

    private static Map<String, String> machineEtat(String qui) {
        return lire(rcon("e2eelec machine " + xyz(machine(qui))));
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

    private static String fmt(long cuivres) {
        return CoinMath.format(cuivres).getString();
    }

    private static String tr(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    private static long valeurPortee() {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s) + Purse.total(s);
        }
        return n + CoinMath.valueOf(mc.player.getOffhandItem());
    }

    /** Les objets de l'inventaire, par identifiant, sans les pièces ni les bourses (sac, barre, armure, seconde main). */
    private static Map<String, Integer> inventaire() {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<ItemStack> tout = new ArrayList<>(mc.player.getInventory().items);
        tout.addAll(mc.player.getInventory().armor);
        tout.addAll(mc.player.getInventory().offhand);
        for (ItemStack s : tout) {
            if (!s.isEmpty() && CoinMath.valueOf(s) == 0 && !(s.getItem() instanceof dev.oneforallsurvival.economy.PurseItem)) {
                out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
        }
        return out;
    }

    private static int compter(String id) {
        return inventaire().getOrDefault(id, 0);
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

    /** Une image du jeu (écran compris) dans {@code run-e2e/captures-e6/<nom>.png}. */
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
    // Le panneau du poste
    // ================================================================================================

    private static PowerPanelScreen panneau() {
        if (!(mc.screen instanceof PowerPanelScreen s)) {
            throw new IllegalStateException("panneau ferme : ecran " + mc.screen);
        }
        return s;
    }

    private static boolean panneauOuvert() {
        return mc.screen instanceof PowerPanelScreen s && s.layout() != null;
    }

    private static PowerPanelPacket.Client rang(String abonne) {
        for (PowerPanelPacket.Client c : panneau().state().clients()) {
            if (c.subscriber().equalsIgnoreCase(abonne)) {
                return c;
            }
        }
        return null;
    }

    private static void cliquer(PowerPanelLayout.Rect r, String quoi) {
        if (r == null) {
            throw new IllegalStateException("aucun bouton " + quoi);
        }
        int x = r.x() + r.w() / 2;
        int y = r.y() + r.h() / 2;
        boolean pris = mc.screen.mouseClicked(x, y, 0);
        LOG.info("[e2e] clic gauche sur {} du panneau en ({}, {}) : {}", quoi, x, y, pris);
    }

    /** Le bouton {@code b} (0 : accepter, couper ou rétablir ; 1 : refuser ou détacher) de la ligne de cet abonné. */
    private static void cliquerRang(String abonne, int b, String quoi) {
        List<PowerPanelPacket.Client> clients = panneau().state().clients();
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).subscriber().equalsIgnoreCase(abonne)) {
                PowerPanelLayout.RowLayout ligne = panneau().layout().rows().get(i);
                cliquer(ligne.buttons().get(b), quoi + " (" + abonne + ", ligne " + i + ")");
                return;
            }
        }
        throw new IllegalStateException("aucune ligne pour " + abonne + " : " + clients);
    }

    private static void taper(String texte) {
        for (char c : texte.toCharArray()) {
            mc.screen.charTyped(c, 0);
        }
    }

    private static String decrire(PowerPanelPacket.Client c) {
        return c == null ? "absent" : c.subscriber() + " " + c.state() + " auj. " + c.feToday() + " hier " + c.feYesterday() + " impayes "
                + c.unpaid() + (c.cut() ? " coupe" : "") + (c.breaker() ? " (disjoncteur)" : "");
    }

    /** Dev, dans l'allée face au poste, ouvre le panneau d'un vrai clic droit, main vide. */
    private static void ouvrirPanneau(String quand) {
        pas(quand + "Dev se place devant son poste", () -> {
            fermer();
            placerJoueur(allee(3), 180, 30);
        }, () -> ecoule() >= 0.8 && pres(allee(3)), () -> "joueur en " + mc.player.blockPosition(), null);
        pas(quand + "Clic droit sur le poste : le panneau s'ouvre", () -> {
            tenirVide();
            clic(poste(), Direction.SOUTH);
        }, () -> panneauOuvert() && ecoule() >= 1.0, () -> "ecran " + mc.screen, () -> "panneau ouvert : « "
                + panneau().layout().title().full() + " », " + panneau().state().clients().size() + " client(s)");
    }

    /** Le testeur sur le poste (le fournisseur retenu), puis sur ce bloc (jumelage). */
    private static void jumeler(String quoi, Supplier<BlockPos> bloc, String cleAttendue) {
        pas("Dev se place devant son poste, testeur en main", () -> {
            fermer();
            placerJoueur(allee(3), 180, 30);
        }, () -> ecoule() >= 0.8 && pres(allee(3)), () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Testeur sur le poste : le fournisseur est retenu", () -> {
            chatAvant = CHAT.size();
            tenir("oneforallsurvival:tester");
            clic(poste(), Direction.SOUTH);
        }, () -> ecoule() >= 1.0 && chatCompte("message.oneforallsurvival.power.hold", chatAvant) >= 1 && mc.screen == null,
                () -> "chat " + CHAT.subList(chatAvant, CHAT.size()) + ", ecran " + mc.screen,
                () -> "testeur sur le poste : « " + tr("message.oneforallsurvival.power.hold", NOM_FOURNISSEUR, 60) + " »");
        pas("Dev se place devant " + quoi, () -> {
            BlockPos b = bloc.get();
            placerJoueur(allee(b.getX() - o.getX()), faceAllee(b) == Direction.SOUTH ? 180 : 0, 30);
        }, () -> ecoule() >= 0.8 && pres(allee(bloc.get().getX() - o.getX())), () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Testeur sur " + quoi + " : jumelé", () -> {
            chatAvant = CHAT.size();
            tenir("oneforallsurvival:tester");
            clic(bloc.get(), faceAllee(bloc.get()));
        }, () -> ecoule() >= 1.0 && chatCompte(cleAttendue, chatAvant) >= 1,
                () -> "chat " + CHAT.subList(chatAvant, CHAT.size()), () -> "testeur sur " + quoi + " : " + cleAttendue + " au chat");
    }

    // ================================================================================================
    // Les gestes répétés
    // ================================================================================================

    private static void allerALAire(String quand) {
        pas(quand + "Le joueur, en spectateur, se rend à l'aire de l'électricité (loin de tout ; les chunks se chargent)", () -> {
            fermer();
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 7.5) + " " + (o.getY() + 1) + " " + (o.getZ() + 4.5));
        }, () -> ecoule() >= 4 && chunksPrets(allee(7)), () -> "chunks de l'aire non recus, joueur en " + mc.player.blockPosition(),
                () -> "aire de l'electricite en " + o.toShortString() + " (joueur parti de " + depart.toShortString() + ") ; Dev porte "
                        + valeurPortee() + " cuivres");
    }

    private static void survie(String quand, String... dons) {
        pas(quand + "Dev, ingénieur, en survie et non opérateur, reçoit ses objets", () -> {
            rcon("role set " + nom() + " ingenieur");
            rcon("gamemode survival " + nom());
            rcon("deop " + nom());
            for (String d : dons) {
                // Le testeur de Dev (étape de la réparation), s'il en a un, sert : un second changerait sa barre d'action.
                if (!(d.startsWith("oneforallsurvival:tester") && compter("oneforallsurvival:tester") > 0)) {
                    rcon("give " + nom() + " " + d);
                }
            }
            placerJoueur(allee(3), 180, 30);
        }, () -> ecoule() >= 1.5 && !operateur() && pres(allee(3)) && mc.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SURVIVAL,
                () -> "operateur " + operateur() + ", joueur en " + mc.player.blockPosition() + ", mode " + mc.gameMode.getPlayerMode(),
                () -> {
                    airePrete = true;
                    return "Dev en survie, operateur = " + operateur() + ", role " + lire(rcon("e2etresor maire")).get("roleDev") + " ; recu "
                            + String.join(", ", dons);
                });
    }

    /** Le contenu des 36 cases du sac et de la barre, par identifiant (vide : « »), sans les pièces ni les bourses. */
    private static List<String> cases() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            boolean argent = CoinMath.valueOf(s) > 0 || s.getItem() instanceof dev.oneforallsurvival.economy.PurseItem;
            out.add(s.isEmpty() || argent ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()) + "x" + s.getCount());
        }
        return out;
    }

    /**
     * Retire, case par case, ce que l'étape a mis dans une case vide au départ (jamais un {@code /clear}, qui prendrait la
     * première pile venue, peut-être celle d'une autre étape), et rend la case tenue.
     */
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

    private static void relever(String quand) {
        pas(quand + "Départ de l'étape : position, inventaire, règles du monde et rôle de Dev relevés", () -> {
            fermer();
            depart = mc.player.blockPosition();
            inventaireAvant = inventaire();
            casesAvant = cases();
            tenueAvant = mc.player.getInventory().selected;
            porteAvant = valeurPortee();
            difficulteAvant = dernierMot(rcon("difficulty"));
            naissancesAvant = dernierMot(rcon("gamerule doMobSpawning"));
            roleDevAvant = lire(rcon("e2etresor maire")).getOrDefault("roleDev", "citoyen");
        }, () -> ecoule() >= 0.3, () -> "", () -> "depart " + depart.toShortString() + " ; inventaire " + inventaireAvant + " ; porte "
                + porteAvant + " ; difficulte " + difficulteAvant + ", naissances " + naissancesAvant + ", role de Dev " + roleDevAvant);
    }

    // ================================================================================================
    // Premier passage
    // ================================================================================================

    static void premierePhase() {
        relever("");
        pas("Origine de l'aire choisie", () -> {
            BlockPos spawn = mc.level.getSharedSpawnPos();
            // Une frontière de chunk entre les connecteurs (x = 4 et 8, côté poste) et les maisons (x = 9 à 13) : le pack
            // (addon d'Advanced Electricity, mod « light_bulb_addon… ») force le chargement des chunks de certains
            // connecteurs de Crafts & Additions reliés par un fil ; sans cette frontière, la maison d'Abonne tombait dans
            // un chunk forcé, chargé dès le démarrage de la relance (vu à la revue finale, selon la position de départ).
            int x = depart.getX() - 640;
            x -= Math.floorMod(x + 9, 16);
            int z = depart.getZ() - 640;
            if (Math.abs(x - spawn.getX()) <= 40 && Math.abs(z - spawn.getZ()) <= 40) {
                z -= 120;
            }
            o = new BlockPos(x, Y_AIRE, z);
        }, () -> true, () -> "", null);
        allerALAire("");
        pas("Aire close, couverte et éclairée ; alternateur, maisons d'Abonne et de Fauche (compteur à leur nom, disjoncteur, "
                + "machine d'essai) posés par la console", () -> {
            rcon("difficulty peaceful");
            rcon("gamerule doMobSpawning false");
            rcon("kill @e[type=!player,x=" + (o.getX() - 1) + ",y=" + (o.getY() - 1) + ",z=" + (o.getZ() - 1) + ",dx=17,dy=6,dz=11]");
            rcon("fill " + boite() + " stone hollow");
            for (int x = 1; x <= 13; x += 4) {
                rcon("setblock " + xyz(p(x, 4, 4)) + " glowstone");
                rcon("setblock " + xyz(p(x, 4, 1)) + " glowstone");
                rcon("setblock " + xyz(p(x, 4, 7)) + " glowstone");
            }
            rcon("setblock " + xyz(alternateur()) + " createaddition:alternator[facing=west]");
            for (String qui : List.of(ABONNE, FAUCHE)) {
                rcon("setblock " + xyz(compteur(qui)) + " oneforallsurvival:meter[facing=east]{subscriber:\"" + qui + "\"}");
                rcon("setblock " + xyz(disjoncteur(qui)) + " oneforallsurvival:breaker[facing=east,closed=true]{subscriber:\"" + qui + "\"}");
                rcon("setblock " + xyz(machine(qui)) + " oneforallsurvival:test_machine");
            }
        }, () -> ecoule() >= 1.0 && mc.level.getBlockState(p(-1, 2, 4)).is(net.minecraft.world.level.block.Blocks.STONE)
                && mc.level.getBlockState(compteur(FAUCHE)).is(GridRegistry.METER.get())
                && mc.level.getBlockState(disjoncteur(ABONNE)).is(GridRegistry.BREAKER.get())
                && !mc.level.getBlockState(alternateur()).isAir() && !mc.level.getBlockState(machine(FAUCHE)).isAir(),
                () -> "compteur " + mc.level.getBlockState(compteur(FAUCHE)) + ", alternateur " + mc.level.getBlockState(alternateur()),
                () -> "aire close en " + boite() + " ; alternateur en " + alternateur().toShortString() + " ; maisons : compteur, disjoncteur, "
                        + "machine en " + compteur(ABONNE).toShortString() + " (Abonne) et " + compteur(FAUCHE).toShortString() + " (Fauche)");
        pas("Abonne entre (somme de départ) et range tout dans son coffre-fort ; Fauche entre sans rien, coffre-fort vide, puis sort", () -> {
            Map<String, String> a = lire(rcon("e2eelec entrer " + ABONNE + " " + xyz(allee(12)) + " true"));
            departAbonne = num(a, "porte");
            reponse = rcon("e2eelec coffre " + ABONNE + " " + xyz(coffreFort(ABONNE))) + " | "
                    + rcon("e2eelec entrer " + FAUCHE + " " + xyz(allee(13)) + " false") + " | "
                    + rcon("e2eelec coffre " + FAUCHE + " " + xyz(coffreFort(FAUCHE))) + " | "
                    + rcon("e2eelec quitter " + FAUCHE);
        }, () -> ecoule() >= 1.0, () -> "", () -> {
            Map<String, String> ca = lire(rcon("e2etresor coffre " + ABONNE));
            Map<String, String> cf = lire(rcon("e2etresor coffre " + FAUCHE));
            String[] r = reponse.split(" \\| ");
            Map<String, String> coffreA = lire(r[0]);
            Map<String, String> entreF = lire(r[1]);
            if (departAbonne <= 0 || num(ca, "total") != departAbonne || !"true".equals(ca.get("pose")) || num(coffreA, "porte") != 0
                    || !"true".equals(entreF.get("sansDepart")) || num(entreF, "porte") != 0 || num(cf, "total") != 0
                    || !"true".equals(cf.get("pose")) || !reponse.contains("PARTI nom=" + FAUCHE)) {
                throw new IllegalStateException(reponse + " ; coffres " + ca + " / " + cf);
            }
            return "Abonne recoit " + departAbonne + " cuivres (somme de depart) et les range (« Deposer tout ») : coffre-fort " + ca.get("total")
                    + ", il ne porte plus rien ; Fauche (somme de depart notee donnee, rien cree) : coffre-fort pose et vide, rien porte ; "
                    + "Fauche est reparti (il est connu du serveur, absent)";
        });
        survie("", "oneforallsurvival:power_station 1", "oneforallsurvival:tester 1");

        // --- Le poste : pose, nom, tarifs.
        pas("Dev pose son poste d'un vrai clic droit, regard à l'est (sortie à l'est, entrée contre l'alternateur)", () -> {
            placerJoueur(allee(3), -90, 30);
        }, () -> ecoule() >= 0.8 && pres(allee(3)), () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Clic droit sur le sol avec le poste", () -> {
            tenir("oneforallsurvival:power_station");
            clic(poste().below(), Direction.UP);
        }, () -> ecoule() >= 1.0 && mc.level.getBlockState(poste()).is(GridRegistry.POWER_STATION.get()),
                () -> "bloc " + mc.level.getBlockState(poste()), () -> {
                    BlockState etat = mc.level.getBlockState(poste());
                    if (etat.getValue(InlineEnergyBlock.FACING) != Direction.EAST) {
                        throw new IllegalStateException("poste tourne " + etat.getValue(InlineEnergyBlock.FACING));
                    }
                    return "poste pose en " + poste().toShortString() + " par Dev (sortie " + etat.getValue(InlineEnergyBlock.FACING)
                            + ", entree contre l'alternateur) ; fournisseur au serveur : " + rcon("e2eelec fournisseur Dev").replace(" FIN_E2E", "");
                });
        ouvrirPanneau("");
        pas("Poste neuf : le panneau demande le nom du fournisseur", () -> {}, () -> ecoule() >= 0.5, () -> "", () -> {
            PowerPanelScreen s = panneau();
            if (!s.state().name().isEmpty() || s.layout().nameField() == null || s.layout().foundButton() == null) {
                throw new IllegalStateException("etat " + s.state().name() + ", champ " + s.layout().nameField());
            }
            return "poste neuf : « " + s.layout().title().full() + " », « " + s.layout().nameLabel().full() + " » ; capture "
                    + capturer("e6_panneau_poste_neuf");
        });
        pas("Dev tape le nom de son fournisseur et clique « " + "Fonder" + " »", () -> {
            taper(NOM_FOURNISSEUR);
            cliquer(panneau().layout().foundButton(), "Fonder");
        }, () -> panneauOuvert() && NOM_FOURNISSEUR.equals(panneau().state().name()) && panneau().layout().price() != null && ecoule() >= 1.0,
                () -> "etat " + (panneauOuvert() ? panneau().state() : mc.screen), () -> {
                    Map<String, String> f = fournisseur();
                    if (!("[" + NOM_FOURNISSEUR + "]").equals(f.get("nom"))) {
                        throw new IllegalStateException("fournisseur " + f);
                    }
                    return "nom tape dans le champ (" + NOM_FOURNISSEUR.length() + " caracteres) et « Fonder » clique : le serveur fonde "
                            + f.get("nom") + " au nom de Dev ; le panneau passe aux tarifs";
                });
        pas("Dev tape le prix (" + PRIX + ") et clique « OK »", () -> {
            cliquer(panneau().layout().price().box(), "champ du prix");
            taper(Long.toString(PRIX));
            cliquer(panneau().layout().price().button(), "OK du prix");
        }, () -> panneauOuvert() && panneau().state().nextPrice() == PRIX && ecoule() >= 1.0, () -> "etat " + panneau().state(), () -> {
            String attendu = tr(GUI + "price", CoinMath.format(0)) + " / " + tr(GUI + "tomorrow", CoinMath.format(PRIX));
            PowerPanelLayout.Field champ = panneau().layout().price();
            String vu = champ.label().full() + " / " + champ.next().full();
            if (!attendu.equals(vu) || panneau().state().price() != 0 || champ.label().cut() || champ.next().cut()) {
                throw new IllegalStateException("prix « " + vu + " » (montré « " + champ.label().shown() + " / "
                        + champ.next().shown() + " »), attendu « " + attendu + " », entier");
            }
            return "prix tape et « OK » clique : le panneau affiche « " + vu + " » (au lendemain) ; serveur : prix demain "
                    + fournisseur().get("prixDemain");
        });
        pas("Dev tape l'abonnement (" + ABONNEMENT + ") et clique « OK »", () -> {
            cliquer(panneau().layout().subscription().box(), "champ de l'abonnement");
            taper(Long.toString(ABONNEMENT));
            cliquer(panneau().layout().subscription().button(), "OK de l'abonnement");
        }, () -> panneauOuvert() && panneau().state().nextSubscription() == ABONNEMENT && ecoule() >= 1.0, () -> "etat " + panneau().state(), () -> {
            String attendu = tr(GUI + "subscription", CoinMath.format(0)) + " / "
                    + tr(GUI + "tomorrow", CoinMath.format(ABONNEMENT));
            PowerPanelLayout.Field champ = panneau().layout().subscription();
            String vu = champ.label().full() + " / " + champ.next().full();
            if (!attendu.equals(vu) || champ.label().cut() || champ.next().cut()) {
                throw new IllegalStateException("abonnement « " + vu + " » (montré « " + champ.label().shown() + " / "
                        + champ.next().shown() + " »), attendu « " + attendu + " », entier");
            }
            return "abonnement tape et « OK » clique : « " + vu + " » (au lendemain, rien de coupe a " + mc.getWindow().getWidth() + " x "
                    + mc.getWindow().getHeight() + ") ; capture " + capturer("e6_panneau_tarifs_au_lendemain");
        });
        pas("Tarifs du lendemain appliqués tout de suite (console : clôture du jour du fournisseur, comme à minuit)", () -> {
            fermer();
            reponse = rcon("e2eelec tarifs Dev");
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            Map<String, String> t = lire(reponse);
            Map<String, String> f = fournisseur();
            if (num(t, "prix") != PRIX || num(t, "abonnement") != ABONNEMENT || num(f, "prix") != PRIX || !"-".equals(f.get("prixDemain"))) {
                throw new IllegalStateException(reponse + " ; " + f);
            }
            return "tarifs en vigueur : " + reponse.replace(" FIN_E2E", "").trim() + " (la facture du jour portera le prix)";
        });

        // --- Le réseau : fil suspendu de Crafts & Additions, moteur créatif.
        pasD(30, "Fil suspendu de Crafts & Additions du poste aux deux maisons (connecteurs, modes, bobine de cuivre), moteur créatif", () -> {
            reponse = rcon("e2eelec reseau " + xyz(poste()) + " " + xyz(compteur(ABONNE)) + " " + xyz(compteur(FAUCHE)) + " false");
            clicNs = System.nanoTime();
            etatBoucle = 0;
        }, () -> {
            if (etatBoucle == 0 && System.nanoTime() - clicNs > 1_500_000_000L) {
                reponse = reponse + " || " + rcon("e2eelec reseau " + xyz(poste()) + " " + xyz(compteur(ABONNE)) + " "
                        + xyz(compteur(FAUCHE)) + " true");
                rcon("setblock " + xyz(moteur()) + " create:creative_motor[facing=east]{ScrollValue:256}");
                rcon("e2eelec charge " + xyz(machine(ABONNE)));
                rcon("e2eelec charge " + xyz(machine(FAUCHE)));
                etatBoucle = 1;
                clicNs = System.nanoTime();
                return false;
            }
            return etatBoucle == 1 && System.nanoTime() - clicNs > 4_000_000_000L;
        }, () -> reponse, () -> {
            Map<String, String> f = fournisseur();
            Map<String, String> ma = machineEtat(ABONNE);
            if (!reponse.toLowerCase(java.util.Locale.ROOT).contains("connector:pull,push,push") || num(ma, "progres") != 0 || num(ma, "energie") != 0) {
                throw new IllegalStateException(reponse + " ; machine " + ma + " ; " + f);
            }
            return "reseau : " + reponse.replace(" FIN_E2E", "").trim() + " ; moteur creatif 256 tr/min ; sortie du poste " + f.get("sortie")
                    + " FE ; compteurs non jumeles : la machine d'Abonne ne recoit rien (" + ma + ")";
        });

        // --- Raccordement d'Abonne.
        jumeler("le compteur d'Abonne", () -> compteur(ABONNE), "message.oneforallsurvival.power.paired");
        pas("Le serveur attend l'accord d'Abonne (message reçu, connecté)", () -> {}, () -> ecoule() >= 0.3, () -> "", () -> {
            Cl c = client(ABONNE);
            if (c == null || !"AWAITING_CONSENT".equals(c.etat())) {
                throw new IllegalStateException("client " + c);
            }
            return "compteur d'Abonne jumele : " + c;
        });
        jumeler("le disjoncteur d'Abonne", () -> disjoncteur(ABONNE), "message.oneforallsurvival.power.breaker_paired");
        pas("Abonne donne son accord : le lien « Accepter » du message, joué en son nom", () -> reponse = rcon("e2eelec accord " + ABONNE),
                () -> ecoule() >= 0.5, () -> "", () -> {
                    Map<String, String> r = lire(reponse);
                    Cl c = client(ABONNE);
                    if (!"1".equals(r.get("resultat")) || !"message.oneforallsurvival.power.request".equals(r.get("demande")) || c == null
                            || !"AWAITING_ACCEPT".equals(c.etat()) || c.disjoncteurs() != 1) {
                        throw new IllegalStateException(reponse + " ; client " + c);
                    }
                    return "accord d'Abonne : " + reponse.replace(" FIN_E2E", "").trim() + " ; client " + c;
                });
        pasD(10, "Avant l'acceptation, la machine d'Abonne ne reçoit rien (3 s)", () -> {}, () -> ecoule() >= 3.0, () -> "", () -> {
            Map<String, String> ma = machineEtat(ABONNE);
            Cl c = client(ABONNE);
            if (num(ma, "energie") != 0 || num(ma, "progres") != 0 || c.feJour() != 0) {
                throw new IllegalStateException("machine " + ma + " ; client " + c);
            }
            return "accord donne, pas encore accepte : machine d'Abonne " + ma + ", consommation du jour " + c.feJour() + " FE";
        });
        ouvrirPanneau("");
        pas("Le panneau montre la demande d'Abonne à accepter ; vrai clic sur « Accepter »", () -> {
            PowerPanelPacket.Client c = rang(ABONNE);
            if (c == null || c.state() != PowerClient.State.AWAITING_ACCEPT) {
                throw new IllegalStateException("ligne " + decrire(c));
            }
            reponse = "demande au panneau : « " + panneau().layout().rows().get(0).state().full() + " » ; capture " + capturer("e6_panneau_demande_a_accepter");
            cliquerRang(ABONNE, 0, "Accepter");
        }, () -> panneauOuvert() && rang(ABONNE) != null && rang(ABONNE).state() == PowerClient.State.ACTIVE && ecoule() >= 1.0,
                () -> "ligne " + decrire(rang(ABONNE)), () -> reponse + " ; « Accepter » clique : " + decrire(rang(ABONNE)));
        pasD(20, "La machine d'Abonne tourne ; sa consommation se compte", () -> fermer(), () -> {
            Map<String, String> ma = machineEtat(ABONNE);
            return ecoule() >= 2.0 && num(ma, "progres") > 0 && client(ABONNE).feJour() > 0;
        }, () -> "machine " + machineEtat(ABONNE) + " ; client " + client(ABONNE), () -> "accepte : machine d'Abonne " + machineEtat(ABONNE)
                + " ; client " + client(ABONNE));

        // --- Raccordement de Fauche, absent au jumelage (cache des profils).
        jumeler("le compteur de Fauche (Fauche absent)", () -> compteur(FAUCHE), "message.oneforallsurvival.power.paired");
        pas("Fauche, absent, est reconnu par le cache des profils : le jumelage attend son accord", () -> {}, () -> ecoule() >= 0.3, () -> "", () -> {
            Cl c = client(FAUCHE);
            String present = rcon("e2eelec chunk " + xyz(allee(7)));
            if (c == null || !"AWAITING_CONSENT".equals(c.etat()) || !FAUCHE.equals(c.joueur())) {
                throw new IllegalStateException("client " + c + " ; " + present);
            }
            return "Fauche hors ligne (joueurs connectes : " + lire(present).get("joueurs") + ") reconnu par le cache des profils : client " + c;
        });
        jumeler("le disjoncteur de Fauche", () -> disjoncteur(FAUCHE), "message.oneforallsurvival.power.breaker_paired");
        pas("Fauche se connecte : la demande d'accord lui arrive à l'entrée ; il donne son accord", () -> {
            reponse = rcon("e2eelec entrer " + FAUCHE + " " + xyz(allee(13)) + " false");
            reponse = reponse + " | " + rcon("e2eelec accord " + FAUCHE);
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            String[] r = reponse.split(" \\| ");
            Map<String, String> e = lire(r[0]);
            Map<String, String> a = lire(r[1]);
            Cl c = client(FAUCHE);
            if (!e.get("connexion").contains("message.oneforallsurvival.power.request") || !e.get("liens").contains("/power_consent")
                    || !"1".equals(a.get("resultat")) || c == null || !"AWAITING_ACCEPT".equals(c.etat())) {
                throw new IllegalStateException(reponse + " ; client " + c);
            }
            return "Fauche a la connexion : " + e.get("connexion") + " (liens " + e.get("liens") + ") ; accord : " + a.get("lien") + " ; client " + c;
        });
        ouvrirPanneau("");
        pas("Vrai clic sur « Accepter » pour Fauche", () -> cliquerRang(FAUCHE, 0, "Accepter"),
                () -> panneauOuvert() && rang(FAUCHE) != null && rang(FAUCHE).state() == PowerClient.State.ACTIVE && ecoule() >= 1.0,
                () -> "ligne " + decrire(rang(FAUCHE)), () -> "« Accepter » clique : " + decrire(rang(FAUCHE)));
        pasD(20, "La machine de Fauche tourne", () -> {}, () -> ecoule() >= 2.0 && num(machineEtat(FAUCHE), "progres") > 0
                && client(FAUCHE).feJour() > 0, () -> "machine " + machineEtat(FAUCHE), () -> "Fauche raccorde : machine " + machineEtat(FAUCHE)
                + " ; client " + client(FAUCHE));

        // --- Coupure, refus du sans-droit, plafond, rétablissement.
        pas("Vrai clic sur « Couper » pour Fauche : son disjoncteur s'ouvre", () -> cliquerRang(FAUCHE, 0, "Couper"),
                () -> panneauOuvert() && rang(FAUCHE) != null && rang(FAUCHE).cut() && ecoule() >= 1.0
                        && !mc.level.getBlockState(disjoncteur(FAUCHE)).getValue(BreakerBlock.CLOSED),
                () -> "ligne " + decrire(rang(FAUCHE)) + ", disjoncteur " + mc.level.getBlockState(disjoncteur(FAUCHE)), () -> {
                    Cl c = client(FAUCHE);
                    VU.put("fauche_coupe", c.feJour());
                    if (!c.coupe()) {
                        throw new IllegalStateException("client " + c);
                    }
                    return "« Couper » clique : " + decrire(rang(FAUCHE)) + " ; disjoncteur ouvert (vu par le client) ; serveur " + c + " ; capture "
                            + capturer("e6_panneau_fauche_coupe");
                });
        pasD(10, "Fauche coupé : sa consommation ne bouge plus (2 s)", () -> {}, () -> ecoule() >= 2.0, () -> "", () -> {
            Cl c = client(FAUCHE);
            if (c.feJour() != VU.get("fauche_coupe")) {
                throw new IllegalStateException(VU.get("fauche_coupe") + " -> " + c.feJour());
            }
            return "Fauche coupe : consommation figee a " + c.feJour() + " FE";
        });
        pas("Fauche (client, sans droit) tente de réenclencher son disjoncteur puis de casser son compteur : refusé", () ->
                reponse = rcon("e2eelec intrus " + FAUCHE + " " + xyz(disjoncteur(FAUCHE)) + " " + xyz(compteur(FAUCHE))),
                () -> ecoule() >= 0.5, () -> "", () -> {
                    Map<String, String> r = lire(reponse);
                    Cl c = client(FAUCHE);
                    if (!"false".equals(r.get("fermeAvant")) || !"false".equals(r.get("fermeApres")) || !"true".equals(r.get("compteurLa"))
                            || !"false".equals(r.get("casse")) || !r.get("messagesClic").contains("message.oneforallsurvival.power.touch_client")
                            || !c.coupe() || mc.level.getBlockState(disjoncteur(FAUCHE)).getValue(BreakerBlock.CLOSED)) {
                        throw new IllegalStateException(reponse + " ; client " + c);
                    }
                    return "refuse : " + reponse.replace(" FIN_E2E", "").trim() + " ; toujours coupe";
                });
        pas("Le maire simulé règle le plafond à " + PLAFOND + " (/power cap) : Dev est prévenu", () -> {
            fermer();
            chatAvant = CHAT.size();
            reponse = rcon("e2eelec maire plafond " + PLAFOND);
        }, () -> ecoule() >= 1.0 && chatCompte("message.oneforallsurvival.power.cap_lowered", chatAvant) >= 1,
                () -> reponse + " ; chat " + CHAT.subList(chatAvant, CHAT.size()), () -> {
                    Map<String, String> r = lire(reponse);
                    if (!"true".equals(r.get("elu")) || !"false".equals(r.get("operateur")) || !"1".equals(r.get("resultat"))
                            || !r.get("cles").contains("command.oneforallsurvival.power.cap_set") || num(r, "plafondDemain") != PLAFOND) {
                        throw new IllegalStateException(reponse);
                    }
                    return "maire simule : " + reponse.replace(" FIN_E2E", "").trim() + " ; Dev lit « "
                            + tr("message.oneforallsurvival.power.cap_lowered", CoinMath.format(PLAFOND), 100000, NOM_FOURNISSEUR) + " »";
                });
        ouvrirPanneau("");
        pas("Le panneau annonce le plafond de " + PLAFOND + " au lendemain (prix ramené à minuit)", () -> {}, () -> ecoule() >= 0.5, () -> "", () -> {
            PowerPanelPacket s = panneau().state();
            PowerPanelLayout.Text demain = panneau().layout().capNext();
            String cap = panneau().layout().cap().full() + " / " + demain.full();
            String attendu = tr(GUI + "cap_next", CoinMath.format(PLAFOND), 0);
            if (s.nextCap() != PLAFOND || s.price() != PRIX || !attendu.equals(demain.full()) || demain.cut()
                    || panneau().layout().cap().cut()) {
                throw new IllegalStateException("plafond « " + cap + " » (montre « " + demain.shown() + " ») ; etat " + s);
            }
            return "panneau : « " + cap + " » (entier), prix du jour « " + panneau().layout().price().label().full() + " » ; capture "
                    + capturer("e6_panneau_plafond_au_lendemain");
        });
        pas("Vrai clic sur « Rétablir » pour Fauche avant l'arrêt : son disjoncteur se referme", () -> cliquerRang(FAUCHE, 0, "Retablir"),
                () -> panneauOuvert() && rang(FAUCHE) != null && !rang(FAUCHE).cut() && ecoule() >= 1.0
                        && mc.level.getBlockState(disjoncteur(FAUCHE)).getValue(BreakerBlock.CLOSED),
                () -> "ligne " + decrire(rang(FAUCHE)), () -> "« Retablir » clique : " + decrire(rang(FAUCHE)) + " ; disjoncteur referme");
        pasD(20, "Fauche rétabli : sa machine reprend", () -> {
            VU.put("fauche_retabli", client(FAUCHE).feJour());
        }, () -> ecoule() >= 2.0 && client(FAUCHE).feJour() > VU.get("fauche_retabli"),
                () -> "client " + client(FAUCHE), () -> "Fauche retabli : " + VU.get("fauche_retabli") + " -> " + client(FAUCHE).feJour() + " FE");

        // --- Avant l'arrêt : le courant s'arrête, panneau et serveur comparés, état écrit.
        pasD(60, "Le moteur est retiré ; la consommation se fige (rien ne bouge pendant 3 s)", () -> {
            fermer();
            rcon("setblock " + xyz(moteur()) + " air");
            VU.put("stable", 0L);
            reponse = "";
            clicNs = System.nanoTime();
        }, () -> {
            if (System.nanoTime() - clicNs < 3_000_000_000L) {
                return false;
            }
            clicNs = System.nanoTime();
            Map<String, String> f = fournisseur();
            String cle = f.get("sortie") + "/" + client(f, ABONNE).feJour() + "/" + client(f, FAUCHE).feJour();
            boolean stable = cle.equals(reponse);
            reponse = cle;
            return stable;
        }, () -> "toujours en mouvement : " + reponse, () -> "courant arrete : sortie/Abonne/Fauche = " + reponse);
        ouvrirPanneau("Avant l'arrêt : ");
        pas("Avant l'arrêt : consommations du panneau = consommations relues par la console ; écart du jour", () -> {}, () -> ecoule() >= 0.5,
                () -> "", () -> {
                    PowerPanelPacket s = panneau().state();
                    Map<String, String> f = fournisseur();
                    Cl a = client(f, ABONNE);
                    Cl fa = client(f, FAUCHE);
                    PowerPanelPacket.Client pa = rang(ABONNE);
                    PowerPanelPacket.Client pf = rang(FAUCHE);
                    if (pa.feToday() != a.feJour() || pf.feToday() != fa.feJour() || s.outToday() != num(f, "sortie")
                            || s.metersToday() != a.feJour() + fa.feJour() || a.feJour() <= 0 || fa.feJour() <= 0) {
                        throw new IllegalStateException("panneau " + decrire(pa) + " / " + decrire(pf) + " sortie " + s.outToday() + " compteurs "
                                + s.metersToday() + " ; serveur " + f);
                    }
                    return "panneau = console : Abonne " + pa.feToday() + " FE, Fauche " + pf.feToday() + " FE ; ecart du jour : sortie "
                            + s.outToday() + " FE, compteurs " + s.metersToday() + " FE (« " + panneau().layout().today().gap().full() + " ») ; capture "
                            + capturer("e6_panneau_avant_arret");
                });
        pas("Le poste vu en jeu (contre l'alternateur, fil suspendu vers les maisons)", () -> {
            fermer();
            placerJoueur(p(6, 0, 4), 135, 20);
        }, () -> ecoule() >= 2.0 && pres(p(6, 0, 4)), () -> "joueur en " + mc.player.blockPosition(),
                () -> "capture " + capturer("e6_poste_en_jeu"));
        pas("Abonne et Fauche se déconnectent ; la vidange des machines s'arrête", () -> {
            reponse = rcon("e2eelec quitter " + ABONNE) + " | " + rcon("e2eelec quitter " + FAUCHE) + " | " + rcon("e2eelec charge stop");
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            if (!reponse.contains("PARTI nom=" + ABONNE) || !reponse.contains("PARTI nom=" + FAUCHE)) {
                throw new IllegalStateException(reponse);
            }
            return "deconnectes : " + reponse.replace(" FIN_E2E", "").trim();
        });
        pas("Dev quitte l'aire : objets de l'étape retirés, règles du monde et rôle rendus ; il rentre, opérateur", () -> {
            airePrete = false;
            fermer();
            reponse = rangerInventaire();
            rcon("difficulty " + difficulteAvant);
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("role set " + nom() + " " + roleDevAvant);
            rcon("op " + nom());
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && operateur() && mc.player.blockPosition().distSqr(depart) < 9 && cases().equals(casesAvant),
                () -> "cases " + cases() + " (avant " + casesAvant + "), operateur " + operateur(), () -> {
                    if (valeurPortee() != porteAvant) {
                        throw new IllegalStateException("Dev porte " + valeurPortee() + " (avant " + porteAvant + ")");
                    }
                    return "objets de l'etape retires case par case (" + reponse + ") ; chaque case du sac et de la barre comme au depart ; Dev porte "
                            + valeurPortee() + " cuivres comme avant ; il rentre, operateur";
                });
        pas("Avant l'arrêt : consommations, caisse, factures et registre écrits ; références de relance de la monnaie et du commerce "
                + "réécrites", () -> {}, () -> ecoule() >= 1.0, () -> "", () -> {
                    Map<String, String> f = fournisseur();
                    Map<String, String> fa = lire(rcon("e2eelec factures Dev"));
                    Map<String, String> j = lire(rcon("e2eelec jour"));
                    Map<String, String> ca = lire(rcon("e2etresor coffre " + ABONNE));
                    Map<String, String> cf = lire(rcon("e2etresor coffre " + FAUCHE));
                    long coffreDev = num(lire(rcon("e2etresor coffre Dev")), "total");
                    String stats = rcon("eco stats").trim().replace('\n', '|');
                    String ledger = rcon("eco ledger").split("\n")[0].trim();
                    Map<String, String> r = lire(rcon("e2eelec registre"));
                    if (num(fa, "n") != 0 || client(f, ABONNE).coupe() || client(f, FAUCHE).coupe() || num(f, "prix") != PRIX
                            || num(f, "abonnement") != ABONNEMENT || num(f, "plafondDemain") != PLAFOND || num(f, "taxe") != 0) {
                        throw new IllegalStateException("fournisseur " + f + " ; factures " + fa);
                    }
                    E2eCommerce.noterStatsAvantArret(mc, stats);
                    E2eMonnaie.noterCoffreAvantArret(mc, coffreDev, stats, ledger);
                    List<String> l = new ArrayList<>();
                    l.add("origine=" + xyz(o));
                    l.add("jour=" + j.get("jour"));
                    l.add("abonne=" + client(f, ABONNE).feJour());
                    l.add("fauche=" + client(f, FAUCHE).feJour());
                    l.add("sortie=" + f.get("sortie"));
                    l.add("caisse=" + f.get("caisse"));
                    l.add("coffreAbonne=" + ca.get("total"));
                    l.add("coffreFauche=" + cf.get("total"));
                    for (String cle : List.of("refus", "payees", "paye", "public", "taxe", "lignes", "coffres", "caisses", "dues", "duesTotal")) {
                        l.add("r_" + cle + "=" + r.get(cle));
                    }
                    l.add("arret=" + r.get("reel"));
                    l.add("fournisseur=" + rcon("e2eelec fournisseur Dev").replace(" FIN_E2E", "").trim());
                    try {
                        Files.write(fichier(), l, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    return FICHIER + " ecrit : jour " + j.get("jour") + ", Abonne " + client(f, ABONNE).feJour() + " FE, Fauche "
                            + client(f, FAUCHE).feJour() + " FE, caisse " + f.get("caisse") + ", factures " + fa.get("n") + " ; registre "
                            + rcon("e2eelec registre").replace(" FIN_E2E", "").trim() + " ; references de la monnaie et du commerce reecrites (stats "
                            + stats + " ; " + ledger + ")";
                });
    }

    // ================================================================================================
    // Après un arrêt et une relance du serveur (-Pe2eHorloge : deux jours de plus)
    // ================================================================================================

    static void relance() {
        Map<String, String> lu = new LinkedHashMap<>();
        relever("Relance : ");
        pas("Relance : à minuit (au démarrage), deux jours clos ; l'aire n'a jamais été chargée depuis", () -> {
            lu.putAll(lireFichier());
            o = position(lu.get("origine"));
        }, () -> ecoule() >= 0.3, () -> "", () -> {
            Map<String, String> j = lire(rcon("e2eelec jour"));
            java.time.LocalDate essai = java.time.LocalDate.parse(lu.get("jour"));
            Map<String, String> ch = lire(rcon("e2eelec chunk " + xyz(compteur(ABONNE))));
            if (!essai.plusDays(2).toString().equals(j.get("jour")) || !essai.plusDays(1).toString().equals(j.get("presente"))
                    || !essai.plusDays(1).toString().equals(j.get("emis")) || !"false".equals(ch.get("charge"))) {
                throw new IllegalStateException("jour " + j + " (essai " + essai + ") ; " + ch);
            }
            return "relance : jour " + j.get("jour") + ", jours presentes jusqu'au " + j.get("presente") + " (essai le " + essai
                    + ") ; compteur d'Abonne dans un chunk non charge : " + ch;
        });
        pas("Relance : deux factures par compteur ; celles d'Abonne prélevées au démarrage (absent, zone non chargée), "
                + "celles de Fauche dues", () -> {}, () -> ecoule() >= 0.3, () -> "", () -> {
                    List<Fa> f = factures(lire(rcon("e2eelec factures Dev")));
                    java.time.LocalDate essai = java.time.LocalDate.parse(lu.get("jour"));
                    long debutClient = ManagementFactory.getRuntimeMXBean().getStartTime();
                    long feA = Long.parseLong(lu.get("abonne"));
                    long feF = Long.parseLong(lu.get("fauche"));
                    long unite = 100_000L;
                    List<String> attendu = List.of(
                            ABONNE + ":" + (feA * PRIX / unite + ABONNEMENT) + ":PAID:" + essai + ":" + feA + ":" + PRIX + ":" + ABONNEMENT,
                            ABONNE + ":" + ABONNEMENT + ":PAID:" + essai.plusDays(1) + ":0:" + PLAFOND + ":" + ABONNEMENT,
                            FAUCHE + ":" + (feF * PRIX / unite + ABONNEMENT) + ":DUE:" + essai + ":" + feF + ":" + PRIX + ":" + ABONNEMENT,
                            FAUCHE + ":" + ABONNEMENT + ":DUE:" + essai.plusDays(1) + ":0:" + PLAFOND + ":" + ABONNEMENT);
                    List<String> vu = new ArrayList<>();
                    for (Fa x : f) {
                        vu.add(x.debiteur() + ":" + x.montant() + ":" + x.statut() + ":" + x.jour() + ":" + x.fe() + ":" + x.prix() + ":" + x.abonnement());
                    }
                    List<String> vuTrie = new ArrayList<>(vu);
                    vuTrie.sort(null);
                    List<String> attenduTrie = new ArrayList<>(attendu);
                    attenduTrie.sort(null);
                    long dueF = f.stream().filter(x -> "DUE".equals(x.statut())).mapToLong(Fa::montant).sum();
                    long payeA = f.stream().filter(x -> "PAID".equals(x.statut())).mapToLong(Fa::montant).sum();
                    // Le premier contrôle du client (la somme de départ de la monnaie, dès la connexion) a déjà lu ces prélèvements.
                    Long auDemarrage = VU.get("payeDemarrage");
                    if (!vuTrie.equals(attenduTrie) || auDemarrage == null || auDemarrage != payeA) {
                        throw new IllegalStateException("factures " + vu + " ; attendues " + attendu + " ; preleve au premier controle du client : "
                                + auDemarrage);
                    }
                    VU.put("payeA", payeA);
                    VU.put("dueF", dueF);
                    return "factures : " + vu + " (jour de l'essai : " + feA + " FE x " + PRIX + " / 100 000 + " + ABONNEMENT + " ; lendemain : 0 FE au prix "
                            + "ramene a " + PLAFOND + ", abonnement " + ABONNEMENT + ") ; les " + payeA + " cuivres d'Abonne etaient deja preleves au premier "
                            + "controle du client, juste apres sa connexion (client lance a " + java.time.Instant.ofEpochMilli(debutClient) + ")";
                });
        pas("Relance : coffre-fort d'Abonne diminué d'autant, caisse augmentée d'autant ; refus au registre, « Factures publiques dues : 2 »",
                () -> {}, () -> ecoule() >= 0.3, () -> "", () -> {
                    long payeA = VU.get("payeA");
                    long dueF = VU.get("dueF");
                    Map<String, String> r = lire(rcon("e2eelec registre"));
                    Map<String, String> f = fournisseur();
                    long coffreA = num(lire(rcon("e2etresor coffre " + ABONNE)), "total");
                    long coffreF = num(lire(rcon("e2etresor coffre " + FAUCHE)), "total");
                    String stats = rcon("eco stats");
                    String ligneDues = tr("command.oneforallsurvival.eco.stats.public_due", 2, CoinMath.format(dueF));
                    String ligneRefus = tr("command.oneforallsurvival.eco.stats.refused_debits", num(r, "refus"));
                    Map<String, String> jl = lire(rcon("e2eelec journal " + lu.get("arret")));
                    String genres = jl.get("genres");
                    // Revue finale, I3 : une ligne au premier refus de chaque facture (Fauche : facture du jour de l'essai refusée deux
                    // nuits, celle du lendemain une), soit deux lignes ; le registre compte deux factures refusées.
                    boolean journalOk = genres.contains(MoneyTransfer.PUBLIC_BILL.kind() + ":2;") && genres.contains("debit.refused:2;");
                    if (coffreA != Long.parseLong(lu.get("coffreAbonne")) - payeA || coffreF != 0 || num(f, "caisse") != Long.parseLong(lu.get("caisse")) + payeA
                            || num(r, "payees") != Long.parseLong(lu.get("r_payees")) + 2 || num(r, "paye") != Long.parseLong(lu.get("r_paye")) + payeA
                            || num(r, "public") != Long.parseLong(lu.get("r_public")) + payeA || num(r, "taxe") != Long.parseLong(lu.get("r_taxe"))
                            || num(r, "refus") != Long.parseLong(lu.get("r_refus")) + 2 || num(r, "dues") != 2 || num(r, "duesTotal") != dueF
                            || !stats.contains(ligneDues) || !stats.contains(ligneRefus) || !journalOk) {
                        throw new IllegalStateException("registre " + r + " ; avant " + lu + " ; coffres " + coffreA + "/" + coffreF + " ; journal " + jl
                                + " ; stats " + stats.replace('\n', '|'));
                    }
                    caisseAvant = num(f, "caisse");
                    return "coffre-fort d'Abonne " + lu.get("coffreAbonne") + " -> " + coffreA + " (-" + payeA + ") ; caisse " + lu.get("caisse") + " -> "
                            + f.get("caisse") + " (+" + payeA + ", taxe 0 %) ; registre : " + r + " ; journal depuis l'arret : " + genres
                            + " ; /eco stats : « " + ligneDues + " », « " + ligneRefus + " »";
                });
        allerALAire("Relance : ");
        pas("Relance : Abonne revient : les deux prélèvements faits pendant son absence lui sont dits", () ->
                reponse = rcon("e2eelec entrer " + ABONNE + " " + xyz(allee(12)) + " true") + " | " + rcon("e2eelec quitter " + ABONNE),
                () -> ecoule() >= 0.5, () -> "", () -> {
                    Map<String, String> e = lire(reponse.split(" \\| ")[0]);
                    String connexion = e.get("connexion");
                    int n = connexion.split("message.oneforallsurvival.debit.paid.safe", -1).length - 1;
                    if (n != 2 || num(e, "porte") != 0) {
                        throw new IllegalStateException(reponse);
                    }
                    return "Abonne a la connexion : " + connexion + " (deux fois « " + tr("message.oneforallsurvival.debit.paid.safe", "…", "…", "…")
                            + " ») ; il ne porte rien, aucune somme de depart redonnee";
                });
        pas("Relance : Fauche se connecte : l'avis de refus, avec le lien de copie de chaque facture due", () ->
                reponse = rcon("e2eelec entrer " + FAUCHE + " " + xyz(allee(13)) + " false") + " | " + rcon("e2eelec quitter " + FAUCHE),
                () -> ecoule() >= 0.5, () -> "", () -> {
                    Map<String, String> e = lire(reponse.split(" \\| ")[0]);
                    String connexion = e.get("connexion");
                    String liens = e.get("liens");
                    List<Fa> dues = factures(lire(rcon("e2eelec factures Dev"))).stream().filter(x -> "DUE".equals(x.statut())).toList();
                    int n = connexion.split("message.oneforallsurvival.debit.refused", -1).length - 1;
                    boolean tous = dues.size() == 2 && dues.stream().allMatch(x -> liens.contains("/invoice_copy_" + x.id()));
                    if (n != 2 || !tous) {
                        throw new IllegalStateException(reponse + " ; dues " + dues);
                    }
                    return "Fauche a la connexion : " + connexion + " ; liens « " + tr("message.oneforallsurvival.debit.copy_link") + " » : " + liens;
                });
        survie("Relance : ");
        ouvrirPanneau("Relance : ");
        pas("Relance : le panneau montre les impayés de Fauche, l'écart d'hier, le prix ramené à " + PLAFOND, () -> {}, () -> ecoule() >= 0.5,
                () -> "", () -> {
                    PowerPanelPacket s = panneau().state();
                    Map<String, String> f = fournisseur();
                    PowerPanelPacket.Client pf = rang(FAUCHE);
                    PowerPanelPacket.Client pa = rang(ABONNE);
                    if (pf == null || pa == null || pf.unpaid() != VU.get("dueF") || pa.unpaid() != 0 || s.price() != PLAFOND || s.cap() != PLAFOND
                            || s.outYesterday() != num(f, "sortieHier") || s.metersYesterday() != num(f, "compteursHier")
                            || s.till() != caisseAvant || CHAT.indexOf("message.oneforallsurvival.power.founder_unpaid_summary") < 0) {
                        throw new IllegalStateException("panneau " + decrire(pf) + " / " + decrire(pa) + " ; etat " + s + " ; serveur " + f + " ; chat " + CHAT);
                    }
                    PowerPanelLayout.RowLayout ligne = panneau().layout().rows().get(s.clients().indexOf(pf));
                    return "panneau : Fauche « " + ligne.unpaid().full() + " » (" + pf.unpaid() + " = factures dues) ; ecart d'hier : sortie "
                            + s.outYesterday() + ", compteurs " + s.metersYesterday() + " (« " + panneau().layout().yesterday().gap().full() + " ») ; « "
                            + panneau().layout().price().label().full() + " » ; caisse « " + panneau().layout().till().full() + " » ; Dev avait lu a sa "
                            + "connexion « founder_unpaid_summary » ; capture " + capturer("e6_panneau_relance_impayes");
                });
        pas("Relance : vrai clic sur « Caisse » : la caisse du poste s'ouvre", () -> {
            porteAvant = valeurPortee();
            cliquer(panneau().layout().tillButton(), "Caisse");
        }, () -> mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof PowerTillMenu && ecoule() >= 1.0,
                () -> "ecran " + mc.screen, () -> {
                    long total = ((CoinAccountMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).total();
                    if (total != caisseAvant) {
                        throw new IllegalStateException("caisse " + total + ", attendu " + caisseAvant);
                    }
                    return "caisse ouverte : " + total + " cuivres (" + fmt(total) + ") ; capture " + capturer("e6_caisse_relance");
                });
        pasD(120, "Relance : Dev vide la caisse, retrait par retrait (vrais clics)", () -> {
            etatBoucle = 0;
            clicNs = 0;
            retraits = 0;
        }, () -> {
            if (!(mc.screen instanceof AbstractContainerScreen<?> s) || !(s.getMenu() instanceof PowerTillMenu m)) {
                return false;
            }
            long total = m.total();
            long maintenant = System.nanoTime();
            if (etatBoucle == 1) {
                if (total < totalAvantClic) {
                    retraits++;
                    etatBoucle = 0;
                    clicNs = maintenant;
                } else if (maintenant - clicNs > 5_000_000_000L) {
                    throw new IllegalStateException("retrait sans effet : " + total);
                }
                return false;
            }
            if (total == 0) {
                return true;
            }
            if (maintenant - clicNs < 300_000_000L) {
                return false;
            }
            Coin piece = null;
            for (int i = Coin.values().length - 1; i >= 0 && piece == null; i--) {
                if (m.count(Coin.values()[i]) > 0) {
                    piece = Coin.values()[i];
                }
            }
            long n = m.count(piece);
            int quantite = n >= 64 ? 64 : n >= 10 ? 10 : 1;
            totalAvantClic = total;
            CoinAccountScreen<?> ecran = (CoinAccountScreen<?>) mc.screen;
            int[] c = ecran.elementCenter("row." + piece.key() + "." + quantite)
                    .orElseThrow(() -> new IllegalStateException("aucun bouton de retrait"));
            ecran.mouseClicked(c[0], c[1], 1);
            etatBoucle = 1;
            clicNs = maintenant;
            return false;
        }, () -> "ecran " + mc.screen, () -> {
            fermer();
            return "caisse videe en " + retraits + " retrait(s)";
        });
        pas("Relance : les pièces sont dans l'inventaire de Dev, total identique ; la caisse est vide au serveur", () -> {}, () -> ecoule() >= 1.0,
                () -> "", () -> {
                    Map<String, String> f = fournisseur();
                    long gagne = valeurPortee() - porteAvant;
                    if (gagne != caisseAvant || num(f, "caisse") != 0) {
                        throw new IllegalStateException("Dev +" + gagne + " (caisse " + caisseAvant + ") ; serveur " + f);
                    }
                    return "Dev porte " + porteAvant + " -> " + valeurPortee() + " cuivres (+" + gagne + " = la caisse) ; caisse du serveur " + f.get("caisse");
                });
        pas("Relance : plafond rendu (20), aire rangée, objets de l'étape retirés ; Dev rentre, opérateur (il garde les pièces de la caisse)", () -> {
            airePrete = false;
            fermer();
            reponse = rcon("power cap " + 20) + " ; " + rangerInventaire();
            rcon("kill @e[type=!player,x=" + (o.getX() - 1) + ",y=" + (o.getY() - 1) + ",z=" + (o.getZ() - 1) + ",dx=17,dy=6,dz=11]");
            rcon("fill " + boite() + " air");
            rcon("difficulty " + difficulteAvant);
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("role set " + nom() + " " + roleDevAvant);
            rcon("op " + nom());
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && operateur() && mc.player.blockPosition().distSqr(depart) < 9, () -> "joueur en " + mc.player.blockPosition(),
                () -> "relance de l'electricite finie : " + reponse.replace('\n', '|') + " ; Dev porte " + valeurPortee() + " cuivres");
    }

    // ================================================================================================
    // Pour les relances des étapes de la monnaie et du commerce
    // ================================================================================================

    /** Le début d'une ligne de {@code /eco stats} (le texte de la clé avant son premier argument), dans la langue du client. */
    private static String prefixe(String cle) {
        String motif = Language.getInstance().getOrDefault(cle);
        int i = motif.indexOf('%');
        return (i < 0 ? motif : motif.substring(0, i)).trim();
    }

    /**
     * Le texte de {@code /eco stats} d'avant l'arrêt, avec les lignes que les prélèvements de minuit du démarrage ont
     * changées (coffres-forts, factures publiques dues, refus, caisses des postes, électricité, prélèvements) remplacées
     * par celles d'aujourd'hui — <b>seulement</b> si les chiffres du serveur prouvent qu'ils sont seuls en cause : le
     * coffre-fort d'Abonne a perdu exactement ce que les caisses, les prélèvements et l'électricité ont gagné, et la
     * masse des coffres-forts autant ; le nombre de refus n'a fait que croître. Sinon (ou sans premier passage de cette
     * étape), le texte tel quel : la comparaison échoue et le dit. L'étape de l'électricité contrôle ensuite ces lignes
     * exactement.
     */
    static String statsCorrigees(String statsAvant) {
        Map<String, String> lu;
        try {
            lu = lireFichier();
        } catch (RuntimeException e) {
            return statsAvant;
        }
        try {
            Map<String, String> r = lire(E2eReseau.rcon("e2eelec registre"));
            long coffreA = Long.parseLong(lire(E2eReseau.rcon("e2etresor coffre " + ABONNE)).get("total"));
            long paye = Long.parseLong(lu.get("coffreAbonne")) - coffreA;
            boolean prouve = paye >= 0 && Long.parseLong(lu.get("r_coffres")) - Long.parseLong(r.get("coffres")) == paye
                    && Long.parseLong(r.get("public")) - Long.parseLong(lu.get("r_public")) == paye
                    && Long.parseLong(r.get("paye")) - Long.parseLong(lu.get("r_paye")) == paye
                    && Long.parseLong(r.get("caisses")) - Long.parseLong(lu.get("r_caisses")) == paye
                    && Long.parseLong(r.get("refus")) >= Long.parseLong(lu.get("r_refus"));
            if (!prouve) {
                LOG.warn("[e2e] stats d'avant l'arret non corrigees : registre {} ; avant {} ; coffre d'Abonne {}", r, lu, coffreA);
                return statsAvant;
            }
            List<String> prefixes = new ArrayList<>(List.of(prefixe("command.oneforallsurvival.eco.stats.vaults"),
                    prefixe("command.oneforallsurvival.eco.stats.public_due"), prefixe("command.oneforallsurvival.eco.stats.refused_debits"),
                    prefixe("command.oneforallsurvival.eco.stats.tills"), prefixe("command.oneforallsurvival.eco.stats.electricity")));
            prefixes.add((Language.getInstance().getOrDefault(MoneyTransfer.PUBLIC_BILL.langKey()) + ":").trim());
            String maintenant = E2eReseau.rcon("eco stats").trim().replace('\n', '|');
            Map<String, String> actuelles = new LinkedHashMap<>();
            for (String ligne : maintenant.split("\\|")) {
                for (String p : prefixes) {
                    if (ligne.trim().startsWith(p) && !actuelles.containsKey(p)) {
                        actuelles.put(p, ligne);
                    }
                }
            }
            List<String> out = new ArrayList<>();
            for (String ligne : statsAvant.split("\\|", -1)) {
                String remplace = ligne;
                for (String p : prefixes) {
                    if (ligne.trim().startsWith(p) && actuelles.containsKey(p)) {
                        remplace = actuelles.get(p);
                        break;
                    }
                }
                out.add(remplace);
            }
            VU.putIfAbsent("payeDemarrage", paye);
            LOG.info("[e2e] stats d'avant l'arret corrigees des prelevements du demarrage ({} cuivres preleves chez Abonne)", paye);
            return String.join("|", out);
        } catch (RuntimeException e) {
            LOG.warn("[e2e] stats d'avant l'arret non corrigees", e);
            return statsAvant;
        }
    }

    /**
     * La première ligne de {@code /eco ledger} d'avant l'arrêt, ou celle d'aujourd'hui si toutes les lignes ajoutées au
     * journal depuis l'arrêt sont des prélèvements payés ou refusés (et rien d'autre).
     */
    static String journalCorrige(String ledgerAvant) {
        try {
            Map<String, String> lu = lireFichier();
            Map<String, String> j = lire(E2eReseau.rcon("e2eelec journal " + lu.get("arret")));
            long nouvelles = Long.parseLong(j.get("nouvelles"));
            boolean seuls = true;
            for (String g : liste(j.get("genres"))) {
                String genre = g.substring(0, g.lastIndexOf(':'));
                seuls &= genre.equals(MoneyTransfer.PUBLIC_BILL.kind()) || genre.equals("debit.refused");
            }
            if (nouvelles > 0 && seuls && Long.parseLong(j.get("lignes")) - nouvelles == Long.parseLong(lu.get("r_lignes"))) {
                return E2eReseau.rcon("eco ledger").split("\n")[0].trim();
            }
            LOG.warn("[e2e] registre d'avant l'arret non corrige : {} ; avant {}", j, lu.get("r_lignes"));
        } catch (RuntimeException e) {
            LOG.warn("[e2e] registre d'avant l'arret non corrige", e);
        }
        return ledgerAvant;
    }

    // ================================================================================================
    // Arrêt
    // ================================================================================================

    /** Avant le {@code /stop}, même après un échec : joueur opérateur, règles du monde rendues, joueurs simulés sortis. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null || o == null) {
            return;
        }
        mc = minecraft;
        try {
            rcon("op " + nom());
            rcon("e2eelec charge stop");
            rcon("e2eelec quitter " + ABONNE);
            rcon("e2eelec quitter " + FAUCHE);
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("difficulty " + difficulteAvant);
        } catch (RuntimeException e) {
            LOG.error("[e2e] rcon impossible avant l'arret", e);
        }
    }
}
