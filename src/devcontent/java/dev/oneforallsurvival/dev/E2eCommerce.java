package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.Receipt;
import dev.oneforallsurvival.economy.ReceiptItem;
import dev.oneforallsurvival.economy.client.InvoiceFormScreen;
import dev.oneforallsurvival.economy.client.InvoiceScreen;
import dev.oneforallsurvival.economy.client.StallCashScreen;
import dev.oneforallsurvival.economy.client.StallLedgerCache;
import dev.oneforallsurvival.economy.client.StallOwnerScreen;
import dev.oneforallsurvival.economy.client.StallShopScreen;
import dev.oneforallsurvival.economy.client.TradeScreen;
import dev.oneforallsurvival.economy.invoice.InvoiceItem;
import dev.oneforallsurvival.economy.invoice.InvoicePayPacket;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallActionPacket;
import dev.oneforallsurvival.economy.stall.StallCashMenu;
import dev.oneforallsurvival.economy.stall.StallOffer;
import dev.oneforallsurvival.economy.stall.StallOwnerMenu;
import dev.oneforallsurvival.economy.stall.StallShopMenu;
import dev.oneforallsurvival.economy.trade.TradeMenu;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Étape « commerce » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot E2 vu par un vrai client
 * contre un serveur dédié avec le pack complet. Elle suit l'étape de la monnaie (lot E1), dont elle reprend le monde :
 * le joueur {@code Dev} ne porte rien, le second joueur {@code Autre} porte ses 500 cuivres de départ.
 *
 * <p>Le joueur n'est <b>pas opérateur</b> pendant la séquence. Tout ce qui change le monde passe par la console RCON ;
 * ses gestes sont de vrais clics (lien du message, boutons de la fenêtre d'échange, vitrine, gestion de l'étal et ses
 * champs, table de fabrication, carnet utilisé sur l'autre joueur, formulaire et écran de la facture, caisse). Le
 * second joueur n'est pas un vrai client (un seul client peut tourner à la fois) : une connexion simulée côté serveur
 * ({@link E2eCommerceServeur}), dont les gestes passent par les mêmes chemins que les paquets d'un client.
 *
 * <p>Comptes attendus (cuivres) : Dev reçoit 900 de l'équipe ; échange 200 (Dev vers Autre) ; achat 30 (monnaie
 * rendue 70) ; rachat 16 ; facture de Dev payée par Autre 150 ; facture d'Autre payée par Dev 120, en attente pour
 * Autre ; Dev range le reste (716) dans la caisse de son étal. La caisse de l'étal d'Autre : 100 + 30 − 16 = 114.
 */
final class E2eCommerce {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-commerce.txt";
    private static final double DELAI = 25;
    private static final int Y_DALLE = 150;
    private static final String AUTRE = "Autre";

    // Attendus (cuivres).
    private static final long DONNE_PAR_L_EQUIPE = 900;
    private static final long ECHANGE = 200;
    private static final long ACHAT = 30;
    private static final long RACHAT = 16;
    private static final long FACTURE_DE_DEV = 150;
    private static final long FACTURE_D_AUTRE = 120;
    private static final long DEPOT_D_AUTRE = 100;
    private static final long AUTRE_DEPART = 500;
    private static final long DEV_FINAL = DONNE_PAR_L_EQUIPE - ECHANGE - ACHAT + RACHAT + FACTURE_DE_DEV - FACTURE_D_AUTRE; // 716
    private static final long AUTRE_PORTE_FIN = AUTRE_DEPART + ECHANGE - DEPOT_D_AUTRE - FACTURE_DE_DEV; // 450
    private static final long CAISSE_AUTRE = DEPOT_D_AUTRE + ACHAT - RACHAT; // 114

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static final ArrayDeque<Runnable> CLICS = new ArrayDeque<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;

    private static Minecraft mc;
    private static BlockPos depart;
    private static BlockPos o;
    private static boolean airePrete;

    // Mémoire d'un pas à l'autre.
    private static String reponse = "";
    private static String reponse2 = "";
    private static int origine;
    private static long circulationDebut;
    private static long valeurAvant;
    private static int painAvant;
    private static int pommeAvant;
    private static int recusAvant;
    private static int painEnStock;
    private static Style lienAccepter;
    private static final List<String> CHAT = new ArrayList<>();
    private static UUID factureId;
    private static int factureCase;
    private static String etalAutreLu = "";
    private static String etalDevLu = "";
    private static String facturesLues = "";
    private static String statsAvantArret = "";
    private static long autrePorte = -1;

    private E2eCommerce() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape du commerce";
    }

    /** Les messages reçus par le client (appelé par {@link E2eClient#onChat}) : le lien « [Accepter] » est gardé. */
    static void recevoir(Component message) {
        CHAT.add(message.getString());
        message.visit((style, texte) -> {
            ClickEvent clic = style.getClickEvent();
            if (clic != null && clic.getAction() == ClickEvent.Action.RUN_COMMAND && clic.getValue().startsWith("/trade accept ")) {
                lienAccepter = style;
            }
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static boolean chatContient(String texte) {
        return CHAT.stream().anyMatch(l -> l.contains(texte));
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

    /** Où se tient Autre pour l'échange et les factures ; Dev se tient deux blocs au sud. */
    private static BlockPos placeAutre() {
        return p(10, 0, 4);
    }

    private static BlockPos placeDev() {
        return p(10, 0, 6);
    }

    private static BlockPos loin() {
        return p(10, 0, 20);
    }

    private static BlockPos etalAutre() {
        return p(20, 0, 0);
    }

    private static BlockPos etalDev() {
        return p(30, 0, 0);
    }

    // ================================================================================================
    // Déroulement
    // ================================================================================================

    /** Avance d'un pas ; vrai quand la séquence est finie. Le premier échec arrête l'essai. */
    static boolean avancer(Minecraft minecraft) {
        mc = minecraft;
        if (index >= PAS.size() && CLICS.isEmpty()) {
            return true;
        }
        if (airePrete && (mc.player.isDeadOrDying() || mc.player.getY() < o.getY() - 3)) {
            E2eClient.fail("le joueur est mort ou tombe dans le vide : vie " + mc.player.getHealth() + ", position " + mc.player.position()
                    + ", pas " + legende());
            return false;
        }
        if (!CLICS.isEmpty()) {
            try {
                CLICS.poll().run();
            } catch (RuntimeException e) {
                CLICS.clear();
                E2eClient.fail("clic impossible (" + legende() + ") : " + e.getMessage());
            }
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

    private static Path fichier() {
        return mc.gameDirectory.toPath().resolve(FICHIER);
    }

    private static void placerJoueur(BlockPos bloc, float yaw, float pitch) {
        rcon("tp " + nom() + " " + (bloc.getX() + 0.5) + " " + bloc.getY() + " " + (bloc.getZ() + 0.5) + " " + yaw + " " + pitch);
    }

    private static boolean pres(BlockPos bloc) {
        return Math.abs(mc.player.getX() - (bloc.getX() + 0.5)) < 1.0 && Math.abs(mc.player.getZ() - (bloc.getZ() + 0.5)) < 1.0
                && Math.abs(mc.player.getY() - bloc.getY()) < 2.5;
    }

    /** Le joueur devant un bloc : deux blocs au sud, regard vers le nord. */
    private static void devant(BlockPos bloc) {
        placerJoueur(bloc.south(2), 180, 30);
    }

    private static boolean devantPret(BlockPos bloc) {
        return pres(bloc.south(2));
    }

    private static String fmt(long cuivres) {
        return CoinMath.format(cuivres).getString();
    }

    private static long valeurLibre() {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s);
        }
        return n + CoinMath.valueOf(mc.player.getOffhandItem());
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

    private static int compter(Item item) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int casesLibres() {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.isEmpty()) {
                n++;
            }
        }
        return n;
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

    /**
     * Une main vraiment vide : une case libre de la barre d'action, sinon la case 8 vidée dans une case libre du sac (les
     * étapes des lots précédents remplissent la barre). Le geste de l'échange exige une main vide.
     */
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

    private static void clicMenu(int caseMenu, int bouton, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, caseMenu, bouton, type, mc.player);
    }

    /** La case du menu ouvert qui est dans l'inventaire du joueur et porte l'objet voulu ; -1 sinon. */
    private static int caseJoueur(Predicate<ItemStack> voulu) {
        for (Slot s : mc.player.containerMenu.slots) {
            if (s.container == mc.player.getInventory() && s.getContainerSlot() < 36 && voulu.test(s.getItem())) {
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

    private static void fermer() {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        mc.setScreen(null);
    }

    /** Un vrai clic de souris sur l'écran ouvert, en coordonnées d'écran. */
    private static void clicEcran(double x, double y) {
        // Le nom est lu avant le clic : un bouton comme « Émettre » ferme l'écran.
        String ecran = mc.screen.getClass().getSimpleName();
        boolean pris = mc.screen.mouseClicked(x, y, 0);
        LOG.info("[e2e] clic en ({}, {}) sur {} : {}", x, y, ecran, pris);
    }

    private static void clicEchange(String id) {
        TradeScreen s = (TradeScreen) mc.screen;
        int[] c = s.elementCenter(id).orElseThrow(() -> new IllegalStateException("aucun element " + id));
        clicEcran(c[0], c[1]);
    }

    private static void taper(String texte) {
        for (char c : texte.toCharArray()) {
            mc.screen.charTyped(c, 0);
        }
    }

    private static int champ(Object ecran, String nom) {
        try {
            java.lang.reflect.Field f = ecran.getClass().getDeclaredField(nom);
            f.setAccessible(true);
            return f.getInt(ecran);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("champ " + nom + " illisible : " + e);
        }
    }

    private static final Pattern CLE_VALEUR = Pattern.compile("(\\w+)=(\\[[^\\]]*\\]|[^ ]+)");

    private static Map<String, String> lire(String texte) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = CLE_VALEUR.matcher(texte);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static long comptes(String cle) {
        String r = rcon("e2ecommerce comptes");
        String v = lire(r).get(cle);
        if (v == null) {
            throw new IllegalStateException("comptes illisibles : " + r);
        }
        return Long.parseLong(v);
    }

    private static TradeMenu menuEchange() {
        return ((TradeScreen) mc.screen).getMenu();
    }

    private static boolean echangeOuvert() {
        return mc.screen instanceof TradeScreen;
    }

    /** Le nombre de cet objet dans une moitié de la fenêtre d'échange (la sienne ou celle de l'autre). */
    private static int dansEchange(boolean sienne, Item objet) {
        int debutCases = sienne ? TradeMenu.OWN_START : TradeMenu.OTHER_START;
        int n = 0;
        for (int s = debutCases; s < debutCases + TradeMenu.OFFER_SIZE; s++) {
            ItemStack st = menuEchange().getSlot(s).getItem();
            if (st.is(objet)) {
                n += st.getCount();
            }
        }
        return n;
    }

    private static Player autreVu() {
        for (Player joueur : mc.level.players()) {
            if (AUTRE.equals(joueur.getGameProfile().getName())) {
                return joueur;
            }
        }
        return null;
    }

    private static MarketStallBlockEntity etalClient(BlockPos pos) {
        return mc.level.getBlockEntity(pos) instanceof MarketStallBlockEntity be ? be : null;
    }

    private static Map<String, String> statsEnLignes(String stats) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String ligne : stats.split("\\|")) {
            int i = ligne.indexOf(':');
            if (i > 0) {
                out.put(ligne.substring(0, i).trim(), ligne.substring(i + 1).trim());
            }
        }
        return out;
    }

    private static String nomGenre(MoneyTransfer genre) {
        return Component.translatable(genre.langKey()).getString();
    }

    // ================================================================================================
    // Premier passage
    // ================================================================================================

    static void premierePhase() {
        preparation();
        echange();
        echangeAbandonne();
        etalDAutre();
        acheteurChezLAbsent();
        etalDeDev();
        factures();
        caisseDeDev();
        comptesFinaux();
        fin();
    }

    private static void preparation() {
        pas("Le joueur, en spectateur, se rend au centre de l'aire du commerce (les chunks se chargent)", () -> {
            fermer();
            depart = mc.player.blockPosition();
            BlockPos spawn = mc.level.getSharedSpawnPos();
            int x = depart.getX() + 100;
            int z = depart.getZ() + 130;
            if (Math.abs(x - spawn.getX()) <= 40 && Math.abs(z - spawn.getZ()) <= 40) {
                x = depart.getX() - 180;
            }
            o = new BlockPos(x, Y_DALLE, z);
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 18.5) + " " + (o.getY() + 5) + " " + (o.getZ() + 6.5));
        }, () -> ecoule() >= 4 && mc.level.getChunkSource().hasChunk((o.getX() + 18) >> 4, (o.getZ() + 6) >> 4)
                && mc.level.getChunkSource().hasChunk((o.getX() + 40) >> 4, (o.getZ() + 24) >> 4)
                && mc.level.getChunkSource().hasChunk((o.getX() - 4) >> 4, (o.getZ() - 8) >> 4),
                () -> "chunks de l'aire non recus, joueur en " + mc.player.blockPosition(), null);
        pas("Aire du commerce sur une dalle, joueur sans droits (console RCON : deop)", () -> {
            rcon("difficulty peaceful");
            rcon("gamerule doMobSpawning false");
            rcon("fill " + xyz(p(-4, 0, -8)) + " " + xyz(p(40, 8, 24)) + " air");
            rcon("fill " + xyz(p(-4, -1, -8)) + " " + xyz(p(40, -1, 24)) + " stone");
            rcon("effect clear " + nom());
            placerJoueur(p(2, 0, 2), 180, 0);
            rcon("gamemode survival " + nom());
            rcon("deop " + nom());
        }, () -> mc.level.getBlockState(o.below()).is(Blocks.STONE) && !operateur() && mc.player.getHealth() > 0 && pres(p(2, 0, 2))
                && ecoule() >= 2,
                () -> "operateur " + operateur() + ", joueur en " + mc.player.blockPosition() + ", dalle " + mc.level.getBlockState(o.below()),
                () -> {
                    airePrete = true;
                    return "commerce : dalle en " + o.toShortString() + " (joueur parti de " + depart.toShortString()
                            + "), deop par RCON : hasPermissions(2) cote client = " + operateur() + ", gamemode " + mc.gameMode.getPlayerMode();
                });
        pas("Départ : Dev ne porte rien, aucun transfert n'a encore eu lieu dans ce monde", () -> {
            reponse = rcon("e2ecommerce comptes");
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            Map<String, String> r = lire(reponse);
            if (valeurTotale() != 0) {
                throw new IllegalStateException("Dev porte deja " + valeurTotale() + " cuivres");
            }
            for (MoneyTransfer genre : MoneyTransfer.values()) {
                if (!"0".equals(r.get(genre.name().toLowerCase(java.util.Locale.ROOT)))) {
                    throw new IllegalStateException("monde deja servi au commerce : " + reponse);
                }
            }
            if (!"0".equals(r.get("attente")) || !"0".equals(r.get("tresor"))) {
                throw new IllegalStateException("attente ou tresor non nuls : " + reponse);
            }
            if (casesLibres() < 10) {
                throw new IllegalStateException("inventaire de Dev trop plein pour l'essai : " + casesLibres() + " cases libres");
            }
            return "depart du commerce : Dev ne porte rien, aucun transfert, tresor et attente a 0 (" + reponse.replace(" FIN_E2E", "").trim()
                    + "), " + casesLibres() + " cases libres";
        });
        pas("L'équipe donne une bourse vide puis 900 cuivres à Dev (/eco give : ils entrent dans la bourse)", () -> {
            rcon("give " + nom() + " oneforallsurvival:purse 1");
        }, () -> !bourse().isEmpty() && ecoule() >= 1.0, () -> "bourse absente", null);
        pas("/eco give 900", () -> {
            circulationDebut = comptes("circulation");
            reponse = rcon("eco give " + nom() + " " + DONNE_PAR_L_EQUIPE + " essai du commerce");
        }, () -> Purse.total(bourse()) == DONNE_PAR_L_EQUIPE && valeurTotale() == DONNE_PAR_L_EQUIPE && ecoule() >= 1.0,
                () -> "bourse " + Purse.total(bourse()) + ", valeur " + valeurTotale() + ", reponse " + reponse,
                () -> {
                    long apres = comptes("circulation");
                    if (apres != circulationDebut + DONNE_PAR_L_EQUIPE) {
                        throw new IllegalStateException("circulation " + circulationDebut + " -> " + apres);
                    }
                    circulationDebut = apres;
                    return "/eco give : " + reponse.trim() + " ; la bourse de Dev porte " + Purse.total(bourse()) + " cuivres ("
                            + Purse.count(bourse(), Coin.GOLD) + " or) ; en circulation au depart de l'etape : " + circulationDebut;
                });
    }

    private static ItemStack bourse() {
        for (ItemStack s : mc.player.getInventory().items) {
            if (Purse.isPurse(s)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------------------------------------- échange

    private static void echange() {
        pas("Autre se connecte (connexion simulée) deux blocs au nord de Dev ; il reçoit 8 pains et une pomme", () -> {
            placerJoueur(placeDev(), 180, 10);
            reponse = rcon("e2ecommerce autre entrer " + xyz(placeAutre()));
            reponse2 = rcon("e2ecommerce autre donner minecraft:bread 8") + rcon("e2ecommerce autre donner minecraft:apple 1");
        }, () -> pres(placeDev()) && autreVu() != null && ecoule() >= 2, () -> "joueur en " + mc.player.blockPosition() + ", autre vu "
                + (autreVu() != null) + ", reponse " + reponse, () -> {
                    Map<String, String> r = lire(reponse);
                    if (!("" + AUTRE_DEPART).equals(r.get("porte")) || !"false".equals(r.get("operateur")) || !reponse2.contains("pris=true")) {
                        throw new IllegalStateException(reponse + " / " + reponse2);
                    }
                    return "Autre connecte (simule, non operateur), il porte " + r.get("porte") + " cuivres (sa somme de depart du lot E1) ; Dev le voit a "
                            + String.format(java.util.Locale.ROOT, "%.1f", autreVu().distanceTo(mc.player)) + " bloc(s)";
                });
        pas("Autre propose un échange à Dev (/trade Dev, en son nom)", () -> {
            lienAccepter = null;
            CHAT.clear();
            reponse = rcon("e2ecommerce autre commande trade " + nom());
        }, () -> lienAccepter != null && ecoule() >= 0.5, () -> "aucun lien [Accepter] recu ; chat " + CHAT + ", reponse " + reponse,
                () -> "le message de proposition arrive chez Dev avec un lien cliquable : " + lienAccepter.getClickEvent().getValue());
        pas("Dev clique sur le lien [Accepter] du message : la fenêtre d'échange s'ouvre", () -> {
            painAvant = compter(Items.BREAD);
            pommeAvant = compter(Items.APPLE);
            recusAvant = compter(EconomyRegistry.RECEIPT.get());
            ChatScreen chat = new ChatScreen("");
            mc.setScreen(chat);
            boolean pris = chat.handleComponentClicked(lienAccepter);
            LOG.info("[e2e] clic sur le lien du chat : {}", pris);
            if (mc.screen == chat) {
                mc.setScreen(null);
            }
        }, () -> echangeOuvert() && ecoule() >= 0.5, () -> "ecran " + mc.screen, () -> {
            reponse = rcon("e2ecommerce autre etat");
            if (!reponse.contains("menu=TradeMenu")) {
                throw new IllegalStateException("Autre n'a pas la fenetre : " + reponse);
            }
            return "Dev accepte par le lien du message (/trade accept " + AUTRE + " envoye par le clic) : fenetre d'echange ouverte chez les deux ("
                    + mc.screen.getTitle().getString() + ")";
        });
        pas("Autre pose 5 pains dans sa moitié", () -> {
            reponse = rcon("e2ecommerce echange poser minecraft:bread 5");
        }, () -> echangeOuvert() && dansEchange(false, Items.BREAD) == 5 && ecoule() >= 0.5,
                () -> "pains vus chez l'autre " + (echangeOuvert() ? dansEchange(false, Items.BREAD) : -1) + ", reponse " + reponse,
                () -> "Autre pose 5 pains : Dev les voit dans la moitie de l'autre (" + reponse.replace(" FIN_E2E", "").trim() + ")");
        pas("Dev clique « +1 » de l'or : une pièce prise dans sa bourse", () -> clicEchange("coins.gold.1"),
                () -> echangeOuvert() && dansEchange(true, Coin.GOLD.item()) == 1 && Purse.total(bourse()) == 800 && ecoule() >= 0.5,
                () -> "or pose " + dansEchange(true, Coin.GOLD.item()) + ", bourse " + Purse.total(bourse()), null);
        pas("Dev clique encore « +1 » de l'or", () -> clicEchange("coins.gold.1"),
                () -> echangeOuvert() && dansEchange(true, Coin.GOLD.item()) == 2 && Purse.total(bourse()) == 700 && ecoule() >= 0.5,
                () -> "or pose " + dansEchange(true, Coin.GOLD.item()) + ", bourse " + Purse.total(bourse()),
                () -> "Dev verse 2 or par le bouton « +1 » de la fenetre : sa bourse passe de 900 a " + Purse.total(bourse())
                        + " cuivres, 2 pieces d'or dans sa moitie, aucune piece libre (" + valeurLibre() + ")");
        pas("Dev coche « Reçu »", () -> clicEchange("receipt"), () -> echangeOuvert() && menuEchange().receipt() && ecoule() >= 0.5,
                () -> "recu " + menuEchange().receipt(), () -> "case Recu cochee (vue par le client : version " + menuEchange().version() + ")");
        pas("Dev confirme", () -> clicEchange("confirm"), () -> echangeOuvert() && menuEchange().selfConfirmed() && ecoule() >= 0.5,
                () -> "confirme " + menuEchange().selfConfirmed(), () -> {
                    valeurAvant = menuEchange().version();
                    return "Dev confirme : l'ecran montre sa confirmation (version " + menuEchange().version() + "), l'autre en attente ("
                            + menuEchange().otherConfirmed() + ")";
                });
        pas("Autre change une case (il pose sa pomme) : la confirmation de Dev doit tomber", () -> {
            reponse = rcon("e2ecommerce echange poser minecraft:apple 1");
        }, () -> echangeOuvert() && dansEchange(false, Items.APPLE) == 1 && !menuEchange().selfConfirmed() && ecoule() >= 0.5,
                () -> "pomme vue " + dansEchange(false, Items.APPLE) + ", confirme " + menuEchange().selfConfirmed() + ", reponse " + reponse,
                () -> "Autre change une case (une pomme) : la confirmation de Dev tombe, lue a l'ecran (selfConfirmed=" + menuEchange().selfConfirmed()
                        + ", version " + valeurAvant + " -> " + menuEchange().version() + ")");
        pas("Dev confirme l'état final", () -> clicEchange("confirm"), () -> echangeOuvert() && menuEchange().selfConfirmed() && ecoule() >= 0.5,
                () -> "confirme " + menuEchange().selfConfirmed(), null);
        pas("Autre confirme : l'échange a lieu", () -> {
            reponse = rcon("e2ecommerce echange confirmer");
        }, () -> mc.screen == null && compter(Items.BREAD) == painAvant + 5 && compter(Items.APPLE) == pommeAvant + 1
                && compter(EconomyRegistry.RECEIPT.get()) == recusAvant + 1 && ecoule() >= 1.0,
                () -> "ecran " + mc.screen + ", pain " + compter(Items.BREAD) + " (avant " + painAvant + "), pomme " + compter(Items.APPLE)
                        + ", recus " + compter(EconomyRegistry.RECEIPT.get()) + ", reponse " + reponse,
                () -> {
                    Map<String, String> r = lire(reponse);
                    if (!"DONE".equals(r.get("resultat")) || !("" + (AUTRE_DEPART + ECHANGE)).equals(r.get("porte"))
                            || !r.get("objets").contains("receipt:1") || !r.get("objets").contains("bread:3") || r.get("objets").contains("apple")) {
                        throw new IllegalStateException("cote d'Autre : " + reponse);
                    }
                    ItemStack recu = recuDeDev();
                    if (valeurTotale() != DONNE_PAR_L_EQUIPE - ECHANGE || !"trade".equals(Receipt.kind(recu))) {
                        throw new IllegalStateException("Dev porte " + valeurTotale() + ", recu " + Receipt.kind(recu));
                    }
                    return "echange fait apres deux confirmations : Dev recoit 5 pains, 1 pomme et un recu (genre " + Receipt.kind(recu)
                            + "), il porte " + valeurTotale() + " cuivres ; Autre : " + r.get("porte") + " cuivres, objets " + r.get("objets");
                });
        pas("/eco ledger : le transfert de l'échange", () -> {
            reponse = rcon("eco ledger");
        }, () -> ecoule() >= 0.3, () -> "", () -> {
            String trade = nomGenre(MoneyTransfer.TRADE);
            String ligne = null;
            for (String l : reponse.split("\n")) {
                if (l.contains(trade) && l.contains(nom() + " -> " + AUTRE)) {
                    ligne = l.trim();
                }
            }
            if (ligne == null || !ligne.contains(fmt(ECHANGE)) || comptes("trade") != ECHANGE) {
                throw new IllegalStateException(reponse.replace('\n', '|'));
            }
            return "/eco ledger montre le transfert : " + ligne + " (total des echanges " + comptes("trade") + " cuivres)";
        });
    }

    private static ItemStack recuDeDev() {
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.getItem() instanceof ReceiptItem) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void echangeAbandonne() {
        pas("Second échange : Dev, accroupi et la main vide, fait un clic droit sur Autre (le geste qui propose)", () -> {
            CHAT.clear();
            tenirVide();
            mc.options.keyShift.setDown(true);
        }, () -> {
            mc.options.keyShift.setDown(true);
            return mc.player.isShiftKeyDown() && mc.player.getMainHandItem().isEmpty() && ecoule() >= 1.0;
        }, () -> "accroupi " + mc.player.isShiftKeyDown() + ", main " + mc.player.getMainHandItem(), null);
        pas("Clic droit sur Autre", () -> {
            mc.options.keyShift.setDown(true);
            Player cible = autreVu();
            if (cible == null) {
                throw new IllegalStateException("Autre n'est pas vu par le client");
            }
            mc.gameMode.interactAt(mc.player, cible, new EntityHitResult(cible), InteractionHand.MAIN_HAND);
            var r = mc.gameMode.interact(mc.player, cible, InteractionHand.MAIN_HAND);
            LOG.info("[e2e] clic droit accroupi sur Autre : {}", r);
        }, () -> chatContient("Trade offered to " + AUTRE) && ecoule() >= 0.5, () -> "chat " + CHAT, () -> {
            mc.options.keyShift.setDown(false);
            return "le geste (accroupi, main vide, clic droit sur le joueur) propose l'echange : « Trade offered to " + AUTRE + " »";
        });
        pas("Autre accepte (/trade accept Dev, en son nom)", () -> {
            mc.options.keyShift.setDown(false);
            reponse = rcon("e2ecommerce autre commande trade accept " + nom());
        }, () -> echangeOuvert() && ecoule() >= 0.5, () -> "ecran " + mc.screen + ", reponse " + reponse, null);
        pas("Dev verse 1 or", () -> clicEchange("coins.gold.1"),
                () -> echangeOuvert() && dansEchange(true, Coin.GOLD.item()) == 1 && valeurTotale() == DONNE_PAR_L_EQUIPE - ECHANGE - 100
                        && ecoule() >= 0.5,
                () -> "or pose " + dansEchange(true, Coin.GOLD.item()) + ", valeur " + valeurTotale(), null);
        pas("Autre pose ses 3 pains", () -> {
            reponse = rcon("e2ecommerce echange poser minecraft:bread 3");
        }, () -> echangeOuvert() && dansEchange(false, Items.BREAD) == 3 && ecoule() >= 0.5,
                () -> "pains vus " + dansEchange(false, Items.BREAD) + ", reponse " + reponse, null);
        pas("Dev s'éloigne (téléporté à 14 blocs) : l'échange est abandonné, tout revient", () -> {
            CHAT.clear();
            placerJoueur(loin(), 180, 10);
        }, () -> mc.screen == null && valeurTotale() == DONNE_PAR_L_EQUIPE - ECHANGE && chatContient("too far apart") && ecoule() >= 1.0,
                () -> "ecran " + mc.screen + ", valeur " + valeurTotale() + ", chat " + CHAT, () -> {
                    reponse = rcon("e2ecommerce autre etat");
                    Map<String, String> r = lire(reponse);
                    if (!r.get("objets").contains("bread:3") || !("" + (AUTRE_DEPART + ECHANGE)).equals(r.get("porte"))
                            || !"InventoryMenu".equals(r.get("menu")) || valeurLibre() != 0) {
                        throw new IllegalStateException(reponse + " ; Dev libre " + valeurLibre());
                    }
                    return "second echange abandonne par l'eloignement de Dev (« too far apart ») : la fenetre se ferme, Dev retrouve sa piece d'or "
                            + "(bourse " + Purse.total(bourse()) + " cuivres), Autre ses 3 pains (" + r.get("objets") + ", " + r.get("porte")
                            + " cuivres) ; rien au sol";
                });
    }

    // ------------------------------------------------------------------------------------------ étal d'Autre

    private static void etalDAutre() {
        pas("Autre pose un étal (clic droit avec l'objet sur la dalle)", () -> {
            placerJoueur(etalAutre().south(4), 180, 20);
            reponse2 = rcon("e2ecommerce autre donner minecraft:bread 12") + rcon("e2ecommerce autre donner minecraft:wheat 1");
            reponse = rcon("e2ecommerce etal poser " + xyz(etalAutre()));
        }, () -> etalClient(etalAutre()) != null && ecoule() >= 1.0,
                () -> "bloc vu " + mc.level.getBlockState(etalAutre()) + ", reponse " + reponse, () -> {
                    if (!reponse.contains("pose=true")) {
                        throw new IllegalStateException(reponse);
                    }
                    return "Autre pose son etal en " + etalAutre().toShortString() + " : proprietaire " + etalClient(etalAutre()).ownerName()
                            + " (vu par le client)";
                });
        pas("Autre garnit l'étal : offre 4 pains pour 30 cuivres, rachat 8 blés pour 16 cuivres, pain au stock, 100 cuivres en caisse", () -> {
            reponse = rcon("e2ecommerce etal garnir " + xyz(etalAutre()));
        }, () -> {
            MarketStallBlockEntity be = etalClient(etalAutre());
            return be != null && be.offers().get(0) != null && be.buybacks().get(0) != null && ecoule() >= 1.0;
        }, () -> "reponse " + reponse, () -> {
            Map<String, String> r = lire(reponse);
            if (!reponse.contains("offre:DONE") || !reponse.contains("rachat:DONE") || !"[bread:15]".equals(r.get("stock"))
                    || !("" + DEPOT_D_AUTRE).equals(r.get("caisse")) || !r.get("offres").contains("0:breadx4@30")
                    || !r.get("rachats").contains("0:wheatx8@16,reste5")) {
                throw new IllegalStateException(reponse);
            }
            StallOffer vue = etalClient(etalAutre()).offers().get(0);
            return "etal d'Autre garni par ses gestes : " + reponse.replace(" FIN_E2E", "").trim() + " ; le client voit l'offre " + vue;
        });
        pas("L'équipe met un entonnoir sous l'étal (console)", () -> {
            reponse = rcon("setblock " + xyz(etalAutre().below()) + " minecraft:hopper");
        }, () -> mc.level.getBlockState(etalAutre().below()).is(Blocks.HOPPER) && ecoule() >= 0.5, () -> "bloc " + mc.level.getBlockState(etalAutre().below()), null);
        pas("Autre se déconnecte : l'étal reste, vendeur absent", () -> {
            reponse = rcon("e2ecommerce autre quitter");
        }, () -> autreVu() == null && ecoule() >= 1.0, () -> "Autre encore vu, reponse " + reponse, () -> {
            if (!reponse.contains("PARTI")) {
                throw new IllegalStateException(reponse);
            }
            return "Autre se deconnecte (" + reponse.replace(" FIN_E2E", "").trim() + ")";
        });
    }

    // --------------------------------------------------------------------------------------- achat, vendeur absent

    private static int[] origineEcran() {
        AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.screen;
        return new int[] {s.getGuiLeft(), s.getGuiTop()};
    }

    /** Le bouton « Acheter » / « Vendre » de la rangée visible {@code rangee} de la vitrine. */
    private static void clicRangeeVitrine(int rangee) {
        int[] g = origineEcran();
        clicEcran(g[0] + 8 + 154 - 42 - 2 + 21, g[1] + StallShopMenu.LIST_Y + rangee * StallShopMenu.ROW_H + (StallShopMenu.ROW_H - 14) / 2 + 7);
    }

    /** L'onglet {@code t} de la vitrine (0 : à vendre, 1 : rachète). */
    private static void clicOngletVitrine(int t) {
        int[] g = origineEcran();
        int w = (176 - 16 - 2) / 2;
        clicEcran(g[0] + 8 + t * (w + 2) + w / 2, g[1] + 16 + 6);
    }

    private static StallShopMenu vitrine() {
        return ((StallShopScreen) mc.screen).getMenu();
    }

    private static void acheteurChezLAbsent() {
        pas("Dev se met devant l'étal d'Autre", () -> {
            devant(etalAutre());
            valeurAvant = valeurTotale();
            painAvant = compter(Items.BREAD);
        }, () -> devantPret(etalAutre()) && ecoule() >= 1.0, () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Clic droit sur l'étal (main vide) : la vitrine, pas la gestion", () -> {
            tenirVide();
            clic(etalAutre(), Direction.SOUTH);
        }, () -> mc.screen instanceof StallShopScreen && vitrine().saleAvailable(0) == 3 && ecoule() >= 0.5,
                () -> "ecran " + mc.screen, () -> "Dev ouvre la vitrine de l'etal d'Autre absent par un vrai clic droit (" + mc.screen.getClass().getSimpleName()
                        + ", pas la gestion) : l'offre 0 annonce " + vitrine().saleAvailable(0) + " lots");
        pas("Dev clique « Acheter » : 4 pains pour 30 cuivres, monnaie rendue dans la bourse", () -> clicRangeeVitrine(0),
                () -> compter(Items.BREAD) == painAvant + 4 && valeurTotale() == valeurAvant - ACHAT && ecoule() >= 0.5,
                () -> "pain " + compter(Items.BREAD) + " (avant " + painAvant + "), valeur " + valeurTotale() + " (avant " + valeurAvant + ")",
                () -> "achat a l'etal du vendeur absent : +4 pains, Dev passe de " + valeurAvant + " a " + valeurTotale()
                        + " cuivres, monnaie rendue dans la bourse (" + Purse.total(bourse()) + " cuivres, " + valeurLibre()
                        + " en vrac), la vitrine annonce maintenant " + vitrine().saleAvailable(0) + " lots");
        pas("L'équipe donne 8 blés à Dev (console) ; il passe à l'onglet « Rachète »", () -> {
            rcon("give " + nom() + " minecraft:wheat 8");
            clicOngletVitrine(1);
            valeurAvant = valeurTotale();
        }, () -> compter(Items.WHEAT) == 8 && mc.screen instanceof StallShopScreen
                && vitrine().buybackState(0) == StallShopMenu.BUYBACK_OPEN && ecoule() >= 1.0,
                () -> "ble " + compter(Items.WHEAT) + ", etat du rachat " + vitrine().buybackState(0), null);
        pas("Dev clique « Vendre » : 8 blés contre 16 cuivres payés par la caisse", () -> clicRangeeVitrine(0),
                () -> compter(Items.WHEAT) == 0 && valeurTotale() == valeurAvant + RACHAT && ecoule() >= 0.5,
                () -> "ble " + compter(Items.WHEAT) + ", valeur " + valeurTotale(),
                () -> "rachat par l'etal du vendeur absent : 8 bles vendus, Dev passe de " + valeurAvant + " a " + valeurTotale() + " cuivres");
        pas("Dev referme la vitrine ; un client qui envoie de lui-même l'écriture d'une offre (paquet forgé) est refusé", () -> {
            fermer();
            BenchNetwork.CHANNEL.sendToServer(StallActionPacket.setOffer(etalAutre(), 1, 1, 1L));
        }, () -> mc.screen == null && ecoule() >= 1.5, () -> "ecran " + mc.screen, () -> {
            reponse = rcon("e2ecommerce etal lire " + xyz(etalAutre()));
            if (lire(reponse).get("offres").contains("1:") || etalClient(etalAutre()).offers().get(1) != null) {
                throw new IllegalStateException("une offre a ete ecrite : " + reponse);
            }
            return "Dev ne peut pas gerer l'etal d'Autre : le clic droit ouvre la vitrine, et l'ecriture d'une offre envoyee de lui-meme "
                    + "n'a rien ecrit (offres " + lire(reponse).get("offres") + ")";
        });
        pasD(20, "Dev essaie de casser l'étal en survie (3 s de clic gauche tenu)", () -> {
            tenirVide();
            mc.gameMode.startDestroyBlock(etalAutre(), Direction.SOUTH);
        }, () -> {
            mc.gameMode.continueDestroyBlock(etalAutre(), Direction.SOUTH);
            mc.player.swing(InteractionHand.MAIN_HAND);
            return ecoule() >= 3.0;
        }, () -> "", null);
        pas("L'étal est toujours là, client et serveur", () -> {
            mc.gameMode.stopDestroyBlock();
        }, () -> ecoule() >= 1.5, () -> "", () -> {
            String serveur = rcon("execute if block " + xyz(etalAutre()) + " oneforallsurvival:market_stall");
            if (!mc.level.getBlockState(etalAutre()).is(EconomyRegistry.MARKET_STALL.get()) || !serveur.contains("Test passed")) {
                throw new IllegalStateException("client " + mc.level.getBlockState(etalAutre()) + ", serveur " + serveur);
            }
            return "Dev ne casse pas l'etal d'Autre (3 s de clic gauche en survie) : bloc present cote client et serveur (" + serveur.trim() + ")";
        });
        pas("L'entonnoir sous l'étal n'a rien pris ; le stock et la caisse sont ceux attendus", () -> {
            reponse = rcon("e2ecommerce etal lire " + xyz(etalAutre()));
            reponse2 = rcon("data get block " + xyz(etalAutre().below()) + " Items");
        }, () -> ecoule() >= 0.3, () -> "", () -> {
            Map<String, String> r = lire(reponse);
            if (reponse2.contains("bread") || reponse2.contains("wheat") || !"[bread:11,wheat:8]".equals(r.get("stock"))
                    || !("" + CAISSE_AUTRE).equals(r.get("caisse")) || !r.get("rachats").contains("reste4")) {
                throw new IllegalStateException(reponse + " / entonnoir : " + reponse2);
            }
            return "l'entonnoir sous l'etal ne prend rien (" + reponse2.trim() + ") ; etal : stock " + r.get("stock") + ", caisse " + r.get("caisse")
                    + " (100 + 30 - 16), rachats " + r.get("rachats") + ", registre " + r.get("registre");
        });
    }

    // ------------------------------------------------------------------------------------------ étal de Dev

    private static void ongletGestion(int i) {
        int[] g = origineEcran();
        int w = (160 - 3 * 2) / 4;
        clicEcran(g[0] + 8 + i * (w + 2) + w / 2, g[1] + 18 + 7);
    }

    private static StallOwnerMenu gestion() {
        return ((StallOwnerScreen) mc.screen).getMenu();
    }

    private static void etalDeDev() {
        pas("Dev se met devant la table pour fabriquer un étal (matériel donné par la console)", () -> {
            rcon("give " + nom() + " minecraft:white_wool 3");
            rcon("give " + nom() + " minecraft:stick 2");
            rcon("give " + nom() + " minecraft:oak_planks 3");
            rcon("setblock " + xyz(table()) + " minecraft:crafting_table");
            devant(table());
        }, () -> devantPret(table()) && compter(Items.WHITE_WOOL) >= 3 && compter(Items.STICK) >= 2 && compter(Items.OAK_PLANKS) >= 3
                && mc.level.getBlockState(table()).is(Blocks.CRAFTING_TABLE) && ecoule() >= 1.5, () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Clic droit sur la table", () -> {
            tenirVide();
            clic(table(), Direction.SOUTH);
        }, () -> mc.screen instanceof CraftingScreen, () -> "ecran " + mc.screen, null);
        pas("Il dispose la recette de l'étal (3 laines, 2 bâtons, 3 planches) et prend le résultat", () -> {
            poserUnParCase(Items.WHITE_WOOL, 1, 2, 3);
            poserUnParCase(Items.STICK, 4, 6);
            poserUnParCase(Items.OAK_PLANKS, 7, 8, 9);
            CLICS.add(attendreSortie(40));
            CLICS.add(() -> clicMenu(0, 0, ClickType.QUICK_MOVE));
        }, () -> compter(EconomyRegistry.MARKET_STALL_ITEM.get()) == 1 && ecoule() >= 1.0, () -> "pas d'etal dans l'inventaire",
                () -> "etal fabrique a la table avec sa recette (laine, batons, planches)");
        pas("Il referme la table et se met devant l'emplacement de son étal", () -> {
            fermer();
            placerJoueur(etalDev().south(2), 180, 40);
        }, () -> mc.screen == null && pres(etalDev().south(2)) && ecoule() >= 1.5, () -> "ecran " + mc.screen, null);
        pas("Pose de l'étal (clic droit sur la dalle)", () -> {
            tenir(s -> s.is(EconomyRegistry.MARKET_STALL_ITEM.get()));
            clic(etalDev().below(), Direction.UP);
        }, () -> etalClient(etalDev()) != null && ecoule() >= 0.5, () -> "bloc " + mc.level.getBlockState(etalDev()), () -> {
            reponse = rcon("e2ecommerce etal lire " + xyz(etalDev()));
            if (!reponse.contains("proprietaire=" + nom())) {
                throw new IllegalStateException(reponse);
            }
            return "etal de Dev pose par un clic droit reel en " + etalDev().toShortString() + " : proprietaire " + nom();
        });
        pas("Clic droit sur son étal (main vide) : la gestion", () -> {
            tenirVide();
            clic(etalDev(), Direction.SOUTH);
        }, () -> mc.screen instanceof StallOwnerScreen && ecoule() >= 0.5, () -> "ecran " + mc.screen,
                () -> "gestion de l'etal ouverte par son proprietaire (" + mc.screen.getClass().getSimpleName() + ", onglet Stock)");
        pas("Onglet Stock : il prend la moitié de ses pains (clic droit) et les pose dans la première case du stock", () -> {
            int pain = caseJoueur(s -> s.is(Items.BREAD));
            if (pain < 0) {
                throw new IllegalStateException("pas de pain");
            }
            painAvant = mc.player.containerMenu.getSlot(pain).getItem().getCount();
            painEnStock = (painAvant + 1) / 2;
            CLICS.add(() -> clicMenu(pain, 1, ClickType.PICKUP));
            CLICS.add(() -> clicMenu(StallOwnerMenu.STOCK_START, 0, ClickType.PICKUP));
        }, () -> mc.screen instanceof StallOwnerScreen && gestion().getSlot(StallOwnerMenu.STOCK_START).getItem().is(Items.BREAD)
                && gestion().getCarried().isEmpty() && ecoule() >= 1.0,
                () -> "case 0 du stock " + gestion().getSlot(StallOwnerMenu.STOCK_START).getItem() + ", curseur " + gestion().getCarried(),
                () -> "stock garni par de vrais clics : " + gestion().getSlot(StallOwnerMenu.STOCK_START).getItem().getCount() + " pains dans la case 0");
        pas("Onglet Offres (clic)", () -> ongletGestion(1), () -> gestion().view() == StallOwnerMenu.VIEW_OFFERS && ecoule() >= 0.3,
                () -> "vue " + gestion().view(), null);
        pas("Il choisit la case d'offre 0 et prend ses pains restants sur le curseur (le modèle)", () -> {
            int[] g = origineEcran();
            clicEcran(g[0] + StallOwnerMenu.STOCK_X - 1 + 9, g[1] + 50 + 9);
            origine = caseJoueur(s -> s.is(Items.BREAD));
            if (origine < 0) {
                throw new IllegalStateException("pas de pain restant");
            }
            clicMenu(origine, 0, ClickType.PICKUP);
        }, () -> gestion().getCarried().is(Items.BREAD) && ecoule() >= 0.5, () -> "curseur " + gestion().getCarried(), null);
        pas("Il tape le lot (2) et le prix (20 cuivres) dans les champs, puis clique « Définir »", () -> {
            int[] g = origineEcran();
            int y = g[1] + 70 + 2 + 3;
            clicEcran(g[0] + 8 + 3 + 6, y);
            mc.screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
            mc.screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            mc.screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            taper("2");
            clicEcran(g[0] + 8 + 24 + 2 + 3 + 10, y);
            taper("20");
            int definirX = g[0] + 8 + (160 - 40 - 40 - 4) + 2 + 20;
            clicEcran(definirX, g[1] + 84 + 6);
        }, () -> {
            MarketStallBlockEntity be = etalClient(etalDev());
            StallOffer offre = be == null ? null : be.offers().get(0);
            return offre != null && offre.lot() == 2 && offre.price() == 20 && offre.template().is(Items.BREAD) && ecoule() >= 0.5;
        }, () -> "offre vue " + etalClient(etalDev()).offers().get(0), () -> "offre definie par de vrais clics (case, curseur, champs tapes, Definir) : "
                + etalClient(etalDev()).offers().get(0) + " ; le pain reste au joueur (curseur " + gestion().getCarried().getCount() + ")");
        pas("Il repose ses pains et referme la gestion", () -> {
            clicMenu(origine, 0, ClickType.PICKUP);
        }, () -> gestion().getCarried().isEmpty() && ecoule() >= 0.5, () -> "curseur " + gestion().getCarried(), () -> {
            fermer();
            reponse = rcon("e2ecommerce etal lire " + xyz(etalDev()));
            Map<String, String> r = lire(reponse);
            if (!("[bread:" + painEnStock + "]").equals(r.get("stock")) || !r.get("offres").contains("0:breadx2@20")) {
                throw new IllegalStateException(reponse);
            }
            return "etal de Dev relu sur le serveur : stock " + r.get("stock") + ", offres " + r.get("offres") + ", registre " + r.get("registre");
        });
    }

    // ------------------------------------------------------------------------------------------------ factures

    private static ItemStack factureDe(UUID id) {
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(EconomyRegistry.INVOICE.get()) && (id == null || id.equals(InvoiceItem.idOf(s).orElse(null)))) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void factures() {
        pas("Dev se met devant la table pour fabriquer un carnet de factures (livre, poche d'encre, 2 papiers, donnés par la console)", () -> {
            rcon("give " + nom() + " minecraft:book 1");
            rcon("give " + nom() + " minecraft:ink_sac 1");
            rcon("give " + nom() + " minecraft:paper 2");
            devant(table());
        }, () -> devantPret(table()) && compter(Items.BOOK) >= 1 && compter(Items.INK_SAC) >= 1 && compter(Items.PAPER) >= 2 && ecoule() >= 1.5,
                () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Clic droit sur la table", () -> {
            tenirVide();
            clic(table(), Direction.SOUTH);
        }, () -> mc.screen instanceof CraftingScreen, () -> "ecran " + mc.screen, null);
        pas("Il pose le livre, l'encre et les deux papiers, et prend le carnet", () -> {
            poserUnParCase(Items.BOOK, 1);
            poserUnParCase(Items.INK_SAC, 2);
            poserUnParCase(Items.PAPER, 4, 5);
            CLICS.add(attendreSortie(40));
            CLICS.add(() -> clicMenu(0, 0, ClickType.QUICK_MOVE));
        }, () -> compter(EconomyRegistry.INVOICE_BOOK.get()) == 1 && ecoule() >= 1.0, () -> "pas de carnet",
                () -> "carnet de factures fabrique a la table avec sa recette (livre, poche d'encre, 2 papiers)");
        pas("Autre revient (connexion simulée), deux blocs au nord de Dev", () -> {
            fermer();
            placerJoueur(placeDev(), 180, 10);
            reponse = rcon("e2ecommerce autre entrer " + xyz(placeAutre()));
        }, () -> mc.screen == null && pres(placeDev()) && autreVu() != null && ecoule() >= 2.0,
                () -> "autre vu " + (autreVu() != null) + ", reponse " + reponse, () -> {
                    if (!("" + (AUTRE_DEPART + ECHANGE - DEPOT_D_AUTRE)).equals(lire(reponse).get("porte"))) {
                        throw new IllegalStateException(reponse);
                    }
                    return "Autre revient : il porte " + lire(reponse).get("porte") + " cuivres";
                });
        pas("Dev utilise le carnet sur Autre (clic droit) : le formulaire s'ouvre", () -> {
            tenir(s -> s.is(EconomyRegistry.INVOICE_BOOK.get()));
            Player cible = autreVu();
            mc.gameMode.interactAt(mc.player, cible, new EntityHitResult(cible), InteractionHand.MAIN_HAND);
            var r = mc.gameMode.interact(mc.player, cible, InteractionHand.MAIN_HAND);
            LOG.info("[e2e] carnet sur Autre : {}", r);
        }, () -> mc.screen instanceof InvoiceFormScreen && ecoule() >= 0.3, () -> "ecran " + mc.screen,
                () -> "formulaire de facture ouvert par le carnet utilise sur Autre : " + mc.screen.getTitle().getString());
        pas("Il tape le montant (150) et le motif, puis clique « Émettre »", () -> {
            CHAT.clear();
            taper("150");
            mc.screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
            taper("Trois miches de pain");
            int left = champ(mc.screen, "left");
            int top = champ(mc.screen, "top");
            clicEcran(left + 10 + 47, top + 108 + 8);
        }, () -> mc.screen == null && ecoule() >= 1.0, () -> "ecran " + mc.screen, () -> {
            reponse = rcon("e2ecommerce autre etat");
            String f = lire(reponse).get("factures");
            if (f == null || !f.endsWith(":150]")) {
                throw new IllegalStateException(reponse);
            }
            return "facture emise par le formulaire : Autre la recoit (" + f + ")";
        });
        pas("Autre paie la facture (le chemin du bouton « Payer ») : les pièces arrivent dans l'inventaire de Dev", () -> {
            valeurAvant = valeurTotale();
            reponse = rcon("e2ecommerce facture payer");
        }, () -> valeurTotale() == valeurAvant + FACTURE_DE_DEV && ecoule() >= 1.0, () -> "valeur " + valeurTotale() + ", reponse " + reponse, () -> {
            Map<String, String> r = lire(reponse);
            if (!"PAID_TO_INVENTORY".equals(r.get("resultat")) || !"true".equals(r.get("recu"))
                    || !("" + AUTRE_PORTE_FIN).equals(r.get("apres"))) {
                throw new IllegalStateException(reponse);
            }
            return "Autre paie : " + r.get("resultat") + ", il passe de " + r.get("avant") + " a " + r.get("apres") + " cuivres, sa facture devient un recu ("
                    + r.get("genre") + ") ; Dev passe de " + valeurAvant + " a " + valeurTotale() + " cuivres (bourse " + Purse.total(bourse()) + ")";
        });
        pas("Autre émet une facture de 120 cuivres à Dev, puis se déconnecte", () -> {
            reponse = rcon("e2ecommerce facture emettre " + nom() + " " + FACTURE_D_AUTRE + " Location de la place au marche");
        }, () -> !factureDe(null).isEmpty() && ecoule() >= 0.5, () -> "aucune facture chez Dev, reponse " + reponse, () -> {
            if (!reponse.contains("resultat=DONE")) {
                throw new IllegalStateException(reponse);
            }
            reponse2 = rcon("e2ecommerce autre quitter");
            if (!reponse2.contains("PARTI") || !("" + AUTRE_PORTE_FIN).equals(lire(reponse2).get("porte"))) {
                throw new IllegalStateException(reponse2);
            }
            ItemStack f = factureDe(null);
            factureId = InvoiceItem.idOf(f).orElseThrow();
            return "Autre emet une facture a Dev (" + reponse.replace(" FIN_E2E", "").trim() + ") : Dev la porte (" + InvoiceItem.amount(f)
                    + " cuivres, de " + InvoiceItem.issuerName(f) + ", n° " + InvoiceItem.shortId(f) + ") ; Autre se deconnecte en portant "
                    + lire(reponse2).get("porte") + " cuivres";
        });
        pas("Dev utilise la facture (clic droit) : son écran", () -> {
            tenir(s -> s.is(EconomyRegistry.INVOICE.get()));
            factureCase = mc.player.getInventory().selected;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> mc.screen instanceof InvoiceScreen && ecoule() >= 0.3, () -> "ecran " + mc.screen, null);
        pas("Il clique « Payer » : la facture devient un reçu, les pièces vont en attente pour Autre (pas de coffre-fort)", () -> {
            valeurAvant = valeurTotale();
            int left = champ(mc.screen, "left");
            int top = champ(mc.screen, "top");
            int boutons = champ(mc.screen, "buttonsY");
            clicEcran(left + 10 + 47, top + boutons + 8);
        }, () -> mc.screen == null && valeurTotale() == valeurAvant - FACTURE_D_AUTRE
                && mc.player.getInventory().getItem(factureCase).getItem() instanceof ReceiptItem && ecoule() >= 1.0,
                () -> "valeur " + valeurTotale() + " (avant " + valeurAvant + "), case " + mc.player.getInventory().getItem(factureCase),
                () -> {
                    long attente = comptes("attente");
                    if (attente != FACTURE_D_AUTRE || valeurTotale() != DEV_FINAL) {
                        throw new IllegalStateException("attente " + attente + ", Dev " + valeurTotale());
                    }
                    return "Dev paie par l'ecran : il passe de " + valeurAvant + " a " + valeurTotale() + " cuivres, la facture devient un recu ("
                            + Receipt.kind(mc.player.getInventory().getItem(factureCase)) + "), " + attente
                            + " cuivres en attente pour Autre (absent, sans coffre-fort)";
                });
        pas("La console remet à Dev une copie de la facture payée ; il essaie de la payer (paquet du bouton « Payer »)", () -> {
            reponse = rcon("e2ecommerce facture copie " + nom() + " " + factureId);
        }, () -> !factureDe(factureId).isEmpty() || ecoule() >= 3.0, () -> "", () -> {
            int caseCopie = -1;
            for (int i = 0; i < 36; i++) {
                if (factureId.equals(InvoiceItem.idOf(mc.player.getInventory().getItem(i)).orElse(null))) {
                    caseCopie = i;
                }
            }
            valeurAvant = valeurTotale();
            if (caseCopie >= 0) {
                BenchNetwork.CHANNEL.sendToServer(new InvoicePayPacket(caseCopie, factureId));
            }
            return "copie remise (" + reponse.replace(" FIN_E2E", "").trim() + "), vue par le client en case " + caseCopie + " : paiement envoye";
        });
        pasD(10, "Deux secondes plus tard : rien n'est payé, la copie est un reçu", () -> {}, () -> ecoule() >= 2.0, () -> "", () -> {
            long attente = comptes("attente");
            long factures = comptes("invoice");
            if (valeurTotale() != valeurAvant || !factureDe(factureId).isEmpty() || attente != FACTURE_D_AUTRE
                    || factures != FACTURE_DE_DEV + FACTURE_D_AUTRE) {
                throw new IllegalStateException("valeur " + valeurAvant + " -> " + valeurTotale() + ", copie encore facture "
                        + !factureDe(factureId).isEmpty() + ", attente " + attente + ", factures " + factures);
            }
            return "une copie de la facture payee ne se paie pas : Dev garde " + valeurTotale() + " cuivres, la copie est devenue un recu, attente "
                    + attente + " et total des factures " + factures + " inchanges";
        });
    }

    // ------------------------------------------------------------------------------------- Dev range son argent

    private static void caisseDeDev() {
        pas("Dev revient à son étal et ouvre la gestion", () -> {
            placerJoueur(etalDev().south(2), 180, 40);
        }, () -> pres(etalDev().south(2)) && ecoule() >= 1.0, () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Clic droit sur son étal", () -> {
            tenirVide();
            clic(etalDev(), Direction.SOUTH);
        }, () -> mc.screen instanceof StallOwnerScreen && ecoule() >= 0.5, () -> "ecran " + mc.screen, null);
        pas("Onglet Caisse (clic) : l'écran de la caisse", () -> ongletGestion(2),
                () -> mc.screen instanceof StallCashScreen && ecoule() >= 0.5, () -> "ecran " + mc.screen, null);
        pas("« Déposer tout » : toute sa monnaie (bourse comprise) entre dans la caisse de son étal", () -> {
            StallCashScreen s = (StallCashScreen) mc.screen;
            int[] c = s.elementCenter("deposit_all").orElseThrow(() -> new IllegalStateException("aucun bouton deposit_all"));
            clicEcran(c[0], c[1]);
        }, () -> mc.screen instanceof StallCashScreen s && s.getMenu().total() == DEV_FINAL && valeurTotale() == 0 && ecoule() >= 0.5,
                () -> "caisse " + ((StallCashScreen) mc.screen).getMenu().total() + ", Dev " + valeurTotale(),
                () -> "Dev range sa monnaie dans la caisse de son etal : caisse " + ((StallCashScreen) mc.screen).getMenu().total()
                        + " cuivres, il ne porte plus rien (" + valeurTotale() + ")");
        pas("Il referme la caisse", () -> fermer(), () -> mc.screen == null && valeurTotale() == 0 && ecoule() >= 0.5, () -> "ecran " + mc.screen, null);
    }

    // --------------------------------------------------------------------------------------------- comptes

    private static void comptesFinaux() {
        pas("/eco stats : en circulation inchangé, transferts attendus, en attente = ce qui est dû à Autre", () -> {
            reponse = rcon("eco stats");
            reponse2 = rcon("e2ecommerce comptes");
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            Map<String, String> c = lire(reponse2);
            Map<String, Long> attendus = new LinkedHashMap<>();
            attendus.put("trade", ECHANGE);
            attendus.put("stall_sale", ACHAT);
            attendus.put("sales_tax", 0L);
            attendus.put("stall_buyback", RACHAT);
            attendus.put("invoice", FACTURE_DE_DEV + FACTURE_D_AUTRE);
            attendus.put("staff_withdraw", 0L);
            attendus.put("tresor", 0L);
            attendus.put("attente", FACTURE_D_AUTRE);
            attendus.put("circulation", circulationDebut);
            for (Map.Entry<String, Long> e : attendus.entrySet()) {
                if (!("" + e.getValue()).equals(c.get(e.getKey()))) {
                    throw new IllegalStateException(e.getKey() + " attendu " + e.getValue() + " : " + reponse2);
                }
            }
            long caisseDev = Long.parseLong(lire(rcon("e2ecommerce etal lire " + xyz(etalDev()))).get("caisse"));
            long caisseAutre = Long.parseLong(lire(rcon("e2ecommerce etal lire " + xyz(etalAutre()))).get("caisse"));
            long coffres = Long.parseLong(c.get("coffres"));
            long somme = coffres + AUTRE_PORTE_FIN + FACTURE_D_AUTRE + caisseAutre + caisseDev + valeurTotale();
            if (somme != circulationDebut || caisseDev != DEV_FINAL || caisseAutre != CAISSE_AUTRE) {
                throw new IllegalStateException("somme " + somme + " au lieu de " + circulationDebut + " (coffres " + coffres + ", caisses "
                        + caisseAutre + " et " + caisseDev + ")");
            }
            // Le texte affiché par /eco stats dit la même chose, en pièces.
            Map<String, String> lignes = statsEnLignes(reponse.replace('\n', '|'));
            for (MoneyTransfer genre : MoneyTransfer.values()) {
                String vu = lignes.get(nomGenre(genre));
                if (vu == null || !vu.equals(fmt(Long.parseLong(c.get(genre.name().toLowerCase(java.util.Locale.ROOT)))))) {
                    throw new IllegalStateException("ligne " + nomGenre(genre) + " : " + vu);
                }
            }
            if (!fmt(FACTURE_D_AUTRE).equals(lignes.get("Pending")) || !fmt(0).equals(lignes.get("Treasury"))) {
                throw new IllegalStateException("attente ou tresor : " + lignes);
            }
            statsAvantArret = reponse.trim().replace('\n', '|');
            return "/eco stats : " + statsAvantArret + " ; en circulation " + circulationDebut + " (inchange depuis le debut de l'etape) = coffres "
                    + coffres + " + porte par Autre " + AUTRE_PORTE_FIN + " + attente " + FACTURE_D_AUTRE + " + caisse de l'etal d'Autre " + caisseAutre
                    + " + caisse de l'etal de Dev " + caisseDev + " + porte par Dev " + valeurTotale() + " : aucune piece creee ni detruite par le commerce";
        });
    }

    // ------------------------------------------------------------------------------------------------- fin

    private static void fin() {
        pas("État d'avant l'arrêt écrit ; objets de l'étape retirés ; le joueur rentre à son point de départ", () -> {
            etalAutreLu = rcon("e2ecommerce etal lire " + xyz(etalAutre())).replace(" FIN_E2E", "").trim();
            etalDevLu = rcon("e2ecommerce etal lire " + xyz(etalDev())).replace(" FIN_E2E", "").trim();
            facturesLues = rcon("e2ecommerce factures").replace(" FIN_E2E", "").trim();
            String ledger = rcon("eco ledger").split("\n")[0].trim();
            try {
                List<String> lignes = new ArrayList<>();
                lignes.add("origine=" + xyz(o));
                lignes.add("depart=" + xyz(depart));
                lignes.add("stats=" + statsAvantArret);
                lignes.add("ledger=" + ledger);
                lignes.add("etalautre=" + etalAutreLu);
                lignes.add("etaldev=" + etalDevLu);
                lignes.add("factures=" + facturesLues);
                lignes.add("paindev=" + painEnStock);
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            // L'étape de la monnaie relit après l'arrêt ce que porte Autre une fois son attente livrée.
            E2eMonnaie.noterAvantArret(mc, statsAvantArret, ledger, AUTRE_PORTE_FIN + FACTURE_D_AUTRE);
            for (String objet : List.of("receipt", "invoice", "invoice_book", "purse", "market_stall")) {
                rcon("clear " + nom() + " oneforallsurvival:" + objet);
            }
            airePrete = false;
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && mc.player.blockPosition().distSqr(depart) < 9, () -> "joueur en " + mc.player.blockPosition(),
                () -> "etat d'avant l'arret note : " + etalAutreLu + " | " + etalDevLu + " | " + facturesLues);
        pas("Le joueur redevient opérateur (console RCON : op)", () -> rcon("op " + nom()),
                () -> operateur() && ecoule() >= 1.5 && mc.player.getHealth() > 0, () -> "operateur " + operateur(),
                () -> "op par RCON, hasPermissions(2) = " + operateur());
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

    /**
     * L'étape du trésor (lot E3), qui suit celle-ci, change {@code /eco stats} (injection au trésor, dépôts, dépenses,
     * salaires) : elle note ici le texte d'avant l'arrêt que la relance de cette étape doit relire. Les étals et les
     * factures, qu'elle ne touche pas, gardent leur référence.
     */
    static void noterStatsAvantArret(Minecraft minecraft, String stats) {
        mc = minecraft;
        Map<String, String> lu = lireFichier();
        lu.put("stats", stats);
        List<String> lignes = new ArrayList<>();
        lu.forEach((k, v) -> lignes.add(k + "=" + v));
        try {
            Files.write(fichier(), lignes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** La ligne « en attente » remplacée par la valeur donnée. */
    private static String avecAttente(String stats, long attente) {
        StringBuilder out = new StringBuilder();
        for (String ligne : stats.split("\\|")) {
            out.append(out.length() == 0 ? "" : "|").append(ligne.trim().startsWith("Pending:") ? "Pending: " + fmt(attente) : ligne);
        }
        return out.toString();
    }

    static void relance() {
        Map<String, String> lu = new LinkedHashMap<>();
        pas("Relance : état d'avant l'arrêt relu ; /eco stats identique ; Dev va devant son étal", () -> {
            lu.putAll(lireFichier());
            o = position(lu.get("origine"));
            depart = position(lu.get("depart"));
            fermer();
            reponse = rcon("eco stats").trim().replace('\n', '|');
            placerJoueur(etalDev().south(2), 180, 40);
        }, () -> pres(etalDev().south(2)) && etalClient(etalDev()) != null && etalClient(etalAutre()) != null && ecoule() >= 2.0,
                () -> "joueur en " + mc.player.blockPosition() + ", etals vus " + (etalClient(etalDev()) != null) + " " + (etalClient(etalAutre()) != null),
                () -> {
                    // Sans la ligne « Salaires dus » : le rattrapage du démarrage (lot E3) y change les dettes de la ville.
                    if (!E2eMonnaie.sansSalaires(reponse).equals(E2eMonnaie.sansSalaires(lu.get("stats")))) {
                        throw new IllegalStateException("stats " + reponse + " ; avant l'arret : " + lu.get("stats"));
                    }
                    return "relance du commerce : /eco stats identique a celui d'avant l'arret (attente comprise) : " + reponse;
                });
        pas("Relance : les deux étals relus sur le serveur (stock, caisse, offres, rachats, registre)", () -> {
            reponse = rcon("e2ecommerce etal lire " + xyz(etalAutre())).replace(" FIN_E2E", "").trim();
            reponse2 = rcon("e2ecommerce etal lire " + xyz(etalDev())).replace(" FIN_E2E", "").trim();
        }, () -> ecoule() >= 0.3, () -> "", () -> {
            if (!reponse.equals(lu.get("etalautre")) || !reponse2.equals(lu.get("etaldev"))) {
                throw new IllegalStateException("etal d'Autre " + reponse + " (avant " + lu.get("etalautre") + ") ; etal de Dev " + reponse2
                        + " (avant " + lu.get("etaldev") + ")");
            }
            return "relance : etal d'Autre relu a l'identique (" + reponse + ") ; etal de Dev relu a l'identique (" + reponse2 + ")";
        });
        pas("Relance : clic droit sur son étal : la gestion montre le même stock et la même offre", () -> {
            tenirVide();
            clic(etalDev(), Direction.SOUTH);
        }, () -> mc.screen instanceof StallOwnerScreen && ecoule() >= 1.0, () -> "ecran " + mc.screen, () -> {
            ItemStack stock = gestion().getSlot(StallOwnerMenu.STOCK_START).getItem();
            StallOffer offre = etalClient(etalDev()).offers().get(0);
            if (!stock.is(Items.BREAD) || !("" + stock.getCount()).equals(lu.get("paindev")) || offre == null || offre.lot() != 2 || offre.price() != 20) {
                throw new IllegalStateException("stock " + stock + ", offre " + offre);
            }
            return "relance : la gestion de l'etal de Dev montre " + stock.getCount() + " pains au stock et l'offre " + offre;
        });
        pas("Relance : onglet Caisse", () -> ongletGestion(2), () -> mc.screen instanceof StallCashScreen s && s.getMenu().total() == DEV_FINAL
                && ecoule() >= 0.5, () -> "ecran " + mc.screen, () -> "relance : la caisse de l'etal de Dev affiche " + DEV_FINAL + " cuivres");
        pas("Relance : il referme, rouvre la gestion et clique l'onglet Registre", () -> {
            fermer();
            StallLedgerCache.clear();
        }, () -> mc.screen == null && ecoule() >= 0.5, () -> "ecran " + mc.screen, null);
        pas("Relance : clic droit sur son étal", () -> clic(etalDev(), Direction.SOUTH), () -> mc.screen instanceof StallOwnerScreen && ecoule() >= 0.5,
                () -> "ecran " + mc.screen, null);
        pas("Relance : onglet Registre", () -> ongletGestion(3), () -> StallLedgerCache.last() != null && ecoule() >= 0.5,
                () -> "registre non recu", () -> {
                    String attendu = lire(lu.get("etaldev")).get("registre");
                    int taille = Integer.parseInt(attendu.substring(0, attendu.indexOf(':')));
                    int vues = StallLedgerCache.last().lines().size();
                    if (vues != Math.min(taille, 20)) {
                        throw new IllegalStateException("lignes vues " + vues + ", attendues " + taille);
                    }
                    return "relance : l'onglet Registre de la gestion montre " + vues + " ligne(s), comme avant l'arret (" + attendu + ")";
                });
        pas("Relance : Autre revient (connexion simulée) : il reçoit son attente et l'avis de paiement", () -> {
            fermer();
            placerJoueur(placeDev(), 180, 10);
            reponse = rcon("e2ecommerce autre retour " + xyz(placeAutre()));
        }, () -> ecoule() >= 1.0, () -> "", () -> {
            Map<String, String> r = lire(reponse);
            String connexion = r.getOrDefault("connexion", "");
            if (!("" + FACTURE_D_AUTRE).equals(r.get("attenteAvant")) || !"0".equals(r.get("attenteApres"))
                    || !("" + (AUTRE_PORTE_FIN + FACTURE_D_AUTRE)).equals(r.get("porte"))
                    || !connexion.contains("message.oneforallsurvival.commerce.delivered")
                    || !connexion.contains("message.oneforallsurvival.invoice.paid.pending")) {
                throw new IllegalStateException(reponse);
            }
            reponse2 = rcon("e2ecommerce autre quitter");
            return "relance : Autre se reconnecte, recoit les " + r.get("attenteAvant") + " cuivres en attente (il porte " + r.get("porte")
                    + ", attente " + r.get("attenteApres") + ") et les messages a la connexion : " + connexion;
        });
        pas("Relance : registre des factures et /eco stats identiques, en attente à 0", () -> {
            reponse = rcon("eco stats").trim().replace('\n', '|');
            reponse2 = rcon("e2ecommerce factures").replace(" FIN_E2E", "").trim();
        }, () -> ecoule() >= 0.3, () -> "", () -> {
            String attendu = avecAttente(lu.get("stats"), 0);
            if (!E2eMonnaie.sansSalaires(reponse).equals(E2eMonnaie.sansSalaires(attendu)) || !reponse2.equals(lu.get("factures"))) {
                throw new IllegalStateException("stats " + reponse + " (attendu " + attendu + ") ; factures " + reponse2 + " (avant " + lu.get("factures") + ")");
            }
            return "relance : registre des factures identique (" + reponse2 + ") ; /eco stats identique a celui d'avant l'arret, attente passee a "
                    + fmt(0) + " apres la livraison";
        });
        pas("Relance : le joueur rentre à son point de départ", () -> {
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && mc.player.blockPosition().distSqr(depart) < 9, () -> "joueur en " + mc.player.blockPosition(), null);
    }

    // ================================================================================================
    // Arrêt
    // ================================================================================================

    /** Avant le {@code /stop}, même après un échec : second joueur déconnecté, joueur rendu opérateur, règles rendues. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null || o == null) {
            return;
        }
        mc = minecraft;
        mc.options.keyShift.setDown(false);
        try {
            rcon("op " + nom());
            rcon("e2ecommerce autre quitter");
            rcon("gamerule doMobSpawning true");
        } catch (RuntimeException e) {
            LOG.error("[e2e] rcon impossible avant l'arret", e);
        }
    }
}
