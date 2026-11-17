package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.SafeMenu;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket;
import dev.oneforallsurvival.economy.quest.client.QuestClientCache;
import dev.oneforallsurvival.economy.quest.client.QuestKeys;
import dev.oneforallsurvival.economy.quest.client.QuestScreen;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout;
import dev.oneforallsurvival.economy.quest.client.QuestToast;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Étape « quêtes » de {@link E2eClient} (développement seulement, jamais empaquetée) : le lot E5 vu par un vrai client
 * contre un serveur dédié avec le pack complet. Elle suit l'étape du butin (E4) et précède celle du trésor (E3) au
 * premier passage, pour que la réécriture des références d'E1 et d'E2 faite à la fin d'E3 compte déjà les pièces des
 * quêtes ; à la relance, elle passe en dernier (ses réclamations et son saut d'horloge au lundi viennent après tous les
 * contrôles exacts des autres étapes). Elle ne compare que la ligne « Quêtes » de {@code /eco stats} et
 * {@code /eco quests}.
 *
 * <p>Une aire close et couverte, loin de tout (à 640 blocs du départ, à l'opposé de celle du butin). Les blocs naturels
 * (pierre, bûches) y sont posés par la console ({@code fill}, {@code setblock} : sans joueur, donc sans marque). Dev,
 * en survie et sans droits, ouvre l'écran par la vraie touche J (évènements du clavier passés au gestionnaire du jeu),
 * casse et pose par de vrais clics, cuisine au Cooking Pot de Farmer's Delight, clique « Réclamer », « Relancer » et
 * « Réclamer l'attente » dans l'écran. Les quêtes à prouver sont imposées par {@code e2equetes imposer} (côté serveur :
 * {@link E2eQuetesServeur}) ; le carnet est lu par {@code e2equetes carnet}.
 *
 * <p>Disposition (depuis O, intérieur de (0, 0, 0) à (12, 3, 8)) : rangée z = 1 : rallonge, piston mécanique de Create,
 * granite (puis sa case d'arrivée), bûches, Cooking Pot sur un bloc de magma ; z = 2 : moteur ; z = 3 : allée où se
 * tient Dev ; z = 5 : pierres naturelles, témoin, pierres et planches posées par Dev.
 */
final class E2eQuetes {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-quetes.txt";
    private static final double DELAI = 25;
    private static final int Y_AIRE = 150;
    private static final Pattern CLE_VALEUR = Pattern.compile("(\\w+)=(\\[[^\\]]*\\]|[^ ]+)");

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai) {}

    /** Une quête telle que l'écrit {@code e2equetes carnet} : {@code modèle:progression/but:récompense:réclamée[:origine:clé]}. */
    private record Q(String modele, int progres, int but, long recompense, boolean reclamee, String origine, String cle) {
        static Q lire(String s) {
            String[] t = s.split(":");
            String[] pb = t[1].split("/");
            return new Q(t[0], Integer.parseInt(pb[0]), Integer.parseInt(pb[1]), Long.parseLong(t[2]), Boolean.parseBoolean(t[3]),
                    t.length > 4 ? t[4] : "", t.length > 5 ? t[5] : "");
        }

        @Override
        public String toString() {
            return modele + " " + progres + "/" + but + " (" + recompense + " cuivres" + (reclamee ? ", reclamee" : "") + ")";
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
    private static Map<String, String> registreAvant = Map.of();
    private static long porteAvant;
    private static long coffreAvant;
    private static String modeleRelance;
    private static String toastVu;
    private static long recompenseAttendue;
    private static List<Q> attenteAvant = List.of();

    private E2eQuetes() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape des quêtes";
    }

    // ================================================================================================
    // Disposition
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static String boite() {
        return xyz(p(-1, -1, -1)) + " " + xyz(p(13, 4, 9));
    }

    private static BlockPos allee(int x) {
        return p(x, 0, 3);
    }

    private static BlockPos pierre(int i) {
        return p(2 + i, 0, 5);
    }

    private static BlockPos temoin() {
        return p(6, 0, 5);
    }

    private static BlockPos pierreAbus() {
        return p(8, 0, 5);
    }

    private static BlockPos pierreGardee() {
        return p(9, 0, 5);
    }

    private static BlockPos plancheCycle() {
        return p(10, 0, 5);
    }

    private static BlockPos plancheGardee(int i) {
        return p(11 + i, 0, 5);
    }

    private static BlockPos buche(int i) {
        return p(8 + i, 0, 1);
    }

    private static BlockPos piston() {
        return p(3, 0, 1);
    }

    private static BlockPos graniteDepart() {
        return p(4, 0, 1);
    }

    private static BlockPos graniteArrivee() {
        return p(5, 0, 1);
    }

    private static BlockPos moteur() {
        return p(3, 0, 2);
    }

    private static BlockPos marmite() {
        return p(12, 0, 1);
    }

    // ================================================================================================
    // Déroulement (même mécanique que les étapes de la monnaie, du commerce, du butin et du trésor)
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
        guetterToast();
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

    private static List<Q> quetes(String liste) {
        List<Q> out = new ArrayList<>();
        for (String s : sansCrochets(liste).split(";")) {
            if (!s.isBlank()) {
                out.add(Q.lire(s));
            }
        }
        return out;
    }

    private static Map<String, String> carnet() {
        return lire(rcon("e2equetes carnet " + nom()));
    }

    private static List<Q> journalieres() {
        return quetes(carnet().get("journalieres"));
    }

    private static List<Q> hebdo() {
        return quetes(carnet().get("hebdo"));
    }

    private static Map<String, String> registre() {
        return lire(rcon("e2equetes registre"));
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

    private static int compter(Item item) {
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

    private static String dernierMot(String texte) {
        String[] t = texte.trim().split("\\s+");
        return t[t.length - 1].toLowerCase(java.util.Locale.ROOT);
    }

    // ================================================================================================
    // L'écran des quêtes : touche J, état du client, boutons
    // ================================================================================================

    /** La vraie touche : un évènement du clavier passé au gestionnaire du jeu, comme GLFW le ferait. */
    private static void touche(int action) {
        int code = QuestKeys.OPEN.getKey().getValue();
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, GLFW.glfwGetKeyScancode(code), action, 0);
        LOG.info("[e2e] touche {} ({}) : {}", QuestKeys.OPEN.getKey().getName(), code, action == GLFW.GLFW_PRESS ? "appuyee" : "relachee");
    }

    private static QuestSyncPacket etat() {
        return QuestClientCache.get();
    }

    private static List<QuestSyncPacket.Entry> lignes(QuestRef.Kind genre) {
        List<QuestSyncPacket.Entry> out = new ArrayList<>();
        if (etat() != null) {
            for (QuestSyncPacket.Entry e : etat().quests()) {
                if (e.ref().kind() == genre) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    private static QuestSyncPacket.Entry ligne(QuestRef.Kind genre, int place) {
        return lignes(genre).stream().filter(e -> e.ref().index() == place).findFirst().orElse(null);
    }

    private static String decrire(QuestSyncPacket.Entry e) {
        return e == null ? "absente" : e.ref().templateId() + " " + e.progress() + "/" + e.goal() + " " + e.reward() + " " + e.state();
    }

    /** J appuyée puis relâchée (deux pas : le jeu lit la touche à chaque tic) ; l'écran s'ouvre au relâchement. */
    private static void ouvrirParJ(String quand) {
        pas(quand + "Dev appuie sur J (vraie touche)", () -> {
            fermer();
            touche(GLFW.GLFW_PRESS);
        }, () -> ecoule() >= 0.2, () -> "", null);
        pas(quand + "Dev relâche J : l'écran des quêtes s'ouvre", () -> touche(GLFW.GLFW_RELEASE),
                () -> mc.screen instanceof QuestScreen s && s.layout() != null && etat() != null && ecoule() >= 1.0,
                () -> "ecran " + mc.screen + ", etat " + etat(), () -> "ecran des quetes ouvert par la touche "
                        + QuestKeys.OPEN.getKey().getName() + " au relachement ; " + lignes(QuestRef.Kind.DAILY).size() + " journalieres, "
                        + lignes(QuestRef.Kind.WEEKLY).size() + " hebdomadaires sur la copie du client");
    }

    /** J dans l'écran le referme ; le relâchement qui suit ne le rouvre pas (une seconde d'observation). */
    private static void fermerParJ(String quand) {
        pas(quand + "Dev appuie sur J dans l'écran : il se referme", () -> touche(GLFW.GLFW_PRESS), () -> mc.screen == null && ecoule() >= 0.2,
                () -> "ecran " + mc.screen, null);
        pas(quand + "Dev relâche J : l'écran ne se rouvre pas", () -> touche(GLFW.GLFW_RELEASE), () -> ecoule() >= 1.0,
                () -> "", () -> {
                    if (mc.screen != null) {
                        throw new IllegalStateException("ecran rouvert au relachement : " + mc.screen);
                    }
                    return "J dans l'ecran le referme ; relachee, la touche ne le rouvre pas (aucun ecran 1 s apres)";
                });
    }

    private static QuestScreenLayout.Rect bouton(QuestRef.Kind genre, int place) {
        QuestScreen s = (QuestScreen) mc.screen;
        QuestScreenLayout.Layout l = s.layout();
        int section = genre == QuestRef.Kind.DAILY ? 0 : 1;
        List<QuestSyncPacket.Entry> rangees = lignes(genre);
        for (int r = 0; r < rangees.size(); r++) {
            if (rangees.get(r).ref().index() == place) {
                return l.sections().get(section).rows().get(r).button();
            }
        }
        return null;
    }

    private static void cliquer(QuestScreenLayout.Rect b, String quoi) {
        int x = b.x() + b.w() / 2;
        int y = b.y() + b.h() / 2;
        boolean pris = mc.screen.mouseClicked(x, y, 0);
        LOG.info("[e2e] clic gauche sur {} de l'ecran des quetes en ({}, {}) : {}", quoi, x, y, pris);
    }

    /** Une notification « Quête terminée » à l'écran ou en file : relevée à chaque tic, son titre gardé. */
    private static void guetterToast() {
        if (mc == null || toastVu != null) {
            return;
        }
        try {
            ToastComponent t = mc.getToasts();
            for (java.lang.reflect.Field f : ToastComponent.class.getDeclaredFields()) {
                if (!Collection.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                for (Object o : new ArrayList<>((Collection<?>) f.get(t))) {
                    Object toast = o;
                    if (!(o instanceof QuestToast)) {
                        java.lang.reflect.Method m = o.getClass().getDeclaredMethod("getToast");
                        m.setAccessible(true);
                        toast = m.invoke(o);
                    }
                    if (toast instanceof QuestToast q) {
                        java.lang.reflect.Field champ = QuestToast.class.getDeclaredField("quest");
                        champ.setAccessible(true);
                        toastVu = ((Component) champ.get(q)).getString();
                        LOG.info("[e2e] notification de quete vue ({}) : {}", f.getName(), toastVu);
                        return;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debug("[e2e] lecture des notifications impossible", e);
        }
    }

    // ================================================================================================
    // Les gestes répétés
    // ================================================================================================

    /** Dev, dans l'allée, en face du bloc (au nord ou au sud de l'allée). */
    private static void enFace(BlockPos bloc) {
        boolean sud = bloc.getZ() > allee(0).getZ();
        placerJoueur(allee(bloc.getX() - o.getX()), sud ? 0 : 180, 35);
    }

    private static Direction faceVers(BlockPos bloc) {
        return bloc.getZ() > allee(0).getZ() ? Direction.NORTH : Direction.SOUTH;
    }

    /** Dev casse le bloc au clic gauche tenu, l'outil en main. */
    private static void casser(String legende, Supplier<BlockPos> cible, Predicate<ItemStack> outil) {
        pas(legende + " : Dev se place devant", () -> enFace(cible.get()), () -> ecoule() >= 0.6
                && pres(allee(cible.get().getX() - o.getX())), () -> "joueur en " + mc.player.blockPosition(), null);
        pasD(20, legende, () -> {
            tenir(outil);
            mc.gameMode.startDestroyBlock(cible.get(), faceVers(cible.get()));
        }, () -> {
            BlockPos b = cible.get();
            if (mc.level.getBlockState(b).isAir()) {
                mc.gameMode.stopDestroyBlock();
                return true;
            }
            mc.gameMode.continueDestroyBlock(b, faceVers(b));
            mc.player.swing(InteractionHand.MAIN_HAND);
            return false;
        }, () -> "bloc " + mc.level.getBlockState(cible.get()), null);
    }

    /** Dev pose le bloc qu'il tient par un vrai clic droit sur le sol. */
    private static void poser(String legende, Supplier<BlockPos> cible, Item item, net.minecraft.world.level.block.Block bloc) {
        pas(legende + " : Dev se place devant", () -> enFace(cible.get()), () -> ecoule() >= 0.6
                && pres(allee(cible.get().getX() - o.getX())), () -> "joueur en " + mc.player.blockPosition(), null);
        pas(legende, () -> {
            tenir(s -> s.is(item));
            clic(cible.get().below(), Direction.UP);
        }, () -> mc.level.getBlockState(cible.get()).is(bloc) && ecoule() >= 0.5, () -> "bloc " + mc.level.getBlockState(cible.get()), null);
    }

    /** Le carnet relu après un geste : la quête {@code place} de la série vaut {@code attendu}. */
    private static void progresVaut(String legende, boolean journaliere, int place, int attendu, Supplier<String> texte) {
        pas(legende, () -> {}, () -> ecoule() >= 1.0 && (journaliere ? journalieres() : hebdo()).get(place).progres() == attendu,
                () -> "carnet " + (journaliere ? journalieres() : hebdo()), () -> texte.get() + " ; carnet : "
                        + (journaliere ? "journaliere " : "hebdomadaire ") + place + " = " + (journaliere ? journalieres() : hebdo()).get(place));
    }

    /** Dev, en survie, sans droits, reçoit ses outils et ses blocs. */
    private static void outils(String quand, String... dons) {
        pas(quand + "Dev, en survie et non opérateur, reçoit ses outils", () -> {
            rcon("gamemode survival " + nom());
            rcon("deop " + nom());
            for (String d : dons) {
                rcon("give " + nom() + " " + d);
            }
            placerJoueur(allee(7), 0, 20);
        }, () -> ecoule() >= 1.5 && !operateur() && compter(Items.DIAMOND_PICKAXE) >= 1 && pres(allee(7)),
                () -> "operateur " + operateur() + ", pioche " + compter(Items.DIAMOND_PICKAXE) + ", joueur en " + mc.player.blockPosition(),
                () -> {
                    airePrete = true;
                    return "Dev en survie (" + mc.gameMode.getPlayerMode() + "), operateur = " + operateur() + " ; recu " + String.join(", ", dons);
                });
    }

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

    private static void allerALAire(String quand) {
        pas(quand + "Le joueur, en spectateur, se rend à l'aire des quêtes (loin de tout ; les chunks se chargent)", () -> {
            fermer();
            rcon("gamemode spectator " + nom());
            rcon("tp " + nom() + " " + (o.getX() + 7.5) + " " + (o.getY() + 2) + " " + (o.getZ() + 3.5));
        }, () -> ecoule() >= 4 && chunksPrets(allee(7)), () -> "chunks de l'aire non recus, joueur en " + mc.player.blockPosition(),
                () -> "aire des quetes en " + o.toShortString() + " (joueur parti de " + depart.toShortString() + ") ; Dev porte "
                        + valeurPortee() + " cuivres");
    }

    // ================================================================================================
    // Premier passage
    // ================================================================================================

    static void premierePhase() {
        pas("Départ de l'étape des quêtes : position, inventaire, règles du monde relevés", () -> {
            fermer();
            depart = mc.player.blockPosition();
            inventaireAvant = inventaire();
            BlockPos spawn = mc.level.getSharedSpawnPos();
            int x = depart.getX() + 640;
            int z = depart.getZ() - 100;
            if (Math.abs(x - spawn.getX()) <= 40 && Math.abs(z - spawn.getZ()) <= 40) {
                z = depart.getZ() - 180;
            }
            o = new BlockPos(x, Y_AIRE, z);
            difficulteAvant = dernierMot(rcon("difficulty"));
            naissancesAvant = dernierMot(rcon("gamerule doMobSpawning"));
        }, () -> ecoule() >= 0.3, () -> "", () -> "depart " + depart.toShortString() + " ; inventaire " + inventaireAvant + " ; difficulte "
                + difficulteAvant + ", naissances " + naissancesAvant);
        allerALAire("");
        pas("Aire close et couverte (coque de pierre) ; pierres, bûches, piston de Create et Cooking Pot posés par la console", () -> {
            rcon("difficulty peaceful");
            rcon("gamerule doMobSpawning false");
            rcon("kill @e[type=!player,x=" + (o.getX() - 1) + ",y=" + (o.getY() - 1) + ",z=" + (o.getZ() - 1) + ",dx=14,dy=5,dz=10]");
            rcon("fill " + boite() + " stone hollow");
            for (int i = 0; i < 4; i++) {
                rcon("setblock " + xyz(pierre(i)) + " stone");
            }
            rcon("setblock " + xyz(temoin()) + " stone");
            for (int i = 0; i < 3; i++) {
                rcon("setblock " + xyz(buche(i)) + " oak_log");
            }
            rcon("setblock " + xyz(piston().west()) + " create:piston_extension_pole[facing=east]");
            rcon("setblock " + xyz(graniteDepart()) + " granite");
            rcon("setblock " + xyz(piston()) + " create:mechanical_piston[facing=east,axis_along_first=false]");
            rcon("setblock " + xyz(marmite().below()) + " magma_block");
            rcon("setblock " + xyz(marmite()) + " farmersdelight:cooking_pot");
            registreAvant = registre();
        }, () -> ecoule() >= 1.0 && mc.level.getBlockState(p(-1, 2, 4)).is(Blocks.STONE) && mc.level.getBlockState(p(6, 4, 4)).is(Blocks.STONE)
                && mc.level.getBlockState(buche(2)).is(Blocks.OAK_LOG) && mc.level.getBlockState(temoin()).is(Blocks.STONE)
                && !mc.level.getBlockState(marmite()).isAir() && !mc.level.getBlockState(piston()).isAir(),
                () -> "mur " + mc.level.getBlockState(p(-1, 2, 4)) + ", marmite " + mc.level.getBlockState(marmite()) + ", piston "
                        + mc.level.getBlockState(piston()), () -> {
                    String marques = rcon("e2equetes marque " + xyz(pierre(0))) + " ; " + rcon("e2equetes marque " + xyz(buche(0)));
                    if (marques.contains("pose=true")) {
                        throw new IllegalStateException("bloc pose par la console marque : " + marques);
                    }
                    return "aire close en " + boite() + " ; blocs de la console sans marque (" + marques.replace(" FIN_E2E", "")
                            + ") ; registre des quetes " + registreAvant;
                });
        pasD(40, "Le piston mécanique de Create pousse le granite (moteur créatif), puis s'arrête (moteur retiré) : la contraption repose le bloc",
                () -> rcon("setblock " + xyz(moteur()) + " create:creative_motor[facing=north]"),
                () -> !mc.level.getBlockState(graniteDepart()).is(Blocks.GRANITE) && ecoule() >= 3.0,
                () -> "granite au depart : " + mc.level.getBlockState(graniteDepart()), () -> {
                    rcon("setblock " + xyz(moteur()) + " air");
                    return "le piston mecanique est sorti (granite parti de sa case) ; moteur retire";
                });
        pasD(30, "La contraption s'est démontée : le granite est reposé une case plus loin, marqué « posé »", () -> {},
                () -> mc.level.getBlockState(graniteArrivee()).is(Blocks.GRANITE) && ecoule() >= 1.0,
                () -> "case d'arrivee : " + mc.level.getBlockState(graniteArrivee()), () -> {
                    String m = rcon("e2equetes marque " + xyz(graniteArrivee()));
                    if (!m.contains("pose=true") || !m.contains("bloc=granite")) {
                        throw new IllegalStateException("granite repose par la contraption : " + m);
                    }
                    return "granite repose par la contraption de Create en " + graniteArrivee().toShortString() + " : "
                            + m.replace(" FIN_E2E", "").trim() + " (mixin de Contraption.addBlocksToWorld applique sur serveur dedie)";
                });
        outils("", "minecraft:diamond_pickaxe 1", "minecraft:diamond_axe 1", "minecraft:stone 4", "minecraft:oak_planks 16",
                "minecraft:brown_mushroom 1", "minecraft:red_mushroom 1", "minecraft:bowl 1");

        // --- Lire ses quêtes : 3 + 5, distinctes, comparées au carnet du serveur.
        ouvrirParJ("");
        pas("L'écran montre 3 journalières et 5 hebdomadaires distinctes, celles du carnet du serveur", () -> {}, () -> ecoule() >= 0.3, () -> "", () -> {
            Map<String, String> c = carnet();
            List<Q> jour = quetes(c.get("journalieres"));
            List<Q> semaine = quetes(c.get("hebdo"));
            List<QuestSyncPacket.Entry> j = lignes(QuestRef.Kind.DAILY);
            List<QuestSyncPacket.Entry> h = lignes(QuestRef.Kind.WEEKLY);
            if (j.size() != 3 || h.size() != 5 || jour.size() != 3 || semaine.size() != 5) {
                throw new IllegalStateException("client " + j.size() + " + " + h.size() + " ; carnet " + c);
            }
            comparer(j, jour);
            comparer(h, semaine);
            if (new HashSet<>(j.stream().map(e -> e.ref().templateId()).toList()).size() != 3
                    || new HashSet<>(h.stream().map(e -> e.ref().templateId()).toList()).size() != 5) {
                throw new IllegalStateException("modeles en double : " + j.stream().map(E2eQuetes::decrire).toList() + " ; "
                        + h.stream().map(E2eQuetes::decrire).toList());
            }
            QuestScreenLayout.Layout l = ((QuestScreen) mc.screen).layout();
            return "ecran : 3 journalieres " + j.stream().map(E2eQuetes::decrire).toList() + ", 5 hebdomadaires "
                    + h.stream().map(E2eQuetes::decrire).toList() + " ; identiques au carnet (cles " + c.get("jour") + " / " + c.get("semaine")
                    + ") ; plafond « " + l.cap().full() + " » ; bas « " + l.footer().full() + " » ; " + l.sections().get(0).rows().size()
                    + " + " + l.sections().get(1).rows().size() + " lignes";
        });
        fermerParJ("");

        // --- Imposer.
        pas("Quêtes imposées par la console : journalière 0 = pierre × 4, journalière 1 = planches × 2, hebdomadaire 0 = bûches × 10, "
                + "hebdomadaire 1 = plat du Cooking Pot × 2", () -> {
            reponse = rcon("e2equetes imposer " + nom() + " daily 0 break_stone 4") + " | "
                    + rcon("e2equetes imposer " + nom() + " daily 1 place_planks 2") + " | "
                    + rcon("e2equetes imposer " + nom() + " weekly 0 break_logs 10") + " | "
                    + rcon("e2equetes imposer " + nom() + " weekly 1 cook_meals 2");
        }, () -> ecoule() >= 1.0 && "break_stone".equals(decrireModele(ligne(QuestRef.Kind.DAILY, 0)))
                && "cook_meals".equals(decrireModele(ligne(QuestRef.Kind.WEEKLY, 1))),
                () -> reponse + " ; client " + decrire(ligne(QuestRef.Kind.DAILY, 0)), () -> {
                    if (reponse.contains("REFUS")) {
                        throw new IllegalStateException(reponse);
                    }
                    return "imposees : " + reponse.replace(" FIN_E2E", "") + " ; le client a recu l'etat (journaliere 0 : "
                            + decrire(ligne(QuestRef.Kind.DAILY, 0)) + ")";
                });

        // --- Abus : poser puis casser ne fait rien avancer.
        casser("Dev casse le granite reposé par la contraption", E2eQuetes::graniteArrivee, s -> s.is(Items.DIAMOND_PICKAXE));
        progresVaut("Le granite déplacé par la contraption ne compte pas en casse", true, 0, 0,
                () -> "granite repose par une contraption de Create, casse par Dev : la casse n'avance pas");
        poser("Dev pose une pierre", E2eQuetes::pierreAbus, Items.STONE, Blocks.STONE);
        casser("Dev casse la pierre qu'il vient de poser", E2eQuetes::pierreAbus, s -> s.is(Items.DIAMOND_PICKAXE));
        pas("Poser puis casser une pierre : casse et pose inchangées", () -> {}, () -> ecoule() >= 1.0, () -> "", () -> {
            List<Q> j = journalieres();
            if (j.get(0).progres() != 0 || j.get(1).progres() != 0) {
                throw new IllegalStateException("carnet " + j);
            }
            return "pierre posee puis cassee par Dev (vrais clics) : casse " + j.get(0) + ", pose " + j.get(1);
        });
        for (int n = 1; n <= 10; n++) {
            int k = n;
            poser("Planche posée puis cassée aussitôt (" + k + "/10)", E2eQuetes::plancheCycle, Items.OAK_PLANKS, Blocks.OAK_PLANKS);
            casser("Planche cassée (" + k + "/10)", E2eQuetes::plancheCycle, s -> s.is(Items.DIAMOND_AXE));
        }
        progresVaut("Dix planches posées puis cassées : pose à 0", true, 1, 0, () -> "une planche posee puis cassee dix fois de suite");

        // --- Finir et réclamer.
        pas("Notifications oubliées avant les quatre pierres", () -> toastVu = null, () -> ecoule() >= 0.1, () -> "", null);
        for (int i = 0; i < 4; i++) {
            int k = i;
            casser("Dev casse une pierre naturelle (" + (k + 1) + "/4)", () -> pierre(k), s -> s.is(Items.DIAMOND_PICKAXE));
        }
        pas("La journalière 0 est finie : notification « Quête terminée » vue par le client", () -> {}, () -> ecoule() >= 0.5
                && journalieres().get(0).progres() == 4 && toastVu != null, () -> "carnet " + journalieres() + ", notification " + toastVu
                        + ", client " + decrire(ligne(QuestRef.Kind.DAILY, 0)), () -> {
                    String titre = ligne(QuestRef.Kind.DAILY, 0).title().getString();
                    if (!toastVu.equals(titre)) {
                        throw new IllegalStateException("notification « " + toastVu + " », titre de la quete « " + titre + " »");
                    }
                    return "4 pierres naturelles cassees : " + journalieres().get(0) + " ; notification « "
                            + Component.translatable("gui.oneforallsurvival.quests.toast").getString() + " » : « " + toastVu + " »";
                });
        ouvrirParJ("");
        pas("Vrai clic sur « Réclamer » de la journalière 0", () -> {
            porteAvant = valeurPortee();
            registreAvant = registre();
            recompenseAttendue = ligne(QuestRef.Kind.DAILY, 0).payable();
            QuestScreenLayout.Rect b = bouton(QuestRef.Kind.DAILY, 0);
            if (b == null) {
                throw new IllegalStateException("aucun bouton sur la journaliere 0 : " + decrire(ligne(QuestRef.Kind.DAILY, 0)));
            }
            cliquer(b, "Reclamer (journaliere 0)");
        }, () -> ecoule() >= 1.0 && ligne(QuestRef.Kind.DAILY, 0) != null
                && ligne(QuestRef.Kind.DAILY, 0).state() == QuestSyncPacket.State.CLAIMED && valeurPortee() > porteAvant,
                () -> "client " + decrire(ligne(QuestRef.Kind.DAILY, 0)) + ", porte " + valeurPortee() + " (avant " + porteAvant + ")", () -> {
                    long gain = valeurPortee() - porteAvant;
                    Map<String, String> r = registre();
                    String stats = rcon("eco stats");
                    String q = rcon("eco quests");
                    long hausse = num(r, "total") - num(registreAvant, "total");
                    String ligneStats = sansCrochets(r.get("ligne"));
                    String ligneDev = sansCrochets(r.get("ligneDev"));
                    String ligneModele = sansCrochets(r.get("m_break_stone"));
                    if (gain != recompenseAttendue || hausse != gain || num(r, "reclamations") != num(registreAvant, "reclamations") + 1
                            || !stats.contains(ligneStats) || !q.contains(ligneDev) || ligneModele == null || !q.contains(ligneModele)) {
                        throw new IllegalStateException("gain " + gain + " (attendu " + recompenseAttendue + ") ; registre " + r + " (avant "
                                + registreAvant + ") ; stats " + stats.replace('\n', '|') + " ; quests " + q.replace('\n', '|'));
                    }
                    return "« Reclamer » clique : +" + gain + " cuivre(s) (" + fmt(gain) + ") dans la bourse ou l'inventaire de Dev ; « Quetes » de "
                            + "/eco stats : « " + ligneStats.trim() + " » (+" + hausse + ") ; /eco quests nomme Dev (« " + ligneDev.trim()
                            + " ») et le modele (« " + ligneModele.trim() + " ») : " + q.trim().replace('\n', '|');
                });

        // --- Relancer : une journalière en cours.
        pas("Vrai clic sur « Relancer » de la journalière 2 (en cours)", () -> {
            QuestSyncPacket.Entry e = ligne(QuestRef.Kind.DAILY, 2);
            if (e == null || e.state() != QuestSyncPacket.State.IN_PROGRESS || etat().rerollsLeft() < 1) {
                throw new IllegalStateException("journaliere 2 " + decrire(e) + ", relances " + (etat() == null ? "?" : etat().rerollsLeft()));
            }
            modeleRelance = e.ref().templateId();
            cliquer(bouton(QuestRef.Kind.DAILY, 2), "Relancer (journaliere 2)");
        }, () -> ecoule() >= 1.0 && etat().rerollsLeft() == 0 && ligne(QuestRef.Kind.DAILY, 2) != null
                && !modeleRelance.equals(ligne(QuestRef.Kind.DAILY, 2).ref().templateId()),
                () -> "client " + decrire(ligne(QuestRef.Kind.DAILY, 2)) + ", relances " + etat().rerollsLeft(), () -> {
                    QuestScreenLayout.Rect b = bouton(QuestRef.Kind.DAILY, 2);
                    List<Q> j = journalieres();
                    if (b != null || j.get(2).modele().equals(modeleRelance)) {
                        throw new IllegalStateException("bouton apres la relance " + b + " ; carnet " + j);
                    }
                    return "relance : " + modeleRelance + " -> " + decrire(ligne(QuestRef.Kind.DAILY, 2)) + " ; plus de relance aujourd'hui, "
                            + "plus de bouton « Relancer » sur les quetes en cours (carnet : relances " + carnet().get("relances") + ")";
                });
        fermerParJ("");

        // --- Laisser en attente : deux planches gardées, trois bûches de l'équipe.
        poser("Dev pose une planche et la garde (1/2)", () -> plancheGardee(0), Items.OAK_PLANKS, Blocks.OAK_PLANKS);
        poser("Dev pose une planche et la garde (2/2)", () -> plancheGardee(1), Items.OAK_PLANKS, Blocks.OAK_PLANKS);
        progresVaut("La journalière 1 est finie, non réclamée", true, 1, 2, () -> "deux planches posees et gardees");
        for (int i = 0; i < 3; i++) {
            int k = i;
            casser("Dev casse une bûche posée par la console (" + (k + 1) + "/3)", () -> buche(k), s -> s.is(Items.DIAMOND_AXE));
        }
        progresVaut("L'hebdomadaire 0 est à 3 / 10", false, 0, 3, () -> "trois buches posees par la console (sans joueur) cassees");

        // --- Cooking Pot de Farmer's Delight.
        pas("Dev se place devant le Cooking Pot (chauffé par un bloc de magma)", () -> enFace(marmite()),
                () -> ecoule() >= 0.6 && pres(allee(marmite().getX() - o.getX())), () -> "joueur en " + mc.player.blockPosition(), null);
        pas("Clic droit sur le Cooking Pot : son écran s'ouvre", () -> {
            tenirVide();
            clic(marmite(), Direction.SOUTH);
        }, () -> ecoule() >= 1.0 && mc.screen instanceof AbstractContainerScreen<?> s
                && s.getMenu().getClass().getSimpleName().equals("CookingPotMenu"), () -> "ecran " + mc.screen, null);
        pasD(40, "Dev met deux champignons et un bol (vrais clics d'inventaire) ; le plat cuit", () -> {
            AbstractContainerMenu m = ((AbstractContainerScreen<?>) mc.screen).getMenu();
            deplacer(m, Items.BROWN_MUSHROOM, 0);
            deplacer(m, Items.RED_MUSHROOM, 1);
            deplacer(m, Items.BOWL, 7);
        }, () -> mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu().getSlot(8).getItem().is(Items.MUSHROOM_STEW),
                () -> "ecran " + mc.screen + ", sortie " + (mc.screen instanceof AbstractContainerScreen<?> s ? s.getMenu().getSlot(8).getItem() : "?"),
                () -> "plat cuit au Cooking Pot en " + String.format(java.util.Locale.ROOT, "%.1f", ecoule()) + " s : ragout de champignons en sortie");
        pas("Dev prend le plat (clic majuscule sur la sortie)", () -> {
            AbstractContainerMenu m = ((AbstractContainerScreen<?>) mc.screen).getMenu();
            mc.gameMode.handleInventoryMouseClick(m.containerId, 8, 0, ClickType.QUICK_MOVE, mc.player);
        }, () -> ecoule() >= 1.0 && compter(Items.MUSHROOM_STEW) == 1 && hebdo().get(1).progres() == 1,
                () -> "ragouts " + compter(Items.MUSHROOM_STEW) + ", carnet " + hebdo(), () -> {
                    fermer();
                    return "cuisine au Cooking Pot de Farmer's Delight : hebdomadaire 1 = " + hebdo().get(1)
                            + " (mixin du menu du Cooking Pot applique sur serveur dedie)";
                });

        // --- Une pierre posée par Dev, laissée pour la relance ; état d'avant l'arrêt.
        poser("Dev pose une pierre et la laisse pour la relance", E2eQuetes::pierreGardee, Items.STONE, Blocks.STONE);
        pas("État d'avant l'arrêt écrit (carnet, ligne « Quêtes », /eco quests)", () -> {}, () -> ecoule() >= 1.0, () -> "", () -> {
            Map<String, String> c = carnet();
            Map<String, String> r = registre();
            String q = rcon("eco quests").trim().replace('\n', '|');
            String m = rcon("e2equetes marque " + xyz(pierreGardee()));
            List<Q> j = quetes(c.get("journalieres"));
            List<Q> h = quetes(c.get("hebdo"));
            if (!m.contains("pose=true") || j.get(1).progres() != 2 || j.get(1).reclamee() || h.get(0).progres() != 3) {
                throw new IllegalStateException("marque " + m + " ; carnet " + c);
            }
            List<String> lignes = new ArrayList<>();
            lignes.add("origine=" + xyz(o));
            lignes.add("jour=" + c.get("jour"));
            lignes.add("semaine=" + c.get("semaine"));
            lignes.add("planches=" + j.get(1).recompense());
            lignes.add("pierre=" + xyz(pierreGardee()));
            lignes.add("ligne=" + sansCrochets(r.get("ligne")));
            lignes.add("total=" + r.get("total"));
            lignes.add("reclamations=" + r.get("reclamations"));
            lignes.add("quests=" + q);
            try {
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return "avant l'arret : " + FICHIER + " ecrit : journalieres " + j + " ; hebdomadaires " + h + " ; attente " + c.get("attente")
                    + " ; pierre gardee " + m.replace(" FIN_E2E", "").trim() + " ; ligne « " + sansCrochets(r.get("ligne")).trim() + " » ; /eco quests " + q;
        });
        rangerAvantTresor();
    }

    private static String decrireModele(QuestSyncPacket.Entry e) {
        return e == null ? null : e.ref().templateId();
    }

    private static void comparer(List<QuestSyncPacket.Entry> client, List<Q> carnet) {
        for (int i = 0; i < client.size(); i++) {
            QuestSyncPacket.Entry e = client.get(i);
            Q q = carnet.get(e.ref().index());
            if (!q.modele().equals(e.ref().templateId()) || q.progres() != e.progress() || q.but() != e.goal() || q.recompense() != e.reward()) {
                throw new IllegalStateException("client " + decrire(e) + " ; carnet " + q);
            }
        }
    }

    /** Un vrai clic sur la pile de l'inventaire du joueur, puis un sur la case du Cooking Pot. */
    private static void deplacer(AbstractContainerMenu m, Item item, int caseMarmite) {
        for (Slot s : m.slots) {
            if (s.container == mc.player.getInventory() && s.getItem().is(item)) {
                mc.gameMode.handleInventoryMouseClick(m.containerId, s.index, 0, ClickType.PICKUP, mc.player);
                mc.gameMode.handleInventoryMouseClick(m.containerId, caseMarmite, 0, ClickType.PICKUP, mc.player);
                LOG.info("[e2e] {} pose dans la case {} du Cooking Pot", item, caseMarmite);
                return;
            }
        }
        throw new IllegalStateException(item + " absent de l'inventaire");
    }

    /** Dev quitte l'aire, range ses pièces dans son coffre-fort (l'étape du trésor part d'un Dev qui ne porte rien). */
    private static void rangerAvantTresor() {
        pas("Dev quitte l'aire des quêtes et va à son coffre-fort", () -> {
            airePrete = false;
            BlockPos coffre = E2eMonnaie.coffreFortNote(mc);
            rcon("tp " + nom() + " " + (coffre.getX() + 0.5) + " " + coffre.getY() + " " + (coffre.getZ() + 2.5) + " 180 40");
        }, () -> {
            BlockPos coffre = E2eMonnaie.coffreFortNote(mc);
            return ecoule() >= 2 && pres(coffre.south(2)) && mc.level.getBlockState(coffre).is(EconomyRegistry.SAFE.get());
        }, () -> "joueur en " + mc.player.blockPosition(), () -> "Dev devant son coffre-fort");
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
                () -> "Dev range " + porteAvant + " cuivres (la recompense de quete) : coffre-fort " + coffreAvant + " -> " + menuPieces().total());
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
        pas("Relance : état d'avant l'arrêt relu ; journalières renouvelées, la journalière finie attend ; ligne « Quêtes » et "
                + "/eco quests identiques", () -> {
            lu.putAll(lireFichier());
            fermer();
            o = position(lu.get("origine"));
            depart = mc.player.blockPosition();
            inventaireAvant = inventaire();
            difficulteAvant = dernierMot(rcon("difficulty"));
            naissancesAvant = dernierMot(rcon("gamerule doMobSpawning"));
        }, () -> ecoule() >= 1.5, () -> "", () -> {
            Map<String, String> c = carnet();
            List<Q> j = quetes(c.get("journalieres"));
            List<Q> h = quetes(c.get("hebdo"));
            List<Q> attente = quetes(c.get("attente"));
            Map<String, String> r = registre();
            String q = rcon("eco quests").trim().replace('\n', '|');
            String stats = rcon("eco stats");
            boolean jourNeuf = !c.get("jour").equals(lu.get("jour")) && c.get("jour").equals(c.get("aujourdhui")) && j.size() == 3;
            Q planches = attente.stream().filter(a -> a.modele().equals("place_planks") && a.progres() == 2 && !a.reclamee()
                    && "DAILY".equals(a.origine()) && a.cle().equals(lu.get("jour"))).findFirst().orElse(null);
            boolean memeSemaine = c.get("semaine").equals(lu.get("semaine"));
            boolean hebdoJuste = memeSemaine ? h.get(0).modele().equals("break_logs") && h.get(0).progres() == 3
                    : c.get("semaine").equals(c.get("cettesemaine")) && h.size() == 5
                            && attente.stream().noneMatch(a -> a.modele().equals("break_logs") || a.modele().equals("cook_meals"));
            if (!jourNeuf || planches == null || planches.recompense() != Long.parseLong(lu.get("planches")) || !hebdoJuste
                    || !q.equals(lu.get("quests")) || !sansCrochets(r.get("ligne")).equals(lu.get("ligne")) || !stats.contains(lu.get("ligne"))
                    || !r.get("total").equals(lu.get("total")) || !r.get("reclamations").equals(lu.get("reclamations"))) {
                throw new IllegalStateException("carnet " + c + " ; avant " + lu + " ; registre " + r + " ; quests " + q);
            }
            recompenseAttendue = planches.recompense();
            return "relance des quetes : journalieres renouvelees (cle " + lu.get("jour") + " -> " + c.get("jour") + ", " + j + ") ; en attente : "
                    + planches + " du " + planches.cle() + " ; " + (memeSemaine ? "meme semaine (" + c.get("semaine") + ") : hebdomadaire 0 gardee, "
                            + h.get(0) : "semaine changee (" + lu.get("semaine") + " -> " + c.get("semaine") + ") : 5 nouvelles hebdomadaires "
                            + h + ", l'ancienne a 3/10 expiree sans attente") + " ; ligne « " + lu.get("ligne").trim() + " » et /eco quests identiques ("
                    + q + ")";
        });
        allerALAire("Relance : ");
        pas("Relance : la pierre posée par Dev avant l'arrêt est toujours marquée (marque relue du chunk)", () -> {}, () -> ecoule() >= 0.3
                && mc.level.getBlockState(position(lu.get("pierre"))).is(Blocks.STONE), () -> "bloc " + mc.level.getBlockState(position(lu.get("pierre"))),
                () -> {
                    String m = rcon("e2equetes marque " + lu.get("pierre"));
                    if (!m.contains("pose=true")) {
                        throw new IllegalStateException(m);
                    }
                    return "relance : pierre de Dev en " + lu.get("pierre") + " : " + m.replace(" FIN_E2E", "").trim();
                });
        outils("Relance : ", "minecraft:diamond_pickaxe 1");
        pas("Relance : journalière 0 imposée (pierre × 4)", () -> reponse = rcon("e2equetes imposer " + nom() + " daily 0 break_stone 4"),
                () -> ecoule() >= 1.0 && journalieres().get(0).modele().equals("break_stone") && journalieres().get(0).progres() == 0,
                () -> reponse, () -> "imposee : " + reponse.replace(" FIN_E2E", "").trim());
        casser("Relance : Dev casse la pierre qu'il avait posée avant l'arrêt", E2eQuetes::pierreGardee, s -> s.is(Items.DIAMOND_PICKAXE));
        progresVaut("Relance : la pierre posée avant l'arrêt ne compte pas", true, 0, 0,
                () -> "pierre posee par Dev avant l'arret, cassee apres la relance : la casse n'avance pas");
        casser("Relance : Dev casse la pierre témoin (naturelle)", E2eQuetes::temoin, s -> s.is(Items.DIAMOND_PICKAXE));
        progresVaut("Relance : le témoin compte", true, 0, 1, () -> "temoin : une pierre naturelle cassee compte");
        ouvrirParJ("Relance : ");
        pas("Relance : vrai clic sur « Réclamer l'attente »", () -> {
            porteAvant = valeurPortee();
            registreAvant = registre();
            QuestScreenLayout.Layout l = ((QuestScreen) mc.screen).layout();
            if (l.pendingButton() == null || etat().pendingCount() < 1) {
                throw new IllegalStateException("aucun bouton d'attente ; attentes " + etat().pendingCount());
            }
            // Toute l'attente est réclamée d'un geste : d'autres quêtes finies par les étapes suivantes (au passage complet, la
            // marmite du lot 5 finit une journalière de cuisine tirée un jour avancé par l'horloge d'E3) attendent aussi.
            attenteAvant = quetes(carnet().get("attente"));
            recompenseAttendue = attenteAvant.stream().mapToLong(Q::recompense).sum();
            reponse = l.pendingLabel().full();
            cliquer(l.pendingButton(), "Reclamer l'attente");
        }, () -> ecoule() >= 1.0 && valeurPortee() > porteAvant && etat().pendingCount() == 0,
                () -> "porte " + valeurPortee() + " (avant " + porteAvant + "), attentes " + etat().pendingCount(), () -> {
                    long gain = valeurPortee() - porteAvant;
                    Map<String, String> r = registre();
                    String stats = rcon("eco stats");
                    String q = rcon("eco quests");
                    long hausse = num(r, "total") - num(registreAvant, "total");
                    String ligneModele = sansCrochets(r.get("m_place_planks"));
                    if (gain != recompenseAttendue || hausse != gain || num(r, "reclamations") != num(registreAvant, "reclamations") + attenteAvant.size()
                            || !stats.contains(sansCrochets(r.get("ligne"))) || ligneModele == null
                            || !q.contains(ligneModele) || quetes(carnet().get("attente")).stream().anyMatch(a -> a.modele().equals("place_planks"))) {
                        throw new IllegalStateException("gain " + gain + " (attendu " + recompenseAttendue + ") ; registre " + r + " (avant "
                                + registreAvant + ") ; quests " + q.replace('\n', '|'));
                    }
                    return "« " + reponse + " » clique : attente " + attenteAvant + " ; +" + gain + " cuivre(s) ; « Quetes » de /eco stats : « " + sansCrochets(r.get("ligne")).trim()
                            + " » (+" + hausse + ") ; /eco quests : « " + ligneModele.trim() + " »";
                });
        fermerParJ("Relance : ");
        pas("Relance : l'horloge passe au lundi suivant, 6 h + 1 min (e2equetes horloge lundi)", () -> {
            reponse = rcon("e2equetes horloge lundi");
        }, () -> {
            Map<String, String> c = carnet();
            return ecoule() >= 1.5 && c.get("semaine").equals(c.get("cettesemaine")) && c.get("jour").equals(c.get("aujourdhui"));
        }, () -> "carnet " + carnet() + " ; " + reponse, () -> {
            Map<String, String> c = carnet();
            Map<String, String> h = lire(reponse);
            if (!c.get("semaine").equals(h.get("semaine")) || !"0".equals(c.get("relances"))) {
                throw new IllegalStateException(reponse + " ; carnet " + c);
            }
            return "horloge : " + reponse.replace(" FIN_E2E", "").trim() + " ; carnet renouvele par le tic du serveur : semaine " + c.get("semaine")
                    + ", jour " + c.get("jour") + ", hebdomadaires " + c.get("hebdo") + ", plafond restant " + c.get("plafond");
        });
        ouvrirParJ("Relance, lundi : ");
        pas("Relance, lundi : 5 nouvelles hebdomadaires, plafond de la semaine à 0, relance disponible", () -> {}, () -> ecoule() >= 0.5
                && etat().capPaid() == 0 && lignes(QuestRef.Kind.WEEKLY).size() == 5,
                () -> "etat " + etat(), () -> {
                    Map<String, String> c = carnet();
                    List<QuestSyncPacket.Entry> h = lignes(QuestRef.Kind.WEEKLY);
                    String semaine = c.get("semaine");
                    boolean neuves = h.stream().allMatch(e -> semaine.equals(String.valueOf(e.ref().seriesKey())) && e.progress() == 0
                            && e.state() == QuestSyncPacket.State.IN_PROGRESS);
                    QuestScreenLayout.Layout l = ((QuestScreen) mc.screen).layout();
                    boolean relancePossible = etat().rerollsLeft() >= 1 && bouton(QuestRef.Kind.DAILY, 0) != null;
                    if (!neuves || new HashSet<>(h.stream().map(e -> e.ref().templateId()).toList()).size() != 5 || !relancePossible) {
                        throw new IllegalStateException("hebdomadaires " + h.stream().map(E2eQuetes::decrire).toList() + " ; relances "
                                + etat().rerollsLeft() + " ; carnet " + c);
                    }
                    return "lundi : 5 nouvelles hebdomadaires (semaine " + semaine + ") " + h.stream().map(E2eQuetes::decrire).toList()
                            + " ; plafond « " + l.cap().full() + " » ; " + etat().rerollsLeft() + " relance(s), bouton « Relancer » present ; bas « "
                            + l.footer().full() + " »";
                });
        fermerParJ("Relance, lundi : ");
        pas("Relance : horloge rendue, aire rangée, objets de l'étape retirés ; Dev rentre, opérateur", () -> {
            airePrete = false;
            reponse = rcon("e2equetes horloge rendre").replace(" FIN_E2E", "").trim();
            rcon("tp " + nom() + " " + (depart.getX() + 0.5) + " " + depart.getY() + " " + (depart.getZ() + 0.5));
            rcon("kill @e[type=!player,x=" + (o.getX() - 1) + ",y=" + (o.getY() - 1) + ",z=" + (o.getZ() - 1) + ",dx=14,dy=5,dz=10]");
            rcon("fill " + boite() + " air");
            String retires = rangerInventaire();
            rcon("difficulty " + difficulteAvant);
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("op " + nom());
            reponse = reponse + " ; retires " + retires;
        }, () -> ecoule() >= 1.5 && operateur() && mc.player.blockPosition().distSqr(depart) < 9, () -> "joueur en " + mc.player.blockPosition(),
                () -> "relance des quetes finie : " + reponse + " ; Dev garde les " + valeurPortee() + " cuivres portes");
    }

    // ================================================================================================
    // Arrêt
    // ================================================================================================

    /** Avant le {@code /stop}, même après un échec : joueur opérateur, règles du monde rendues. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null || o == null) {
            return;
        }
        mc = minecraft;
        try {
            rcon("op " + nom());
            rcon("gamerule doMobSpawning " + naissancesAvant);
            rcon("difficulty " + difficulteAvant);
        } catch (RuntimeException e) {
            LOG.error("[e2e] rcon impossible avant l'arret", e);
        }
    }
}
