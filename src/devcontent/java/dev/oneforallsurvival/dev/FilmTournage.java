package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import dev.oneforallsurvival.economy.client.StallCashScreen;
import dev.oneforallsurvival.economy.client.TradeScreen;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;

/**
 * Les briques communes des films montés ({@link FilmBandeAnnonce}, {@link FilmEconomie} ; développement seulement, jamais
 * empaquetées) : la liste des pas et leur déroulement, les plans filmés et la mise en place coupée, les clics et les
 * survols d'un curseur virtuel, le travelling de la caméra (joueur en spectateur), les gestes du joueur (clic droit,
 * objet en main, cases des menus) et les commandes jouées par le serveur. Un seul film tourne par JVM : l'état est
 * statique, chaque film le règle (origine {@link #o}, {@link #prise}, {@link #fin}).
 */
public final class FilmTournage {

    static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");

    /** Un pas : {@code tourne} faux = mise en place, non filmée ; {@code carton} non nul : un carton titre filmé. */
    record Etape(String nom, boolean tourne, String titre, Supplier<String> legende, Runnable action, BooleanSupplier pret,
            long reposMs, String[] carton) {}

    static final List<Etape> ETAPES = new ArrayList<>();
    static int indexEtape = -1;
    static boolean actionFaite;
    static long debutAction;
    static long debutRepos;
    static String titreCourant = "";
    static FilmPrise prise;
    static String dernierChat = "";
    /** Facteur des temps de pose de chaque pas (rythme du montage). */
    static double rythme = 1.0;
    /** Appelé quand le dernier pas est fini. */
    static Runnable fin = () -> {};
    /** Délai d'un pas qui ne finit pas, en secondes. */
    static double delaiPas = 30;

    /** Origine du décor. */
    static BlockPos o;

    // Mouvement de caméra (spectateur) et curseur virtuel.
    static Vec3 cameraDe;
    static Vec3 cameraA;
    static float[] regardDe;
    static float[] regardA;
    static long cameraDebutNs;
    static long cameraDureeNs;
    static double curX = 320;
    static double curY = 180;
    static double curDeX;
    static double curDeY;
    static double curAX = 320;
    static double curAY = 180;
    static long curDebutNs;
    static long curDureeNs = 1;

    private FilmTournage() {}

    // ================================================================================================
    // Outils
    // ================================================================================================

    static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    static Vec3 v(double dx, double dy, double dz) {
        return new Vec3(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
    }

    static MinecraftServer serveur(Minecraft mc) {
        return mc.getSingleplayerServer();
    }

    static ServerPlayer moiServeur(Minecraft mc) {
        return serveur(mc).getPlayerList().getPlayer(mc.player.getUUID());
    }

    static void sur(Minecraft mc, Runnable travail) {
        serveur(mc).execute(travail);
    }

    /** Une commande jouée par le serveur au niveau 4 (la console de l'équipe). */
    static void commande(MinecraftServer serveur, String ligne) {
        serveur.execute(() -> serveur.getCommands().performPrefixedCommand(serveur.createCommandSourceStack().withSuppressedOutput(), ligne));
    }

    /** Une commande de l'équipe jouée au nom du joueur : la réponse s'affiche dans son chat. */
    static void commandeVue(Minecraft mc, String ligne) {
        sur(mc, () -> serveur(mc).getCommands().performPrefixedCommand(moiServeur(mc).createCommandSourceStack().withPermission(4), ligne));
    }

    static void aller(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        sur(mc, () -> moiServeur(mc).teleportTo(serveur(mc).overworld(), x, y, z, yaw, pitch));
    }

    /** Le joueur deux blocs devant le bloc (au sud), regard au nord vers lui. */
    static void devant(Minecraft mc, BlockPos bloc, float pitch) {
        aller(mc, bloc.getX() + 0.5, o.getY(), bloc.getZ() + 2.5, 180, pitch);
    }

    static boolean pres(Minecraft mc, double x, double z) {
        return Math.abs(mc.player.getX() - x) < 0.6 && Math.abs(mc.player.getZ() - z) < 0.6;
    }

    static boolean devantPret(Minecraft mc, BlockPos bloc) {
        return pres(mc, bloc.getX() + 0.5, bloc.getZ() + 2.5);
    }

    public static void clicDroit(Minecraft mc, BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        mc.player.swing(InteractionHand.MAIN_HAND);
        LOG.info("[film] clic droit en {} : {}", pos, res);
    }

    public static void tenirVide(Minecraft mc) {
        for (int i = 8; i >= 0; i--) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        // Barre pleine : la case 8 part dans une case libre du sac.
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, 8, ClickType.SWAP, mc.player);
                mc.player.getInventory().selected = 8;
                return;
            }
        }
        throw new IllegalStateException("inventaire plein");
    }

    /** L'objet en main ; pris dans le sac (échange avec une case vide de la barre) s'il n'est pas dans la barre. */
    static void tenir(Minecraft mc, Predicate<ItemStack> voulu) {
        for (int i = 0; i < 9; i++) {
            if (voulu.test(mc.player.getInventory().getItem(i))) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (voulu.test(mc.player.getInventory().getItem(i))) {
                for (int h = 8; h >= 0; h--) {
                    if (mc.player.getInventory().getItem(h).isEmpty()) {
                        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, h, ClickType.SWAP, mc.player);
                        mc.player.getInventory().selected = h;
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("objet absent de l'inventaire");
    }

    static void clicMenu(Minecraft mc, int index, int bouton, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, index, bouton, type, mc.player);
    }

    static int caseJoueur(Minecraft mc, Predicate<ItemStack> voulu) {
        for (Slot s : mc.player.containerMenu.slots) {
            if (s.container == mc.player.getInventory() && s.getContainerSlot() < 36 && voulu.test(s.getItem())) {
                return s.index;
            }
        }
        return -1;
    }

    /** La case du menu qui porte l'objet, sinon la première case vide de l'inventaire. */
    static int caseOuVide(Minecraft mc, Predicate<ItemStack> voulu) {
        int i = caseJoueur(mc, voulu);
        return i >= 0 ? i : caseJoueur(mc, ItemStack::isEmpty);
    }

    /** Les pièces en vrac dans l'inventaire (hors bourses). */
    static long libre(Minecraft mc) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s);
        }
        return n;
    }

    static long valeurPortee(Minecraft mc) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s) + Purse.total(s);
        }
        return n;
    }

    static long bourse(Minecraft mc) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += Purse.total(s);
        }
        return n;
    }

    static int compter(Minecraft mc, Item item) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    static String fmt(long cuivres) {
        return CoinMath.format(cuivres).getString();
    }

    static boolean menuOuvert(Minecraft mc, Class<?> menu) {
        return mc.screen instanceof AbstractContainerScreen<?> s && menu.isInstance(s.getMenu());
    }

    static int[] origineEcran(Minecraft mc) {
        AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.screen;
        return new int[] {s.getGuiLeft(), s.getGuiTop()};
    }

    static int champ(Object ecran, String nom) {
        try {
            Field f = ecran.getClass().getDeclaredField(nom);
            f.setAccessible(true);
            return f.getInt(ecran);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("champ " + nom + " illisible : " + e);
        }
    }

    static void taper(Minecraft mc, String texte) {
        for (char c : texte.toCharArray()) {
            mc.screen.charTyped(c, 0);
        }
    }

    static void effacerChamp(Minecraft mc) {
        mc.screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        for (int i = 0; i < 6; i++) {
            mc.screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        }
    }

    public static Optional<AbstractWidget> widget(Screen ecran, String cle) {
        for (GuiEventListener l : ecran.children()) {
            if (l instanceof AbstractWidget w && w.visible && w.getMessage().getContents() instanceof TranslatableContents t
                    && cle.equals(t.getKey())) {
                return Optional.of(w);
            }
        }
        return Optional.empty();
    }

    static void fermerEcran(Minecraft mc) {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        mc.setScreen(null);
    }

    static Player joueurVu(Minecraft mc, String nom) {
        for (Player j : mc.level.players()) {
            if (nom.equals(j.getGameProfile().getName())) {
                return j;
            }
        }
        return null;
    }

    // --- curseur virtuel ----------------------------------------------------------------------------

    /** Le curseur glisse vers ce point (coordonnées d'interface) en {@code ms}. */
    static void viser(double x, double y, long ms) {
        curDeX = curX;
        curDeY = curY;
        curAX = x;
        curAY = y;
        curDebutNs = System.nanoTime();
        curDureeNs = Math.max(1, ms) * 1_000_000L;
    }

    /** Le curseur posé là d'un coup, sans glisser. */
    static void placerCurseur(double x, double y) {
        curX = x;
        curY = y;
        curAX = x;
        curAY = y;
        curDeX = x;
        curDeY = y;
    }

    static boolean arrive() {
        return System.nanoTime() - curDebutNs >= curDureeNs;
    }

    static boolean curseurVisible(Minecraft mc) {
        return mc.screen != null && !(mc.screen instanceof ChatScreen);
    }

    /** Chaque image : la position du curseur, donnée au jeu par le rappel de GLFW (le vrai curseur ne bouge pas). */
    static void appliquerCurseur(Minecraft mc) {
        double t = Math.min(1.0, (System.nanoTime() - curDebutNs) / (double) curDureeNs);
        double e = t * t * (3 - 2 * t);
        curX = curDeX + (curAX - curDeX) * e;
        curY = curDeY + (curAY - curDeY) * e;
        if (mc.screen == null) {
            return;
        }
        var f = mc.getWindow();
        long fenetre = f.getWindow();
        GLFWCursorPosCallback rappel = GLFW.glfwSetCursorPosCallback(fenetre, null);
        GLFW.glfwSetCursorPosCallback(fenetre, rappel);
        if (rappel != null) {
            rappel.invoke(fenetre, curX * f.getScreenWidth() / f.getGuiScaledWidth(), curY * f.getScreenHeight() / f.getGuiScaledHeight());
        }
    }

    /** Un vrai clic de souris sur l'écran ouvert, là où est le curseur. */
    static void cliquer(Minecraft mc, int bouton) {
        String ecran = mc.screen.getClass().getSimpleName();
        boolean pris = mc.screen.mouseClicked(curX, curY, bouton);
        // Un bouton comme « Émettre » ferme l'écran pendant le clic.
        if (mc.screen != null) {
            mc.screen.mouseReleased(curX, curY, bouton);
        }
        LOG.info("[film] clic {} en ({}, {}) sur {} : {}", bouton, Math.round(curX), Math.round(curY), ecran, pris);
    }

    static int[] centreCase(Minecraft mc, int index) {
        int[] g = origineEcran(mc);
        Slot s = mc.player.containerMenu.getSlot(index);
        return new int[] {g[0] + s.x + 8, g[1] + s.y + 8};
    }

    static int[] centreElement(Minecraft mc, String id) {
        Optional<int[]> c;
        if (mc.screen instanceof CoinAccountScreen<?> s) {
            c = s.elementCenter(id);
        } else if (mc.screen instanceof TradeScreen s) {
            c = s.elementCenter(id);
        } else if (mc.screen instanceof StallCashScreen s) {
            c = s.elementCenter(id);
        } else {
            c = Optional.empty();
        }
        return c.orElseThrow(() -> new IllegalStateException("aucun élément " + id + " sur " + mc.screen));
    }

    // --- caméra (spectateur) ------------------------------------------------------------------------

    public static float[] regard(Vec3 de, Vec3 vers) {
        double dx = vers.x - de.x;
        double dy = vers.y - de.y;
        double dz = vers.z - de.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new float[] {yaw, pitch};
    }

    /** Un travelling de {@code de} à {@code a} en regardant {@code cible} puis {@code cible2}, en {@code ms}. */
    static void travelling(Vec3 de, Vec3 cible, Vec3 a, Vec3 cible2, long ms) {
        cameraDe = de;
        cameraA = a;
        // Le regard part de l'œil, 1,62 au-dessus de la position donnée (les pieds).
        regardDe = regard(de.add(0, 1.62, 0), cible);
        regardA = regard(a.add(0, 1.62, 0), cible2);
        float d = regardA[0] - regardDe[0];
        while (d > 180) {
            d -= 360;
        }
        while (d < -180) {
            d += 360;
        }
        regardA[0] = regardDe[0] + d;
        cameraDebutNs = System.nanoTime();
        cameraDureeNs = ms * 1_000_000L;
    }

    static boolean travellingFini() {
        return cameraDe == null || System.nanoTime() - cameraDebutNs >= cameraDureeNs;
    }

    static void appliquerCamera(Minecraft mc) {
        if (cameraDe == null || mc.player == null) {
            return;
        }
        double t = Math.min(1.0, (System.nanoTime() - cameraDebutNs) / (double) cameraDureeNs);
        double e = t * t * (3 - 2 * t);
        Vec3 pos = cameraDe.add(cameraA.subtract(cameraDe).scale(e));
        float yaw = (float) (regardDe[0] + (regardA[0] - regardDe[0]) * e);
        float pitch = (float) (regardDe[1] + (regardA[1] - regardDe[1]) * e);
        Player j = mc.player;
        j.setPos(pos.x, pos.y, pos.z);
        j.xo = pos.x;
        j.yo = pos.y;
        j.zo = pos.z;
        j.xOld = pos.x;
        j.yOld = pos.y;
        j.zOld = pos.z;
        j.setDeltaMovement(Vec3.ZERO);
        j.setYRot(yaw);
        j.yRotO = yaw;
        j.setXRot(pitch);
        j.xRotO = pitch;
        j.setYHeadRot(yaw);
    }

    static void finCamera() {
        cameraDe = null;
    }

    // ================================================================================================
    // Les pas
    // ================================================================================================

    static void scene(String titre) {
        titreCourant = titre;
    }

    static void plan(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs) {
        ETAPES.add(new Etape(nom, true, titreCourant, () -> legende, action, pret, reposMs, null));
    }

    static void planV(String nom, Supplier<String> legende, Runnable action, BooleanSupplier pret, long reposMs) {
        ETAPES.add(new Etape(nom, true, titreCourant, legende, action, pret, reposMs, null));
    }

    static void prep(String nom, Runnable action, BooleanSupplier pret) {
        ETAPES.add(new Etape(nom, false, "", () -> "", action, pret, 400, null));
    }

    /** Un carton titre filmé pendant {@code ms} : numéro, titre et sous-titre de la scène qui suit. */
    static void carton(String nom, String numero, String titre, String sousTitre, long ms) {
        ETAPES.add(new Etape(nom, true, "", () -> "", () -> {}, () -> true, ms, new String[] {numero, titre, sousTitre}));
    }

    /** Deux pas : le curseur glisse vers la cible, puis le clic (bouton 0 ou 1). */
    static void clic(String nom, String legende, Supplier<int[]> cible, int bouton, BooleanSupplier pret, long reposMs) {
        clicV(nom, () -> legende, cible, bouton, pret, reposMs);
    }

    static void clicV(String nom, Supplier<String> legende, Supplier<int[]> cible, int bouton, BooleanSupplier pret, long reposMs) {
        planV(nom + "-vise", legende, () -> {
            int[] c = cible.get();
            viser(c[0], c[1], 450);
        }, FilmTournage::arrive, 150);
        // Après le clic, le curseur s'écarte un peu : l'infobulle du bouton (souvent « indisponible » une fois fait) disparaît.
        planV(nom, legende, () -> {
            cliquer(Minecraft.getInstance(), bouton);
            viser(curAX + 26, curAY + 34, 350);
        }, pret, reposMs);
    }

    /** Survol d'un point (infobulle), sans clic. */
    static void survol(String nom, String legende, Supplier<int[]> cible, long reposMs) {
        plan(nom, legende, () -> {
            int[] c = cible.get();
            viser(c[0], c[1], 450);
        }, FilmTournage::arrive, reposMs);
    }

    /** L'étape en cours si elle est filmée, sinon {@code null}. */
    static Etape etapeFilmee() {
        if (indexEtape >= 0 && indexEtape < ETAPES.size() && ETAPES.get(indexEtape).tourne()) {
            return ETAPES.get(indexEtape);
        }
        return null;
    }

    static String nomEtape() {
        return indexEtape >= 0 && indexEtape < ETAPES.size() ? ETAPES.get(indexEtape).nom() : "-";
    }

    /** Un tour de la boucle des pas : action, attente de {@code pret}, temps de pose, pas suivant ; {@link #fin} au dernier. */
    static void jouer(Minecraft mc) {
        if (indexEtape >= ETAPES.size()) {
            return;
        }
        long maintenant = System.nanoTime();
        if (indexEtape < 0) {
            indexEtape = 0;
            actionFaite = false;
        }
        Etape e = ETAPES.get(indexEtape);
        if (!actionFaite) {
            LOG.info("[film] étape : {}", e.nom());
            if (e.tourne()) {
                prise.plan(e.nom(), e.carton() != null ? "carton" : e.titre());
            }
            e.action().run();
            actionFaite = true;
            debutAction = maintenant;
            debutRepos = 0;
            return;
        }
        boolean pret;
        try {
            pret = e.pret().getAsBoolean();
        } catch (RuntimeException ex) {
            pret = false;
        }
        if (!pret) {
            if (maintenant - debutAction > (long) (delaiPas * 1e9)) {
                throw new IllegalStateException("étape sans issue : " + e.nom() + " | écran " + mc.screen + " | porté " + valeurPortee(mc)
                        + " | dernier message « " + dernierChat + " »");
            }
            return;
        }
        if (debutRepos == 0) {
            debutRepos = maintenant;
        }
        if ((maintenant - debutRepos) / 1_000_000L < e.reposMs() * rythme) {
            return;
        }
        LOG.info("[film] étape finie : {}", e.nom());
        indexEtape++;
        actionFaite = false;
        if (indexEtape >= ETAPES.size()) {
            fin.run();
        }
    }
}
