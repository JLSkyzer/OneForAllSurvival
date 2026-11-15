package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.SafeMenu;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import dev.oneforallsurvival.economy.client.TownFinancesCache;
import dev.oneforallsurvival.economy.client.TreasuryReasonScreen;
import dev.oneforallsurvival.economy.client.TreasuryScreen;
import dev.oneforallsurvival.economy.town.TownFinances;
import dev.oneforallsurvival.economy.town.TreasuryMenu;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Étape « trésor et salaires » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot E3 vu par un
 * vrai client contre un serveur dédié avec le pack complet. Elle suit l'étape du commerce (lot E2), dont elle reprend le
 * monde : Dev a son coffre-fort (posé par l'étape de la monnaie), le registre a ses transferts, Autre a son attente.
 *
 * <p>Les gestes de Dev sont de vrais clics : pose du trésor et du tableau, ouverture du trésor, boutons de l'écran,
 * fenêtre du motif (texte tapé, bouton « Confirmer »), coffre-fort, casse au clic gauche tenu. Le second joueur,
 * {@code Agent}, est une connexion simulée côté serveur ({@link E2eTresorServeur}). L'heure de la paie est décalée par
 * {@code e2etresor horloge} : l'essai n'attend jamais un vrai minuit. Autre n'entre pas ici : sa première reconnexion
 * après l'arrêt doit rester celle que relit l'étape du commerce (livraison de son attente).
 *
 * <p>Comptes (cuivres) : l'équipe injecte 500 au trésor ; Dev dépose 2 or (200) sortis de son coffre-fort, retire 1 or
 * (100, motif) ; le premier minuit paie trois salaires (Dev policier et maire au coffre-fort, Agent policier à
 * l'inventaire) ; le maire dépense le reste ; le second minuit laisse trois salaires dus. Avant l'arrêt : en circulation
 * = avant l'étape + 500 (injection) + la somme de départ d'Agent, joueur neuf.
 */
final class E2eTresor {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-tresor.txt";
    private static final double DELAI = 25;
    private static final int Y_DALLE = 150;
    private static final String AGENT = E2eTresorServeur.AGENT;
    private static final long INJECTION = 500;
    private static final long TAUX_ESSAI = 600;
    /** Deux jours de paie de plus à la relance : le rattrapage du démarrage en clôt deux. */
    private static final long DEUX_JOURS_MIN = 2 * 24 * 60;
    private static final String MOTIF = "Achat de lanternes pour la place";
    private static final String MOTIF_RESTE = "Travaux du port, reste du trésor";
    private static final String MOTIF_INJECTION = "Dotation de depart de la ville";

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;

    private static Minecraft mc;
    private static BlockPos depart;
    private static BlockPos o;
    private static BlockPos coffreDev;
    private static boolean airePrete;

    // Mémoire d'un pas à l'autre.
    private static String reponse = "";
    private static String reponse2 = "";
    private static final List<String> CHAT = new ArrayList<>();
    private static Map<String, String> comptesAvant = Map.of();
    private static long coffreAvant;
    private static String maireAvant = "aucun";
    private static String roleDevAvant = "citoyen";
    private static int registreAvant;
    private static long agentEntree;
    private static long agentAvantMinuit;
    private static long coffreAvantMinuit;
    private static int registreAvantMinuit;
    private static String regleAvant = "";
    private static long payeAuMinuit;
    private static long payeADev;
    private static long reste;
    private static int depenses;
    private static long devPorteAvantCoffre;
    private static long du2;
    private static String messagesAgent = "";
    private static int lignesCoffreFortVues;
    private static String jourArret = "";
    private static long agentArret;
    private static long coffreArret;

    // Les boucles de clics (un clic, puis la réponse du serveur, puis le suivant).
    private static int etatBoucle;
    private static long clicNs;
    private static Object etatAvantClic;
    private static long totalAvantClic;

    private E2eTresor() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape du trésor";
    }

    /** Les messages reçus par le client (appelé par {@link E2eClient#onChat}). */
    static void recevoir(Component message) {
        CHAT.add(message.getString());
    }

    private static long chatContient(String texte) {
        return CHAT.stream().filter(l -> l.contains(texte)).count();
    }

    // ================================================================================================
    // Disposition (relative à l'origine O : le coin ouest de la dalle, au niveau du sol)
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static BlockPos tresor() {
        return p(6, 0, 0);
    }

    private static BlockPos secondTresor() {
        return p(8, 0, 0);
    }

    private static BlockPos tresorDeplace() {
        return p(10, 0, 0);
    }

    /** La maîtresse du tableau (en bas au milieu) ; le tableau couvre x - 1 à x + 1, y à y + 1. */
    private static BlockPos tableau() {
        return p(14, 0, 0);
    }

    private static BlockPos placeAgent() {
        return p(2, 0, 8);
    }

    // ================================================================================================
    // Déroulement (même mécanique que les étapes de la monnaie et du commerce)
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

    private static String fmt(long cuivres) {
        return CoinMath.format(cuivres).getString();
    }

    private static String tr(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    private static long valeurPortee() {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s) + dev.oneforallsurvival.economy.Purse.total(s);
        }
        return n + CoinMath.valueOf(mc.player.getOffhandItem());
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

    private static int compter(net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
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

    /** Une main vide : une case libre de la barre d'action, sinon la case 8 vidée dans une case libre du sac. */
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

    /** Un vrai clic droit sur la face du dessus du sol, sous {@code pos} : l'objet en main se pose en {@code pos}. */
    private static void poserSur(BlockPos pos) {
        clic(pos.below(), Direction.UP);
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

    /** Un vrai clic sur un bouton de l'écran du trésor ou du coffre-fort, au centre de l'élément nommé. */
    private static void clicElement(String id, int bouton) {
        CoinAccountScreen<?> s = (CoinAccountScreen<?>) mc.screen;
        int[] c = s.elementCenter(id).orElseThrow(() -> new IllegalStateException("aucun element " + id));
        boolean pris = s.mouseClicked(c[0], c[1], bouton);
        LOG.info("[e2e] clic {} sur {} de {} en ({}, {}) : {}", bouton == 0 ? "gauche" : "droit", id, s.getClass().getSimpleName(),
                c[0], c[1], pris);
    }

    private static void taper(String texte) {
        for (char c : texte.toCharArray()) {
            mc.screen.charTyped(c, 0);
        }
    }

    private static Object champ(Object objet, String nom) {
        try {
            java.lang.reflect.Field f = objet.getClass().getDeclaredField(nom);
            f.setAccessible(true);
            return f.get(objet);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("champ " + nom + " illisible : " + e);
        }
    }

    /** Le texte du champ de la fenêtre du motif. */
    private static String motifSaisi(TreasuryReasonScreen s) {
        return ((net.minecraft.client.gui.components.EditBox) champ(s, "reason")).getValue();
    }

    /** Un vrai clic sur « Confirmer » de la fenêtre du motif (position lue sur l'écran). */
    private static void confirmerMotif(TreasuryReasonScreen s) {
        int gauche = (Integer) champ(s, "left");
        int haut = (Integer) champ(s, "top");
        int pad = (Integer) champ(s, "PAD");
        int boutonsY = (Integer) champ(s, "BUTTONS_Y");
        int largeur = (Integer) champ(s, "BUTTON_W");
        int hauteur = (Integer) champ(s, "BUTTON_H");
        double x = gauche + pad + largeur / 2.0;
        double y = haut + boutonsY + hauteur / 2.0;
        boolean pris = s.mouseClicked(x, y, 0);
        LOG.info("[e2e] clic sur Confirmer de la fenetre du motif en ({}, {}) : {}", x, y, pris);
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

    /** Les éléments d'une liste {@code [a;b;c;]}. */
    private static List<String> liste(String crochets) {
        if (crochets == null || crochets.length() < 2) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : crochets.substring(1, crochets.length() - 1).split(";")) {
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private static Map<String, String> comptes() {
        return lire(rcon("e2ecommerce comptes"));
    }

    private static long compte(Map<String, String> c, String cle) {
        String v = c.get(cle);
        if (v == null) {
            throw new IllegalStateException("compte " + cle + " absent : " + c);
        }
        return Long.parseLong(v);
    }

    private static long coffreDe(String joueur) {
        return Long.parseLong(lire(rcon("e2etresor coffre " + joueur)).get("total"));
    }

    private static Map<String, String> tresorServeur() {
        return lire(rcon("e2etresor tresor"));
    }

    private static Map<String, String> paie() {
        return lire(rcon("e2etresor paie"));
    }

    private static Map<String, String> agentEtat() {
        return lire(rcon("e2etresor agent etat"));
    }

    /** Les lignes du registre du trésor à partir de l'index donné (0 : la plus ancienne), « GENRE/acteur/montant/motif ». */
    private static List<String> registre(int depuis) {
        List<String> out = new ArrayList<>();
        int i = depuis;
        for (int garde = 0; garde < 500; garde++) {
            Map<String, String> r = lire(rcon("e2etresor registre " + i));
            out.addAll(liste(r.get("lignes")));
            int taille = Integer.parseInt(r.get("taille"));
            int suite = Integer.parseInt(r.get("suite"));
            if (suite >= taille || suite <= i) {
                break;
            }
            i = suite;
        }
        return out;
    }

    private static String genre(String ligne) {
        return ligne.split("/", 4)[0];
    }

    private static String acteur(String ligne) {
        return ligne.split("/", 4)[1];
    }

    private static long montant(String ligne) {
        return Long.parseLong(ligne.split("/", 4)[2]);
    }

    private static String motif(String ligne) {
        String[] t = ligne.split("/", 4);
        return t.length > 3 ? t[3] : "";
    }

    private static long somme(List<String> lignes) {
        return lignes.stream().mapToLong(E2eTresor::montant).sum();
    }

    private static List<String> deGenre(List<String> lignes, String genre) {
        return lignes.stream().filter(l -> genre.equals(genre(l))).toList();
    }

    private static boolean chunksPrets(BlockPos centre) {
        return mc.level.getChunkSource().hasChunk(centre.getX() >> 4, centre.getZ() >> 4)
                && mc.level.getChunkSource().hasChunk((centre.getX() + 16) >> 4, centre.getZ() >> 4)
                && mc.level.getChunkSource().hasChunk((centre.getX() - 16) >> 4, centre.getZ() >> 4);
    }

    // ================================================================================================
    // Premier passage
    // ================================================================================================

    static void premierePhase() {
        preparation();
        pose();
        citoyen();
        maire();
        tableauDeLaMairie();
        minuit();
        deplacement();
        comptesEtArret();
    }

    private static void preparation() {
        pas("Le joueur, en spectateur, se rend au centre de l'aire du trésor (les chunks se chargent)", () -> {
            fermer();
            CHAT.clear();
            depart = mc.player.blockPosition();
            BlockPos spawn = mc.level.getSharedSpawnPos();
            int x = depart.getX() + 100;
            int z = depart.getZ() + 170;
            if (Math.abs(x - spawn.getX()) <= 40 && Math.abs(z - spawn.getZ()) <= 40) {
                x = depart.getX() - 180;
            }
            o = new BlockPos(x, Y_DALLE, z);
            coffreDev = E2eMonnaie.coffreFortNote(mc);
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 10.5) + " " + (o.getY() + 5) + " " + (o.getZ() + 4.5));
        }, () -> ecoule() >= 4 && chunksPrets(p(10, 0, 4)) && mc.level.getChunkSource().hasChunk((o.getX() - 4) >> 4, (o.getZ() - 8) >> 4)
                && mc.level.getChunkSource().hasChunk((o.getX() + 30) >> 4, (o.getZ() + 20) >> 4),
                () -> "chunks de l'aire non recus, joueur en " + mc.player.blockPosition(), null);
        pas("Aire du trésor sur une dalle ; Dev opérateur (équipe), en survie, ne porte rien", () -> {
            rcon("difficulty peaceful");
            rcon("gamerule doMobSpawning false");
            rcon("fill " + xyz(p(-4, 0, -8)) + " " + xyz(p(30, 8, 20)) + " air");
            rcon("fill " + xyz(p(-4, -1, -8)) + " " + xyz(p(30, -1, 20)) + " stone");
            rcon("effect clear " + nom());
            placerJoueur(p(6, 0, 3), 180, 30);
            rcon("gamemode survival " + nom());
            rcon("op " + nom());
        }, () -> mc.level.getBlockState(o.below()).is(Blocks.STONE) && operateur() && mc.player.getHealth() > 0 && pres(p(6, 0, 3))
                && ecoule() >= 2,
                () -> "operateur " + operateur() + ", joueur en " + mc.player.blockPosition() + ", dalle " + mc.level.getBlockState(o.below()),
                () -> {
                    airePrete = true;
                    return "tresor : dalle en " + o.toShortString() + " (joueur parti de " + depart.toShortString() + "), Dev operateur = "
                            + operateur() + ", gamemode " + mc.gameMode.getPlayerMode() + " ; coffre-fort de Dev (etape de la monnaie) en "
                            + coffreDev.toShortString();
                });
        pas("Départ : aucun trésor posé, solde 0, aucune dette ; comptes, coffre-fort de Dev, maire et rôle relevés", () -> {
            comptesAvant = comptes();
            reponse = rcon("e2etresor tresor") + " " + rcon("e2etresor paie");
            reponse2 = rcon("e2etresor maire");
            coffreAvant = coffreDe("Dev");
        }, () -> ecoule() >= 0.5, () -> "", () -> {
            Map<String, String> t = tresorServeur();
            Map<String, String> pa = paie();
            Map<String, String> m = lire(reponse2);
            maireAvant = m.get("nom");
            roleDevAvant = m.get("roleDev");
            registreAvant = Integer.parseInt(t.get("lignes"));
            if (!"0".equals(t.get("total")) || !"aucune".equals(t.get("position")) || !"0".equals(pa.get("nombre"))
                    || compte(comptesAvant, "tresor") != 0 || valeurPortee() != 0 || coffreAvant <= 0) {
                throw new IllegalStateException("depart inattendu : " + t + " " + pa + " ; Dev porte " + valeurPortee()
                        + ", coffre-fort " + coffreAvant + " ; " + comptesAvant);
            }
            return "depart du tresor : " + reponse.replace(" FIN_E2E", "") + " ; Dev porte 0, son coffre-fort " + coffreAvant
                    + " cuivres ; maire avant l'etape : " + maireAvant + ", role de Dev : " + roleDevAvant + " ; comptes "
                    + comptesAvant;
        });
    }

    // --------------------------------------------------------------------------------------------- pose

    private static void pose() {
        pas("L'équipe donne deux trésors à Dev (/give par la console)", () -> {
            rcon("give " + nom() + " oneforallsurvival:treasury 2");
        }, () -> compter(EconomyRegistry.TREASURY_ITEM.get()) == 2 && ecoule() >= 1.0,
                () -> "tresors dans l'inventaire : " + compter(EconomyRegistry.TREASURY_ITEM.get()), null);
        pas("Dev, opérateur, pose le trésor par un vrai clic droit", () -> {
            tenir(s -> s.is(EconomyRegistry.TREASURY_ITEM.get()));
            poserSur(tresor());
        }, () -> mc.level.getBlockState(tresor()).is(EconomyRegistry.TREASURY.get())
                && lire(rcon("e2etresor tresor")).get("position").equals(tresor().getX() + "," + tresor().getY() + "," + tresor().getZ())
                && ecoule() >= 1.0,
                () -> "bloc " + mc.level.getBlockState(tresor()) + ", serveur " + rcon("e2etresor tresor"),
                () -> "tresor pose par un clic droit reel en " + tresor().toShortString() + " ; le serveur note l'emplacement ("
                        + rcon("e2etresor tresor").replace(" FIN_E2E", "") + ")");
        pas("Dev essaie de poser le second trésor : refusé, l'objet reste visible dans son inventaire", () -> {
            CHAT.clear();
            tenir(s -> s.is(EconomyRegistry.TREASURY_ITEM.get()));
            poserSur(secondTresor());
        }, () -> ecoule() >= 2.0, () -> "", () -> {
            String attendu = tr("message.oneforallsurvival.treasury.exists", tresor().getX(), tresor().getY(), tresor().getZ());
            Map<String, String> t = tresorServeur();
            if (!mc.level.getBlockState(secondTresor()).isAir() || compter(EconomyRegistry.TREASURY_ITEM.get()) != 1
                    || !t.get("position").equals(tresor().getX() + "," + tresor().getY() + "," + tresor().getZ())
                    || chatContient(attendu) == 0) {
                throw new IllegalStateException("bloc " + mc.level.getBlockState(secondTresor()) + ", objets "
                        + compter(EconomyRegistry.TREASURY_ITEM.get()) + ", serveur " + t + ", chat " + CHAT);
            }
            return "second tresor refuse : bloc vide en " + secondTresor().toShortString() + ", l'objet reste dans l'inventaire du client ("
                    + compter(EconomyRegistry.TREASURY_ITEM.get()) + "), message « " + attendu + " »";
        });
        pas("L'équipe injecte 500 cuivres au trésor (/eco treasury give, console) ; Dev range l'objet en trop", () -> {
            reponse = rcon("eco treasury give " + INJECTION + " " + MOTIF_INJECTION);
            rcon("clear " + nom() + " oneforallsurvival:treasury");
        }, () -> ecoule() >= 1.0, () -> "", () -> {
            Map<String, String> t = tresorServeur();
            Map<String, String> c = comptes();
            if (!("" + INJECTION).equals(t.get("total")) || compte(c, "circulation") != compte(comptesAvant, "circulation") + INJECTION
                    || compter(EconomyRegistry.TREASURY_ITEM.get()) != 0) {
                throw new IllegalStateException("tresor " + t + ", comptes " + c + ", reponse " + reponse);
            }
            return "/eco treasury give " + INJECTION + " : " + reponse.trim() + " ; tresor " + t.get("total") + " cuivres ; en circulation "
                    + compte(comptesAvant, "circulation") + " -> " + compte(c, "circulation") + " (source declaree de l'equipe)";
        });
    }

    // --------------------------------------------------------------------------------------------- citoyen

    private static void citoyen() {
        pas("Agent se connecte (connexion simulée), citoyen, sans droits", () -> {
            placerJoueur(p(3, 0, 5), 180, 20);
            rcon("e2etresor agent quitter");
            reponse = rcon("e2etresor agent entrer " + xyz(placeAgent()));
            rcon("role set " + AGENT + " citoyen");
        }, () -> ecoule() >= 1.0, () -> "", () -> {
            Map<String, String> r = lire(reponse);
            if (!"false".equals(r.get("operateur")) || r.get("porte") == null) {
                throw new IllegalStateException(reponse);
            }
            agentEntree = Long.parseLong(r.get("porte"));
            if (agentEntree != 0 && agentEntree != 500) {
                throw new IllegalStateException("Agent porte " + agentEntree + " a son entree (ni 0 ni sa somme de depart)");
            }
            return "Agent connecte (simule, non operateur), il porte " + agentEntree + " cuivres (somme de depart d'un joueur neuf)";
        });
        pas("Agent (citoyen) n'ouvre pas le trésor (NOT_ALLOWED) et ne le casse pas", () -> {
            reponse = rcon("e2etresor agent ouvrir " + xyz(tresor()));
            reponse2 = rcon("e2etresor agent casser " + xyz(tresor()));
            // Il s'écarte du trésor : Dev s'y tiendra.
            rcon("tp " + AGENT + " " + (placeAgent().getX() + 0.5) + " " + placeAgent().getY() + " " + (placeAgent().getZ() + 0.5));
        }, () -> ecoule() >= 1.0, () -> "", () -> {
            Map<String, String> r = lire(reponse);
            Map<String, String> c = lire(reponse2);
            if (!"false".equals(r.get("ouvert")) || !"NOT_ALLOWED".equals(r.get("retrait")) || "TreasuryMenu".equals(r.get("menu"))
                    || !r.getOrDefault("messages", "").contains("message.oneforallsurvival.treasury.open_denied")
                    || !"false".equals(c.get("casse")) || !"true".equals(c.get("tresor"))
                    || !mc.level.getBlockState(tresor()).is(EconomyRegistry.TREASURY.get())
                    || !(INJECTION + "").equals(tresorServeur().get("total"))) {
                throw new IllegalStateException(reponse + " / " + reponse2);
            }
            return "citoyen : clic droit refuse (" + r.get("messages") + ", menu " + r.get("menu") + "), retrait force " + r.get("retrait")
                    + " ; casse refusee (" + c.get("messages") + "), le tresor reste en place avec ses " + INJECTION + " cuivres";
        });
    }

    // --------------------------------------------------------------------------------------------- maire

    private static void maire() {
        pas("Dev est nommé maire (/role mayor set) et n'est plus opérateur (deop par la console)", () -> {
            rcon("role mayor set " + nom());
            rcon("deop " + nom());
        }, () -> !operateur() && ecoule() >= 1.5, () -> "operateur " + operateur(),
                () -> "Dev maire (" + rcon("e2etresor maire").replace(" FIN_E2E", "") + "), hasPermissions(2) cote client = " + operateur());
        pas("Dev va à son coffre-fort (étape de la monnaie) pour y prendre 2 or", () -> {
            airePrete = false;
            rcon("tp " + nom() + " " + (coffreDev.getX() + 0.5) + " " + coffreDev.getY() + " " + (coffreDev.getZ() + 2.5) + " 180 40");
        }, () -> ecoule() >= 2 && pres(coffreDev.south(2)) && mc.level.getBlockState(coffreDev).is(EconomyRegistry.SAFE.get()),
                () -> "joueur en " + mc.player.blockPosition() + ", bloc " + mc.level.getBlockState(coffreDev), null);
        pas("Clic droit sur son coffre-fort", () -> {
            tenirVide();
            clic(coffreDev, Direction.SOUTH);
        }, () -> ecranOuvert(SafeMenu.class) && ecoule() >= 1.0, () -> "ecran " + mc.screen,
                () -> "coffre-fort de Dev ouvert : " + menuPieces().total() + " cuivres, " + menuPieces().count(Coin.GOLD) + " or");
        pasD(40, "S'il manque d'or au coffre-fort : Change (casser une émeraude, ou un rubis) jusqu'à 2 or", () -> {
            etatBoucle = 0;
            clicNs = 0;
            etatAvantClic = null;
        }, () -> {
            CoinAccountMenu m = menuPieces();
            if (m.count(Coin.GOLD) >= 2) {
                return true;
            }
            String etat = Arrays.toString(Arrays.stream(Coin.values()).mapToLong(m::count).toArray());
            if (etatAvantClic != null && etat.equals(etatAvantClic) && System.nanoTime() - clicNs < 4_000_000_000L) {
                return false;
            }
            etatAvantClic = etat;
            clicNs = System.nanoTime();
            clicElement(m.count(Coin.EMERALD) > 0 ? "break.emerald" : "break.ruby", 0);
            return false;
        }, () -> "or au coffre-fort : " + menuPieces().count(Coin.GOLD), () -> "coffre-fort : " + menuPieces().count(Coin.GOLD)
                + " or disponibles (total inchange : " + menuPieces().total() + ")");
        for (int i = 1; i <= 2; i++) {
            int n = i;
            pas("Clic droit sur « 1 » de la ligne de l'or du coffre-fort (" + n + "/2) : la pièce sort dans l'inventaire", () -> {
                totalAvantClic = menuPieces().total();
                clicElement("row.gold.1", 1);
            }, () -> ecranOuvert(SafeMenu.class) && menuPieces().total() == totalAvantClic - 100 && pieces(Coin.GOLD) == n && ecoule() >= 0.5,
                    () -> "coffre-fort " + menuPieces().total() + ", or porte " + pieces(Coin.GOLD), null);
        }
        pas("Il referme son coffre-fort et revient devant le trésor", () -> {
            fermer();
            placerJoueur(p(6, 0, 3), 180, 30);
        }, () -> mc.screen == null && pres(p(6, 0, 3)) && pieces(Coin.GOLD) == 2 && coffreDe("Dev") == coffreAvant - 200 && ecoule() >= 2.0,
                () -> "joueur en " + mc.player.blockPosition() + ", or " + pieces(Coin.GOLD) + ", coffre-fort " + coffreDe("Dev"),
                () -> {
                    airePrete = true;
                    return "Dev porte 2 or sortis de son coffre-fort (" + coffreAvant + " -> " + coffreDe("Dev") + " cuivres)";
                });
        pas("Dev, maire non opérateur, ouvre le trésor par un vrai clic droit", () -> {
            tenirVide();
            clic(tresor(), Direction.SOUTH);
        }, () -> ecranOuvert(TreasuryMenu.class) && mc.screen instanceof TreasuryScreen && ecoule() >= 1.0, () -> "ecran " + mc.screen,
                () -> "ecran du tresor ouvert par le maire (non operateur) : " + menuPieces().total() + " cuivres, "
                        + menuPieces().count(Coin.GOLD) + " or");
        for (int i = 1; i <= 2; i++) {
            int n = i;
            pas("Clic gauche sur « 1 » de la ligne de l'or (" + n + "/2) : dépôt au trésor", () -> {
                totalAvantClic = menuPieces().total();
                clicElement("row.gold.1", 0);
            }, () -> ecranOuvert(TreasuryMenu.class) && menuPieces().total() == totalAvantClic + 100 && pieces(Coin.GOLD) == 2 - n
                    && ecoule() >= 0.5, () -> "tresor " + menuPieces().total() + ", or porte " + pieces(Coin.GOLD),
                    () -> "depot " + n + " : l'ecran montre " + menuPieces().total() + " cuivres, l'inventaire du client " + pieces(Coin.GOLD) + " or");
        }
        pas("Clic droit sur « 1 » de la ligne de l'or : la fenêtre « Motif de la dépense » s'ouvre", () -> {
            totalAvantClic = menuPieces().total();
            clicElement("row.gold.1", 1);
        }, () -> mc.screen instanceof TreasuryReasonScreen && ecoule() >= 0.5, () -> "ecran " + mc.screen,
                () -> "fenetre du motif ouverte (champ « " + motifSaisi((TreasuryReasonScreen) mc.screen) + " »)");
        pas("Dev tape le motif et clique « Confirmer » : retour à l'écran du trésor, 1 or retiré", () -> {
            TreasuryReasonScreen s = (TreasuryReasonScreen) mc.screen;
            taper(MOTIF);
            if (!MOTIF.equals(motifSaisi(s))) {
                throw new IllegalStateException("motif saisi : " + motifSaisi(s));
            }
            confirmerMotif(s);
        }, () -> mc.screen instanceof TreasuryScreen && menuPieces().total() == totalAvantClic - 100 && pieces(Coin.GOLD) == 1
                && ecoule() >= 0.5, () -> "ecran " + mc.screen + ", or porte " + pieces(Coin.GOLD),
                () -> "depense confirmee : l'ecran du tresor montre " + menuPieces().total() + " cuivres, Dev porte " + pieces(Coin.GOLD) + " or");
        pas("Il referme ; /treasury log (en son nom) montre la pose, l'injection, les dépôts et la dépense avec son motif", () -> {
            fermer();
            CHAT.clear();
            mc.player.connection.sendCommand("treasury log");
        }, () -> ecoule() >= 1.5 && chatContient(MOTIF) > 0, () -> "chat " + CHAT, () -> {
            List<String> lignes = registre(registreAvant);
            List<String> genres = lignes.stream().map(E2eTresor::genre).toList();
            if (!genres.equals(List.of("PLACED", "INJECTION", "DEPOSIT", "DEPOSIT", "SPEND")) || !"Dev".equals(acteur(lignes.get(0)))
                    || montant(lignes.get(1)) != INJECTION || !motif(lignes.get(1)).contains(MOTIF_INJECTION)
                    || montant(lignes.get(2)) != 100 || montant(lignes.get(3)) != 100 || montant(lignes.get(4)) != 100
                    || !MOTIF.equals(motif(lignes.get(4))) || !"Dev".equals(acteur(lignes.get(4)))) {
                throw new IllegalStateException("registre du tresor : " + lignes);
            }
            String chat = String.join(" || ", CHAT);
            for (String cle : List.of("placed", "injection", "deposit", "spend")) {
                String libelle = tr("economy.oneforallsurvival.treasury.kind." + cle);
                if (!chat.contains(libelle)) {
                    throw new IllegalStateException("/treasury log sans « " + libelle + " » : " + chat);
                }
            }
            String ledger = rcon("eco ledger");
            if (!ledger.contains("Dev : " + MOTIF) || !ledger.contains("Dev : depot au tresor")) {
                throw new IllegalStateException("/eco ledger : " + ledger);
            }
            return "/treasury log (maire) : " + chat.replace('\n', '|') + " ; registre du serveur : " + lignes + " ; /eco ledger note les transferts "
                    + "(« Dev : depot au tresor », « Dev : " + MOTIF + " »)";
        });
    }

    // --------------------------------------------------------------------------------------------- tableau

    private static void tableauDeLaMairie() {
        pas("Le maire prend un tableau de la mairie (/treasury board, en son nom)", () -> {
            mc.player.connection.sendCommand("treasury board");
        }, () -> compter(EconomyRegistry.TOWN_BOARD_ITEM.get()) == 1 && ecoule() >= 0.5,
                () -> "tableaux dans l'inventaire : " + compter(EconomyRegistry.TOWN_BOARD_ITEM.get()), null);
        pas("Il se place devant l'emplacement du tableau", () -> placerJoueur(tableau().south(3), 180, 15),
                () -> pres(tableau().south(3)) && ecoule() >= 1.5, () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Il pose le tableau (3 x 2) par un vrai clic droit", () -> {
            tenir(s -> s.is(EconomyRegistry.TOWN_BOARD_ITEM.get()));
            poserSur(tableau());
        }, () -> ecoule() >= 1.5 && mc.level.getBlockState(tableau()).is(EconomyRegistry.TOWN_BOARD.get())
                && mc.level.getBlockState(tableau().west().above()).is(EconomyRegistry.TOWN_BOARD.get())
                && mc.level.getBlockState(tableau().east().above()).is(EconomyRegistry.TOWN_BOARD.get()),
                () -> "blocs " + mc.level.getBlockState(tableau()) + " / " + mc.level.getBlockState(tableau().east().above()),
                () -> "tableau pose en " + tableau().toShortString() + " (six parties vues par le client)");
        pas("Le cache du client et le rendu affichent le solde du trésor ; le regard est sur le tableau", () -> {
            placerJoueur(tableau().south(3), 180, 5);
        }, () -> ecoule() >= 2.0 && new TownFinances.Snapshot(600, 0, 0).equals(TownFinancesCache.get()),
                () -> "cache " + TownFinancesCache.get(),
                () -> "tableau : cache du client " + TownFinancesCache.get() + " ; lignes du rendu « " + TownFinances.balanceLine(TownFinancesCache.get())
                        .getString() + " » et « " + TownFinances.owedLine(TownFinancesCache.get()).getString() + " »");
        pas("Clic droit sur le tableau : le même texte dans le chat", () -> {
            CHAT.clear();
            tenirVide();
            clic(tableau().above(), Direction.SOUTH);
        }, () -> ecoule() >= 1.0 && chatContient(tr("gui.oneforallsurvival.town_board.title")) > 0, () -> "chat " + CHAT, () -> {
            String attendu = TownFinances.describe(TownFinancesCache.get()).getString();
            if (CHAT.stream().noneMatch(attendu::equals)) {
                throw new IllegalStateException("chat " + CHAT + ", attendu « " + attendu + " »");
            }
            return "clic droit sur le tableau : « " + attendu.replace('\n', '|') + " » dans le chat, comme le rendu";
        });
    }

    // --------------------------------------------------------------------------------------------- minuit

    private static void minuit() {
        pas("Salaires d'essai : policier et maire à " + TAUX_ESSAI + " par heure ; Dev policier (et maire), Agent policier sans coffre-fort",
                () -> {
                    rcon("eco salary set policier " + TAUX_ESSAI);
                    rcon("eco salary set mayor " + TAUX_ESSAI);
                    rcon("role set " + nom() + " policier");
                    rcon("role set " + AGENT + " policier");
                    placerJoueur(p(6, 0, 3), 180, 30);
                }, () -> ecoule() >= 1.5 && pres(p(6, 0, 3)), () -> "", () -> {
                    Map<String, String> a = agentEtat();
                    Map<String, String> m = lire(rcon("e2etresor maire"));
                    if (!"policier".equals(a.get("role")) || !"Dev".equals(m.get("nom")) || !"policier".equals(m.get("roleDev"))
                            || !"false".equals(lire(rcon("e2etresor coffre " + AGENT)).get("pose"))) {
                        throw new IllegalStateException(a + " / " + m);
                    }
                    return "taux : " + rcon("eco salary list").replace('\n', '|').trim() + " ; Dev policier et maire, Agent policier sans coffre-fort";
                });
        pasD(200, "Horloge réglée à 100 s de la frontière du jour de paie ; au passage, la paie de minuit", () -> {
            Map<String, String> pa = paie();
            regleAvant = pa.get("regle");
            registreAvantMinuit = Integer.parseInt(tresorServeur().get("lignes"));
            coffreAvantMinuit = coffreDe("Dev");
            agentAvantMinuit = Long.parseLong(agentEtat().get("porte"));
            CHAT.clear();
            reponse = rcon("e2etresor horloge frontiere 100");
            LOG.info("[e2e] horloge : {}", reponse);
            clicNs = System.nanoTime();
        }, () -> {
            if (System.nanoTime() - clicNs < 2_000_000_000L) {
                return false;
            }
            clicNs = System.nanoTime();
            return !regleAvant.equals(paie().get("regle"));
        }, () -> "pas de cloture : " + rcon("e2etresor paie") + " ; " + rcon("e2etresor horloge"), () -> {
            Map<String, String> pa = paie();
            List<String> lignes = registre(registreAvantMinuit);
            List<String> dus = deGenre(lignes, "SALARY_OWED");
            List<String> payes = deGenre(lignes, "SALARY_PAID");
            List<String> clos = deGenre(lignes, "DAY_CLOSED");
            payeAuMinuit = somme(payes);
            payeADev = somme(payes.stream().filter(l -> "Dev".equals(acteur(l))).toList());
            long aAgent = somme(payes.stream().filter(l -> AGENT.equals(acteur(l))).toList());
            long coffre = coffreDe("Dev");
            Map<String, String> etatAgent = agentEtat();
            long agent = Long.parseLong(etatAgent.get("porte"));
            messagesAgent = etatAgent.getOrDefault("messages", "");
            List<String> cles = payes.stream().map(l -> acteur(l) + ":" + motif(l).split(" ")[1]).sorted().toList();
            if (dus.size() != 3 || payes.size() != 3 || clos.size() != 1 || lignes.size() != 7
                    || !cles.equals(List.of(AGENT + ":policier", "Dev:mayor", "Dev:policier"))
                    || payes.stream().anyMatch(l -> montant(l) <= 0) || somme(dus) != payeAuMinuit
                    || montant(clos.get(0)) != payeAuMinuit || !motif(clos.get(0)).equals(regleAvantPlusUn() + " : 3 salaire(s)")
                    || !"0".equals(pa.get("nombre")) || coffre != coffreAvantMinuit + payeADev || agent != agentAvantMinuit + aAgent
                    || Long.parseLong(tresorServeur().get("total")) != 600 - payeAuMinuit) {
                throw new IllegalStateException("registre " + lignes + " ; paie " + pa + " ; coffre-fort " + coffreAvantMinuit + " -> " + coffre
                        + " ; Agent " + agentAvantMinuit + " -> " + agent + " ; tresor " + tresorServeur());
            }
            return "minuit (horloge decalee) : jour " + regleAvantPlusUn() + " clos ; " + lignes + " ; Dev paye dans son coffre-fort "
                    + coffreAvantMinuit + " -> " + coffre + " (+" + payeADev + ", deux salaires) ; Agent paye dans son inventaire " + agentAvantMinuit
                    + " -> " + agent + " (+" + aAgent + ") ; tresor " + (600 - payeAuMinuit);
        });
        pas("Avis de paie : Dev (chat) et Agent (messages du serveur)", () -> {}, () -> ecoule() >= 0.5, () -> "", () -> {
            String auCoffre = tr("message.oneforallsurvival.salary.to.safe");
            if (chatContient(auCoffre) != 2 || !messagesAgent.contains("message.oneforallsurvival.salary.paid")) {
                throw new IllegalStateException("chat " + CHAT + " ; Agent " + messagesAgent);
            }
            return "avis : Dev lit deux fois « " + auCoffre + " » (" + CHAT.stream().filter(l -> l.contains(auCoffre)).toList()
                    + ") ; Agent recoit " + messagesAgent;
        });
        pas("Le maire rouvre le trésor", () -> {
            tenirVide();
            clic(tresor(), Direction.SOUTH);
        }, () -> mc.screen instanceof TreasuryScreen && ecoule() >= 1.0, () -> "ecran " + mc.screen,
                () -> "tresor ouvert : " + menuPieces().total() + " cuivres a depenser");
        pasD(120, "Le maire dépense le reste du trésor, retrait par retrait, motif tapé à chaque fois", () -> {
            etatBoucle = 0;
            clicNs = 0;
            depenses = 0;
            reste = menuPieces().total();
            devPorteAvantCoffre = valeurPortee();
        }, () -> {
            long maintenant = System.nanoTime();
            if (etatBoucle == 0) {
                if (!(mc.screen instanceof TreasuryScreen)) {
                    return false;
                }
                long total = menuPieces().total();
                if (total == 0) {
                    return true;
                }
                if (maintenant - clicNs < 300_000_000L) {
                    return false;
                }
                Coin piece = null;
                for (int i = Coin.values().length - 1; i >= 0 && piece == null; i--) {
                    if (menuPieces().count(Coin.values()[i]) > 0) {
                        piece = Coin.values()[i];
                    }
                }
                long n = menuPieces().count(piece);
                int quantite = n >= 64 ? 64 : n >= 10 ? 10 : 1;
                totalAvantClic = total;
                clicElement("row." + piece.key() + "." + quantite, 1);
                etatBoucle = 1;
                clicNs = maintenant;
                return false;
            }
            if (etatBoucle == 1) {
                if (mc.screen instanceof TreasuryReasonScreen s) {
                    // Le dernier motif confirmé est proposé : il n'est tapé que si le champ est vide.
                    if (motifSaisi(s).isEmpty()) {
                        taper(MOTIF_RESTE);
                    }
                    confirmerMotif(s);
                    etatBoucle = 2;
                    clicNs = maintenant;
                } else if (maintenant - clicNs > 5_000_000_000L) {
                    throw new IllegalStateException("fenetre du motif jamais ouverte");
                }
                return false;
            }
            if (mc.screen instanceof TreasuryScreen && menuPieces().total() < totalAvantClic) {
                depenses++;
                etatBoucle = 0;
                clicNs = maintenant;
            } else if (maintenant - clicNs > 5_000_000_000L) {
                throw new IllegalStateException("retrait sans effet : " + menuPieces().total());
            }
            return false;
        }, () -> "ecran " + mc.screen + ", tresor " + (mc.screen instanceof TreasuryScreen ? menuPieces().total() : -1),
                () -> {
                    List<String> lignes = registre(registreAvantMinuit + 7);
                    if (lignes.size() != depenses || lignes.stream().anyMatch(l -> !"SPEND".equals(genre(l)) || !"Dev".equals(acteur(l)))
                            || somme(lignes) != reste || valeurPortee() != devPorteAvantCoffre + reste || lignes.isEmpty()
                            || !MOTIF_RESTE.equals(motif(lignes.get(0)))) {
                        throw new IllegalStateException("registre " + lignes + ", porte " + valeurPortee() + ", reste " + reste);
                    }
                    return "le maire depense le reste (" + reste + " cuivres) en " + depenses + " retrait(s), chacun note SPEND avec son motif ; Dev porte "
                            + valeurPortee();
                });
        pasD(120, "Il referme ; horloge réglée à 30 s de la frontière suivante : trésor vide, les salaires restent dus", () -> {
            fermer();
            Map<String, String> pa = paie();
            regleAvant = pa.get("regle");
            registreAvantMinuit = Integer.parseInt(tresorServeur().get("lignes"));
            CHAT.clear();
            reponse = rcon("e2etresor horloge frontiere 30");
            LOG.info("[e2e] horloge : {}", reponse);
            clicNs = System.nanoTime();
        }, () -> {
            if (System.nanoTime() - clicNs < 2_000_000_000L) {
                return false;
            }
            clicNs = System.nanoTime();
            return !regleAvant.equals(paie().get("regle"));
        }, () -> "pas de cloture : " + rcon("e2etresor paie") + " ; " + rcon("e2etresor horloge"), () -> {
            Map<String, String> pa = paie();
            List<String> lignes = registre(registreAvantMinuit);
            List<String> dettes = liste(pa.get("dettes"));
            du2 = Long.parseLong(pa.get("du"));
            String stats = rcon("eco stats");
            if (lignes.size() != 4 || deGenre(lignes, "SALARY_OWED").size() != 3 || deGenre(lignes, "DAY_CLOSED").size() != 1
                    || !"3".equals(pa.get("nombre")) || du2 <= 0 || somme(deGenre(lignes, "SALARY_OWED")) != du2
                    || !motif(deGenre(lignes, "DAY_CLOSED").get(0)).equals(regleAvantPlusUn() + " : 3 salaire(s)")
                    || !"0".equals(tresorServeur().get("total")) || !stats.contains("Wages owed:") || !stats.contains("(3)")) {
                throw new IllegalStateException("registre " + lignes + " ; paie " + pa + " ; stats " + stats);
            }
            return "second minuit, tresor vide : " + lignes + " ; dettes " + dettes + " (" + du2 + " cuivres) ; /eco stats : "
                    + stats.trim().replace('\n', '|');
        });
        pas("Le tableau affiche la dette ; Dev est prévenu de ses deux salaires dus", () -> {}, () -> ecoule() >= 1.0
                && new TownFinances.Snapshot(0, du2, 3).equals(TownFinancesCache.get()), () -> "cache " + TownFinancesCache.get() + ", chat " + CHAT,
                () -> {
                    List<String> avis = new ArrayList<>();
                    for (String d : liste(paie().get("dettes"))) {
                        String[] t = d.split(":");
                        if ("Dev".equals(t[1])) {
                            String attendu = tr("message.oneforallsurvival.salary.owed", t[3], CoinMath.format(Long.parseLong(t[4])));
                            if (chatContient(attendu) == 0) {
                                throw new IllegalStateException("avis absent « " + attendu + " » : " + CHAT);
                            }
                            avis.add(attendu);
                        }
                    }
                    if (avis.size() != 2) {
                        throw new IllegalStateException("dettes de Dev : " + avis);
                    }
                    return "tableau : cache " + TownFinancesCache.get() + " (« " + TownFinances.owedLine(TownFinancesCache.get()).getString()
                            + " ») ; Dev prevenu : " + avis;
                });
        pas("Dev redevient ingénieur et n'est plus maire ; Agent reste policier", () -> {
            rcon("role set " + nom() + " ingenieur");
            rcon("role mayor remove");
        }, () -> ecoule() >= 3.0, () -> "", () -> "roles : " + rcon("e2etresor maire").replace(" FIN_E2E", "") + " ; Agent "
                + agentEtat().get("role"));
    }

    private static String regleAvantPlusUn() {
        return LocalDate.parse(regleAvant).plusDays(1).toString();
    }

    // --------------------------------------------------------------------------------------------- déplacement

    private static void deplacement() {
        pasD(30, "Dev (ingénieur, non opérateur) casse le trésor au clic gauche tenu", () -> {
            registreAvantMinuit = Integer.parseInt(tresorServeur().get("lignes"));
            placerJoueur(p(6, 0, 2), 180, 30);
            tenirVide();
            mc.gameMode.startDestroyBlock(tresor(), Direction.SOUTH);
        }, () -> {
            if (mc.level.getBlockState(tresor()).isAir()) {
                return true;
            }
            mc.gameMode.continueDestroyBlock(tresor(), Direction.SOUTH);
            mc.player.swing(InteractionHand.MAIN_HAND);
            return false;
        }, () -> "bloc " + mc.level.getBlockState(tresor()) + " apres " + String.format("%.1f", ecoule()) + " s",
                () -> "tresor casse par l'ingenieur en " + String.format("%.1f", ecoule()) + " s");
        pas("Il ramasse l'objet du trésor ; le serveur n'a plus d'emplacement noté (ligne « Trésor retiré »)", () -> {
            mc.gameMode.stopDestroyBlock();
            placerJoueur(tresor(), 180, 30);
        }, () -> compter(EconomyRegistry.TREASURY_ITEM.get()) == 1 && ecoule() >= 1.5,
                () -> "objets " + compter(EconomyRegistry.TREASURY_ITEM.get()) + ", joueur en " + mc.player.blockPosition(), () -> {
                    Map<String, String> t = tresorServeur();
                    List<String> lignes = registre(registreAvantMinuit);
                    if (!"aucune".equals(t.get("position")) || !"0".equals(t.get("total")) || lignes.size() != 1
                            || !"REMOVED".equals(genre(lignes.get(0))) || !"Dev".equals(acteur(lignes.get(0)))) {
                        throw new IllegalStateException(t + " ; " + lignes);
                    }
                    return "objet du tresor ramasse ; serveur : " + t + " ; registre : " + lignes;
                });
        pas("Il repose le trésor ailleurs par un vrai clic droit", () -> {
            placerJoueur(tresorDeplace().south(3), 180, 30);
        }, () -> pres(tresorDeplace().south(3)) && ecoule() >= 1.5, () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Trésor reposé : même solde, registre complété d'une ligne « Trésor posé »", () -> {
            tenir(s -> s.is(EconomyRegistry.TREASURY_ITEM.get()));
            poserSur(tresorDeplace());
        }, () -> mc.level.getBlockState(tresorDeplace()).is(EconomyRegistry.TREASURY.get()) && ecoule() >= 1.5,
                () -> "bloc " + mc.level.getBlockState(tresorDeplace()), () -> {
                    Map<String, String> t = tresorServeur();
                    List<String> lignes = registre(registreAvantMinuit);
                    String pos = tresorDeplace().getX() + "," + tresorDeplace().getY() + "," + tresorDeplace().getZ();
                    if (!pos.equals(t.get("position")) || !"0".equals(t.get("total")) || lignes.size() != 2
                            || !"PLACED".equals(genre(lignes.get(1))) || !"Dev".equals(acteur(lignes.get(1)))
                            || compter(EconomyRegistry.TREASURY_ITEM.get()) != 0 || !"3".equals(paie().get("nombre"))) {
                        throw new IllegalStateException(t + " ; " + lignes);
                    }
                    return "tresor repose par l'ingenieur en " + tresorDeplace().toShortString() + " : solde " + t.get("total") + " (identique), "
                            + t.get("lignes") + " lignes au registre, les dernieres : " + lignes + " ; dettes inchangees";
                });
    }

    // --------------------------------------------------------------------------------------------- comptes

    private static void comptesEtArret() {
        pas("Dev range ce qu'il porte dans son coffre-fort", () -> {
            airePrete = false;
            rcon("tp " + nom() + " " + (coffreDev.getX() + 0.5) + " " + coffreDev.getY() + " " + (coffreDev.getZ() + 2.5) + " 180 40");
        }, () -> ecoule() >= 2 && pres(coffreDev.south(2)) && mc.level.getBlockState(coffreDev).is(EconomyRegistry.SAFE.get()),
                () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Clic droit sur son coffre-fort", () -> {
            tenirVide();
            clic(coffreDev, Direction.SOUTH);
        }, () -> ecranOuvert(SafeMenu.class) && ecoule() >= 1.0, () -> "ecran " + mc.screen, null);
        pas("Clic sur « Déposer tout »", () -> {
            totalAvantClic = menuPieces().total();
            clicElement("deposit_all", 0);
        }, () -> ecranOuvert(SafeMenu.class) && valeurPortee() == 0 && menuPieces().total() == totalAvantClic + devPorteAvantCoffre + reste
                && ecoule() >= 0.5, () -> "porte " + valeurPortee() + ", coffre-fort " + menuPieces().total(),
                () -> "Dev range " + (devPorteAvantCoffre + reste) + " cuivres : coffre-fort " + menuPieces().total() + ", il ne porte plus rien");
        pas("/eco stats : en circulation = avant + 500 (injection) + la somme de départ d'Agent ; transferts attendus", () -> {
            fermer();
            reponse = rcon("eco stats");
        }, () -> mc.screen == null && ecoule() >= 0.5, () -> "", () -> {
            Map<String, String> c = comptes();
            coffreArret = coffreDe("Dev");
            agentArret = Long.parseLong(agentEtat().get("porte"));
            long payeAAgent = payeAuMinuit - payeADev;
            Map<String, Long> attendus = new LinkedHashMap<>();
            attendus.put("circulation", compte(comptesAvant, "circulation") + INJECTION + agentEntree);
            attendus.put("treasury_deposit", compte(comptesAvant, "treasury_deposit") + 200);
            attendus.put("treasury_spend", compte(comptesAvant, "treasury_spend") + 100 + reste);
            attendus.put("salary", compte(comptesAvant, "salary") + payeAuMinuit);
            attendus.put("tresor", 0L);
            attendus.put("attente", compte(comptesAvant, "attente"));
            attendus.put("coffres", compte(comptesAvant, "coffres") - 200 + 100 + reste + payeADev);
            for (Map.Entry<String, Long> e : attendus.entrySet()) {
                if (compte(c, e.getKey()) != e.getValue()) {
                    throw new IllegalStateException(e.getKey() + " attendu " + e.getValue() + " : " + c);
                }
            }
            if (coffreArret != coffreAvant - 200 + 100 + reste + payeADev || agentArret != agentEntree + payeAAgent
                    || (coffreArret - coffreAvant) + (agentArret - agentEntree) != INJECTION) {
                throw new IllegalStateException("coffre-fort " + coffreAvant + " -> " + coffreArret + ", Agent " + agentEntree + " -> " + agentArret);
            }
            return "/eco stats : " + reponse.trim().replace('\n', '|') + " ; comptes " + c + " ; les " + INJECTION
                    + " injectes sont tous chez les joueurs : coffre-fort de Dev +" + (coffreArret - coffreAvant) + ", Agent +" + (agentArret - agentEntree)
                    + " ; tresor 0, " + du2 + " cuivres de salaires dus (une dette n'est pas de l'argent)";
        });
        pas("Références de relance réécrites (monnaie, commerce, trésor) ; statut de maire et rôle de Dev rendus", () -> {
            if (!"aucun".equals(maireAvant)) {
                rcon("role mayor set " + maireAvant);
            }
            rcon("role set " + nom() + " " + roleDevAvant);
            String stats = rcon("eco stats").trim().replace('\n', '|');
            String ledger = rcon("eco ledger").split("\n")[0].trim();
            E2eCommerce.noterStatsAvantArret(mc, stats);
            E2eMonnaie.noterCoffreAvantArret(mc, coffreArret, stats, ledger);
            reponse = stats;
        }, () -> ecoule() >= 1.0, () -> "", () -> "references de relance : stats " + reponse + " ; coffre-fort de Dev " + coffreArret
                + " ; maire rendu (" + rcon("e2etresor maire").replace(" FIN_E2E", "") + ")");
        pasD(40, "Agent reste connecté sous son rôle de policier (temps du jour en cours) ; état d'avant l'arrêt écrit", () -> {
            placerJoueur(p(6, 0, 4), 180, 10);
            clicNs = System.nanoTime();
        }, () -> System.nanoTime() - clicNs > 20_000_000_000L && pres(p(6, 0, 4)), () -> "joueur en " + mc.player.blockPosition(), () -> {
            Map<String, String> h = lire(rcon("e2etresor horloge"));
            Map<String, String> pa = paie();
            Map<String, String> t = tresorServeur();
            List<String> lignes = registre(0);
            jourArret = pa.get("jour");
            long relance = Long.parseLong(h.get("minutes")) + DEUX_JOURS_MIN;
            List<String> fichier = new ArrayList<>();
            fichier.add("origine=" + xyz(o));
            fichier.add("depart=" + xyz(depart));
            fichier.add("relance=" + relance);
            fichier.add("jourarret=" + jourArret);
            fichier.add("regle=" + pa.get("regle"));
            fichier.add("dettes=" + pa.get("dettes"));
            fichier.add("du=" + pa.get("du"));
            fichier.add("temps=" + pa.get("temps"));
            fichier.add("reel=" + pa.get("reel"));
            fichier.add("tresor=" + t.get("position") + " " + t.get("total"));
            fichier.add("registre=" + String.join(" || ", lignes));
            fichier.add("agent=" + agentArret);
            fichier.add("coffre=" + coffreArret);
            try {
                Files.write(fichier(), fichier, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            String temps = pa.get("temps");
            if (!temps.contains(AGENT + ":policier:")) {
                throw new IllegalStateException("aucun temps compte pour Agent aujourd'hui : " + pa);
            }
            LOG.info("[e2e] horloge pour la relance : -Pe2eHorloge={}", relance);
            return "avant l'arret : jour " + jourArret + ", temps compte du jour " + temps + ", dettes " + pa.get("dettes") + ", tresor " + t
                    + " ; relance a lancer avec -Pe2eHorloge=" + relance + " (decalage " + h.get("minutes") + " min + deux jours)";
        });
        pas("Le joueur rentre à son point de départ et redevient opérateur", () -> {
            rcon("gamerule doMobSpawning true");
            rcon("op " + nom());
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 1.5 && operateur() && mc.player.blockPosition().distSqr(depart) < 9,
                () -> "joueur en " + mc.player.blockPosition() + ", operateur " + operateur(), () -> "op par RCON, hasPermissions(2) = " + operateur());
    }

    // ================================================================================================
    // Après un arrêt et une relance du serveur (-Pe2eHorloge : deux jours de plus)
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

    /** Les dettes de la relance qui ne sont pas d'avant l'arrêt, attendues du temps compté noté avant l'arrêt. */
    private static final List<String> DETTES_ATTENDUES = new ArrayList<>();

    static void relance() {
        Map<String, String> lu = new LinkedHashMap<>();
        pas("Relance : état d'avant l'arrêt relu ; l'horloge de l'économie a deux jours d'avance", () -> {
            lu.putAll(lireFichier());
            o = position(lu.get("origine"));
            depart = position(lu.get("depart"));
            coffreDev = E2eMonnaie.coffreFortNote(mc);
            fermer();
            CHAT.clear();
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 10.5) + " " + (o.getY() + 5) + " " + (o.getZ() + 4.5));
        }, () -> ecoule() >= 4 && chunksPrets(p(10, 0, 4)), () -> "chunks non recus", () -> {
            Map<String, String> h = lire(rcon("e2etresor horloge"));
            LocalDate arret = LocalDate.parse(lu.get("jourarret"));
            if (!lu.get("relance").equals(h.get("minutes")) || !arret.plusDays(2).toString().equals(h.get("jour"))) {
                throw new IllegalStateException("horloge " + h + " ; attendu " + lu.get("relance") + " min, jour " + arret.plusDays(2)
                        + " (lancer runServer -Pe2eHorloge=" + lu.get("relance") + ")");
            }
            return "relance du tresor : horloge decalee de " + h.get("minutes") + " min (-Pe2eHorloge), jour de paie " + h.get("jour")
                    + " (deux de plus que " + arret + ")";
        });
        pas("Relance : Dev devant le trésor, en survie", () -> {
            placerJoueur(tresorDeplace().south(3), 180, 20);
            rcon("gamemode survival " + nom());
        }, () -> ecoule() >= 2 && pres(tresorDeplace().south(3)) && mc.level.getBlockState(tresorDeplace()).is(EconomyRegistry.TREASURY.get()),
                () -> "joueur en " + mc.player.blockPosition() + ", bloc " + mc.level.getBlockState(tresorDeplace()), () -> {
                    airePrete = true;
                    return "relance : le bloc du tresor est relu en " + tresorDeplace().toShortString();
                });
        pas("Relance : le rattrapage du démarrage a clos deux jours ; trésor, emplacement et registre relus", () -> {}, () -> ecoule() >= 0.3,
                () -> "", () -> {
                    LocalDate arret = LocalDate.parse(lu.get("jourarret"));
                    Map<String, String> t = tresorServeur();
                    List<String> avant = List.of(lu.get("registre").split(" \\|\\| "));
                    List<String> lignes = registre(0);
                    if (!(t.get("position") + " " + t.get("total")).equals(lu.get("tresor")) || lignes.size() < avant.size()
                            || !lignes.subList(0, avant.size()).equals(avant)) {
                        throw new IllegalStateException("tresor " + t + " (avant " + lu.get("tresor") + ") ; registre " + lignes + " ; avant " + avant);
                    }
                    // Les dettes attendues du jour de l'arrêt, d'après le temps compté noté avant l'arrêt : celles de Dev
                    // exactes (il n'était plus payé), celle d'Agent au moins ce qui était compté, au plus ce que le temps
                    // écoulé jusqu'à maintenant a pu ajouter.
                    long ecouleS = (System.currentTimeMillis() - Long.parseLong(lu.get("reel"))) / 1000L + 1L;
                    DETTES_ATTENDUES.clear();
                    long minimum = 0;
                    long maximum = 0;
                    for (String tps : liste(lu.get("temps"))) {
                        String[] x = tps.split(":");
                        long cs = Long.parseLong(x[3]);
                        long bas = cs / 3600L;
                        long haut = AGENT.equals(x[0]) ? (cs + ecouleS * TAUX_ESSAI) / 3600L : bas;
                        if (haut > 0) {
                            DETTES_ATTENDUES.add(x[0] + ":" + x[1] + ":" + bas + ":" + haut);
                            minimum += bas;
                            maximum += haut;
                        }
                    }
                    List<String> nouvelles = lignes.subList(avant.size(), lignes.size());
                    List<String> dus = deGenre(nouvelles, "SALARY_OWED");
                    List<String> clos = deGenre(nouvelles, "DAY_CLOSED");
                    boolean ok = clos.size() == 2 && nouvelles.size() == dus.size() + 2
                            && motif(clos.get(0)).equals(arret + " : " + dus.size() + " salaire(s)") && montant(clos.get(0)) == somme(dus)
                            && motif(clos.get(1)).equals(arret.plusDays(1) + " : 0 salaire(s)") && montant(clos.get(1)) == 0
                            && genre(nouvelles.get(nouvelles.size() - 1)).equals("DAY_CLOSED")
                            && genre(nouvelles.get(nouvelles.size() - 2)).equals("DAY_CLOSED")
                            && somme(dus) >= minimum && somme(dus) <= maximum && !dus.isEmpty();
                    for (String d : dus) {
                        String cle = acteur(d) + ":" + motif(d).split(" ")[1];
                        boolean vue = DETTES_ATTENDUES.stream().anyMatch(a -> {
                            String[] x = a.split(":");
                            return (x[0] + ":" + x[1]).equals(cle) && montant(d) >= Long.parseLong(x[2]) && montant(d) <= Long.parseLong(x[3]);
                        });
                        ok &= vue && motif(d).endsWith(arret.toString()) && montant(d) > 0;
                    }
                    if (!ok) {
                        throw new IllegalStateException("lignes du rattrapage " + nouvelles + " ; attendues " + DETTES_ATTENDUES);
                    }
                    return "relance : tresor relu (" + t + "), " + avant.size() + " lignes d'avant l'arret identiques ; le rattrapage du demarrage a clos "
                            + arret + " et " + arret.plusDays(1) + " : " + nouvelles + " (temps compte avant l'arret : " + lu.get("temps")
                            + ", le temps du jour a survecu a l'arret)";
                });
        pas("Relance : les dettes d'avant l'arrêt sont relues dans le même ordre ; le tableau les affiche", () -> {
            placerJoueur(tableau().south(3), 180, 5);
        }, () -> ecoule() >= 2.0 && pres(tableau().south(3)) && TownFinancesCache.get() != null, () -> "cache " + TownFinancesCache.get(), () -> {
            Map<String, String> pa = paie();
            List<String> dettes = liste(pa.get("dettes"));
            List<String> avant = liste(lu.get("dettes"));
            long du = Long.parseLong(pa.get("du"));
            if (dettes.size() < avant.size() || !dettes.subList(0, avant.size()).equals(avant)
                    || !new TownFinances.Snapshot(0, du, dettes.size()).equals(TownFinancesCache.get())) {
                throw new IllegalStateException("dettes " + dettes + " (avant " + avant + ") ; cache " + TownFinancesCache.get());
            }
            return "relance : dettes " + dettes + " (les " + avant.size() + " d'avant l'arret en tete, memes identifiants) ; tableau : cache "
                    + TownFinancesCache.get() + " (« " + TownFinances.owedLine(TownFinancesCache.get()).getString() + " »)";
        });
        pas("Relance : clic droit sur le tableau : la dette dans le chat", () -> {
            CHAT.clear();
            tenirVide();
            clic(tableau().above(), Direction.SOUTH);
        }, () -> ecoule() >= 1.0 && chatContient(tr("gui.oneforallsurvival.town_board.title")) > 0, () -> "chat " + CHAT, () -> {
            String attendu = TownFinances.describe(TownFinancesCache.get()).getString();
            if (CHAT.stream().noneMatch(attendu::equals)) {
                throw new IllegalStateException("chat " + CHAT + ", attendu « " + attendu + " »");
            }
            return "relance : le tableau dit « " + attendu.replace('\n', '|') + " »";
        });
        Map<String, String> avantPaie = new LinkedHashMap<>();
        pas("Relance : l'équipe injecte 500 au trésor (console) : la file est payée par ancienneté au tick suivant", () -> {
            Map<String, String> pa = paie();
            avantPaie.clear();
            avantPaie.putAll(pa);
            avantPaie.put("lignes", tresorServeur().get("lignes"));
            avantPaie.put("coffre", "" + coffreDe("Dev"));
            avantPaie.put("attente", lire(rcon("e2etresor attente " + AGENT)).get("valeur"));
            avantPaie.put("circulation", "" + compte(comptes(), "circulation"));
            if (Long.parseLong(pa.get("du")) > INJECTION) {
                throw new IllegalStateException("dette " + pa.get("du") + " au-dela de l'injection de l'essai");
            }
            CHAT.clear();
            reponse = rcon("eco treasury give " + INJECTION + " Paie des salaires dus");
        }, () -> ecoule() >= 1.0 && "0".equals(paie().get("nombre")), () -> "paie " + rcon("e2etresor paie"), () -> {
            List<String> dettes = liste(avantPaie.get("dettes"));
            List<String> lignes = registre(Integer.parseInt(avantPaie.get("lignes")));
            long aDev = 0;
            long aAgent = 0;
            boolean ok = lignes.size() == dettes.size() + 1 && "INJECTION".equals(genre(lignes.get(0)));
            for (int i = 0; ok && i < dettes.size(); i++) {
                String[] d = dettes.get(i).split(":");
                String l = lignes.get(i + 1);
                ok = "SALARY_PAID".equals(genre(l)) && d[1].equals(acteur(l)) && ("salaire " + d[2] + " " + d[3]).equals(motif(l))
                        && Long.parseLong(d[4]) == montant(l);
                if ("Dev".equals(d[1])) {
                    aDev += Long.parseLong(d[4]);
                } else {
                    aAgent += Long.parseLong(d[4]);
                }
            }
            long coffre = coffreDe("Dev");
            long attente = Long.parseLong(lire(rcon("e2etresor attente " + AGENT)).get("valeur"));
            long du = Long.parseLong(avantPaie.get("du"));
            long nDev = dettes.stream().filter(d -> d.split(":")[1].equals("Dev")).count();
            if (!ok || coffre != Long.parseLong(avantPaie.get("coffre")) + aDev || attente != Long.parseLong(avantPaie.get("attente")) + aAgent
                    || Long.parseLong(tresorServeur().get("total")) != INJECTION - du
                    || compte(comptes(), "circulation") != Long.parseLong(avantPaie.get("circulation")) + INJECTION
                    || chatContient(tr("message.oneforallsurvival.salary.to.safe")) != nDev) {
                throw new IllegalStateException("registre " + lignes + " ; dettes " + dettes + " ; coffre-fort " + avantPaie.get("coffre") + " -> " + coffre
                        + " ; attente d'Agent " + avantPaie.get("attente") + " -> " + attente + " ; tresor " + tresorServeur() + " ; chat " + CHAT);
            }
            avantPaie.put("aAgent", "" + aAgent);
            return "/eco treasury give " + INJECTION + " : " + reponse.trim() + " ; registre : " + lignes + " (INJECTION puis les salaires dans l'ordre"
                    + " des dettes) ; coffre-fort de Dev " + avantPaie.get("coffre") + " -> " + coffre + " (+" + aDev + ", " + nDev + " avis au chat) ; "
                    + "Agent absent : " + aAgent + " cuivres en attente ; tresor " + (INJECTION - du);
        });
        pas("Relance : Agent se connecte : il reçoit son attente et l'avis des salaires versés pendant son absence", () -> {
            reponse = rcon("e2etresor agent entrer " + xyz(placeAgent()));
        }, () -> ecoule() >= 1.0, () -> "", () -> {
            Map<String, String> r = lire(reponse);
            long aAgent = Long.parseLong(avantPaie.get("aAgent"));
            String connexion = r.getOrDefault("connexion", "");
            if (!("" + aAgent).equals(r.get("attenteAvant")) || !"0".equals(r.get("attenteApres"))
                    || Long.parseLong(r.get("porte")) != Long.parseLong(lu.get("agent")) + aAgent
                    || !connexion.contains("message.oneforallsurvival.salary.absent_paid")
                    || !connexion.contains("message.oneforallsurvival.commerce.delivered")) {
                throw new IllegalStateException(reponse + " ; Agent portait " + lu.get("agent") + " avant l'arret");
            }
            return "relance : Agent revient, recoit " + aAgent + " cuivres (il porte " + lu.get("agent") + " -> " + r.get("porte")
                    + ", aucune nouvelle somme de depart) et les avis " + connexion + " : « " + r.get("textes") + " »";
        });
        pas("Relance : rangement (Agent sort, taux et rôles rendus, aires de la monnaie et du trésor vidées)", () -> {
            rcon("role set " + AGENT + " citoyen");
            rcon("e2etresor agent quitter");
            rcon("eco salary set policier 60");
            rcon("eco salary set mayor 0");
            airePrete = false;
            rcon("fill " + xyz(p(-4, 0, -8)) + " " + xyz(p(30, 8, 20)) + " air");
            E2eMonnaie.rangerAire(mc);
            rcon("gamerule doMobSpawning true");
            rcon("op " + nom());
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
        }, () -> ecoule() >= 2 && mc.player.blockPosition().distSqr(depart) < 9 && operateur(),
                () -> "joueur en " + mc.player.blockPosition() + ", operateur " + operateur(),
                () -> "aires videes, taux rendus (" + rcon("eco salary list").replace('\n', '|').trim() + "), Dev operateur");
    }

    // ================================================================================================
    // Arrêt
    // ================================================================================================

    /**
     * Avant le {@code /stop}, même après un échec : Dev rendu opérateur. Au premier passage, Agent reste connecté à l'arrêt
     * (son temps du jour doit survivre à la relance) ; à la relance, il sort et les taux sont rendus.
     */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null || o == null) {
            return;
        }
        mc = minecraft;
        try {
            rcon("op " + nom());
            rcon("gamerule doMobSpawning true");
            if (System.getProperty("oneforallsurvival.e2e.relance") != null) {
                rcon("e2etresor agent quitter");
                rcon("eco salary set policier 60");
                rcon("eco salary set mayor 0");
            }
        } catch (RuntimeException e) {
            LOG.error("[e2e] rcon impossible avant l'arret", e);
        }
    }
}
