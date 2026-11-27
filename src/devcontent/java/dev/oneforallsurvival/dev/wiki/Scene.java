package dev.oneforallsurvival.dev.wiki;

import dev.oneforallsurvival.dev.FilmTournage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;

/**
 * Une scène de l'atelier : une suite de pas joués l'un après l'autre sur le fil du rendu. Le premier pas (implicite)
 * prépare un terrain neuf (midi, temps clair, inventaire vide, interface masquée, échelle 2) ; la scène y ajoute sa
 * préparation côté serveur ({@link #serveur}), sa caméra ({@link #camera}, {@link #regarder}), ses écrans
 * ({@link #ouvrirBloc}), ses repères et ses prises ({@link #prise}, {@link #sequence}). Les positions sont relatives à
 * l'origine du terrain. Un pas qui échoue arrête la scène (image {@code <scène>-echec.png}) ; les autres continuent.
 *
 * <pre>{@code
 * atelier.scene("antenne")
 *         .serveur("pose", lieu -> ...)                       // côté serveur, attendu
 *         .attendre("en service", () -> ...)                  // condition côté client
 *         .regarder(new BlockPos(0, 0, 0), new Vec3(1.2, 0.6, 2.2))
 *         .repereBloc("voyant", new BlockPos(0, 0, 0), 0.5, 0.875, 0.5)
 *         .prise("antenne-service");
 * }</pre>
 */
public final class Scene {

    /** Un pas : action (fil du rendu), condition, temps de pose, délai, puis {@code apres} (la prise). */
    record Pas(Scene scene, String nom, Runnable action, BooleanSupplier pret, long reposMs, long delaiMs,
            Runnable apres) {}

    /** Un repère calculé à la prise : pourcentage de l'image, ou vide s'il n'est pas visible. */
    interface Repere {
        Optional<double[]> calculer(Minecraft mc);
    }

    static final long DELAI_MS = 30_000;
    private static final long POSE_MONDE_MS = 700;

    private final String chapitre;
    private final String nom;
    private final List<Pas> pas = new ArrayList<>();
    private final Map<String, Repere> reperes = new LinkedHashMap<>();
    private volatile Lieu lieu;

    Scene(String chapitre, String nom) {
        this.chapitre = chapitre;
        this.nom = nom;
        ajouter("terrain", () -> WikiStudio.preparerTerrain(this), () -> WikiStudio.terrainPret(this), 300,
                120_000, null);
    }

    public String chapitre() {
        return chapitre;
    }

    public String nom() {
        return nom;
    }

    /** Le terrain de la scène, connu une fois le premier pas joué (pour les actions côté client). */
    public Lieu lieu() {
        if (lieu == null) {
            throw new IllegalStateException("terrain de " + nom + " pas encore préparé");
        }
        return lieu;
    }

    void lieu(Lieu l) {
        lieu = l;
    }

    List<Pas> pas() {
        return pas;
    }

    private void ajouter(String nomPas, Runnable action, BooleanSupplier pret, long reposMs, long delaiMs,
            Runnable apres) {
        pas.add(new Pas(this, nomPas, action, pret, reposMs, delaiMs, apres));
    }

    // --- préparation -----------------------------------------------------------------------------------------------

    /** Une préparation jouée par le serveur ; le pas suivant attend qu'elle soit finie (une exception fait échouer). */
    public Scene serveur(String nomPas, Consumer<Lieu> travail) {
        Object[] etat = new Object[1];
        ajouter(nomPas, () -> {
            etat[0] = null;
            MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
            srv.execute(() -> {
                try {
                    travail.accept(lieu());
                    etat[0] = Boolean.TRUE;
                } catch (RuntimeException e) {
                    WikiStudio.LOG.error("[wiki] préparation « {} » de {}", nomPas, nom, e);
                    etat[0] = e;
                }
            });
        }, () -> {
            Object e = etat[0];
            if (e instanceof RuntimeException ex) {
                throw new IllegalStateException("préparation « " + nomPas + " » : " + ex, ex);
            }
            return e == Boolean.TRUE;
        }, 100, DELAI_MS, null);
        return this;
    }

    /** Attend une condition (côté client), au plus 30 s. */
    public Scene attendre(String nomPas, BooleanSupplier condition) {
        return attendre(nomPas, condition, DELAI_MS);
    }

    public Scene attendre(String nomPas, BooleanSupplier condition, long delaiMs) {
        ajouter(nomPas, () -> {}, condition, 100, delaiMs, null);
        return this;
    }

    /** Une action côté client (fil du rendu), sans attente. */
    public Scene client(String nomPas, Consumer<Minecraft> action) {
        ajouter(nomPas, () -> action.accept(Minecraft.getInstance()), () -> true, 100, DELAI_MS, null);
        return this;
    }

    /** Laisse passer le temps (animations, état qui s'établit). */
    public Scene pause(long ms) {
        ajouter("pause " + ms + " ms", () -> {}, () -> true, ms, DELAI_MS, null);
        return this;
    }

    // --- caméra et affichage ---------------------------------------------------------------------------------------

    /**
     * L'œil en {@code oeil} (relatif à l'origine) regarde {@code cible} : le joueur y est téléporté, en vol, sans
     * gravité ; le pas finit quand le client y est et que les chunks visibles sont construits.
     */
    public Scene camera(Vec3 oeil, Vec3 cible) {
        ajouter("caméra", () -> {
            Lieu l = lieu();
            Vec3 o = l.v(oeil.x, oeil.y, oeil.z);
            Vec3 c = l.v(cible.x, cible.y, cible.z);
            float[] r = FilmTournage.regard(o, c);
            WikiStudio.placerCamera(o, r[0], r[1]);
        }, () -> WikiStudio.cameraPrete(lieu().v(oeil.x, oeil.y, oeil.z), lieu().v(cible.x, cible.y, cible.z)),
                POSE_MONDE_MS, 45_000, null);
        return this;
    }

    /** Le centre du bloc {@code bloc} vu depuis son centre + {@code depuis} (comme {@code DemoVisuels.regarder}). */
    public Scene regarder(BlockPos bloc, Vec3 depuis) {
        Vec3 centre = new Vec3(bloc.getX() + 0.5, bloc.getY() + 0.5, bloc.getZ() + 0.5);
        return camera(centre.add(depuis), centre);
    }

    /** L'interface du jeu (barre, main) visible ou non pour les prises suivantes ; masquée par défaut. */
    public Scene ath(boolean visible) {
        return client("interface " + (visible ? "visible" : "masquée"), mc -> WikiStudio.ath(mc, visible));
    }

    /** L'échelle d'interface des prises suivantes ; échoue si le jeu la ramène à une autre (4 sur 720 de haut). */
    public Scene echelle(int echelle) {
        ajouter("échelle " + echelle, () -> WikiStudio.echelle(Minecraft.getInstance(), echelle),
                () -> WikiStudio.echelleAtteinte(Minecraft.getInstance(), echelle), 300, 10_000, null);
        return this;
    }

    // --- gestes et écrans ------------------------------------------------------------------------------------------

    /** Un vrai clic droit, main vide, du joueur sur la face du bloc (le joueur doit être à moins de 8 blocs). */
    public Scene clicDroit(BlockPos bloc, Direction face) {
        return client("clic droit " + bloc.toShortString(), mc -> {
            FilmTournage.tenirVide(mc);
            FilmTournage.clicDroit(mc, lieu().p(bloc.getX(), bloc.getY(), bloc.getZ()), face);
        });
    }

    /** Ouvre l'écran du bloc par un vrai clic droit et attend l'écran de la classe voulue. */
    public Scene ouvrirBloc(BlockPos bloc, Direction face, Class<? extends Screen> ecran) {
        clicDroit(bloc, face);
        return attendre("écran " + ecran.getSimpleName(), () -> ecran.isInstance(Minecraft.getInstance().screen));
    }

    /** Ouvre un écran construit côté client et attend qu'il soit en place. */
    public Scene ouvrir(String nomPas, Supplier<Screen> ecran) {
        Screen[] ouvert = new Screen[1];
        ajouter(nomPas, () -> {
            ouvert[0] = ecran.get();
            Minecraft.getInstance().setScreen(ouvert[0]);
        }, () -> Minecraft.getInstance().screen == ouvert[0], 300, DELAI_MS, null);
        return this;
    }

    /** Ferme l'écran ouvert (et le menu côté serveur). */
    public Scene fermer() {
        return client("fermer l'écran", WikiStudio::fermerEcran);
    }

    // --- repères ---------------------------------------------------------------------------------------------------

    /** Un point du monde (relatif à l'origine), projeté par les matrices de la caméra à la prise suivante. */
    public Scene repereMonde(String nomRepere, Vec3 point) {
        reperes.put(nomRepere, mc -> Reperes.projeter(lieu().v(point.x, point.y, point.z)));
        return this;
    }

    /** Un point d'un bloc : {@code (ox, oy, oz)} dans le bloc, de 0 à 1 (0,5 partout : son centre). */
    public Scene repereBloc(String nomRepere, BlockPos bloc, double ox, double oy, double oz) {
        return repereMonde(nomRepere, new Vec3(bloc.getX() + ox, bloc.getY() + oy, bloc.getZ() + oz));
    }

    /** Le centre d'une case du menu ouvert (indice dans {@code menu.slots}). */
    public Scene repereCase(String nomRepere, int indice) {
        reperes.put(nomRepere, mc -> {
            if (!(mc.screen instanceof AbstractContainerScreen<?> s) || indice >= s.getMenu().slots.size()) {
                return Optional.empty();
            }
            Slot c = s.getMenu().getSlot(indice);
            return Optional.of(Reperes.interfaceEnPourcent(mc, s.getGuiLeft() + c.x + 8, s.getGuiTop() + c.y + 8));
        });
        return this;
    }

    /** Le centre du premier élément visible de l'écran ouvert qui répond au filtre. */
    public Scene repereWidget(String nomRepere, Predicate<AbstractWidget> filtre) {
        reperes.put(nomRepere, mc -> {
            if (mc.screen == null) {
                return Optional.empty();
            }
            for (GuiEventListener e : mc.screen.children()) {
                if (e instanceof AbstractWidget w && w.visible && filtre.test(w)) {
                    return Optional.of(Reperes.interfaceEnPourcent(mc, w.getX() + w.getWidth() / 2.0,
                            w.getY() + w.getHeight() / 2.0));
                }
            }
            return Optional.empty();
        });
        return this;
    }

    /** Un point de l'interface (coordonnées mises à l'échelle) calculé à la prise, ou {@code null} : absent. */
    public Scene repereInterface(String nomRepere, Function<Minecraft, double[]> point) {
        reperes.put(nomRepere, mc -> Optional.ofNullable(point.apply(mc))
                .map(p -> Reperes.interfaceEnPourcent(mc, p[0], p[1])));
        return this;
    }

    // --- prises ----------------------------------------------------------------------------------------------------

    /** Une image nommée comme la scène. */
    public Scene prise() {
        return prise(nom);
    }

    /**
     * Une image {@code captures/<chapitre>/<nom>.png} et, s'il y a des repères déclarés depuis la prise précédente,
     * {@code <nom>.json}. Un repère hors de l'image fait échouer la scène.
     */
    public Scene prise(String nomImage) {
        Map<String, Repere> ceux = new LinkedHashMap<>(reperes);
        reperes.clear();
        ajouter("prise " + nomImage, () -> {}, () -> WikiStudio.imageStable(), 400, DELAI_MS,
                () -> WikiStudio.prendre(this, nomImage, ceux));
        return this;
    }

    /**
     * Une séquence : une image toutes les {@code pasMs} pendant {@code dureeMs}, dans
     * {@code captures/<chapitre>/<nom>/f0000.png}… ; {@code auDebut} est joué par le serveur à la première image.
     */
    public Scene sequence(String nomSequence, long dureeMs, long pasMs, Consumer<Lieu> auDebut) {
        return sequence(nomSequence, dureeMs, pasMs, auDebut, null);
    }

    /**
     * Une séquence pendant laquelle le client fait des gestes : {@code pendant} est appelé à chaque image rendue avec le
     * temps écoulé depuis la première prise (en ms), sur le fil du rendu ; une exception fait échouer la scène.
     */
    public Scene sequence(String nomSequence, long dureeMs, long pasMs, Consumer<Lieu> auDebut,
            java.util.function.BiConsumer<Minecraft, Long> pendant) {
        ajouter("séquence " + nomSequence, () -> WikiStudio.commencerSequence(this, nomSequence, dureeMs, pasMs,
                auDebut, pendant), WikiStudio::sequenceFinie, 0, dureeMs + DELAI_MS, WikiStudio::finirSequence);
        return this;
    }

    public Scene sequence(String nomSequence, long dureeMs, long pasMs) {
        return sequence(nomSequence, dureeMs, pasMs, null);
    }

    /** Le joueur côté serveur (pour une préparation qui le déplace ou l'équipe). */
    static ServerPlayer joueur(MinecraftServer srv) {
        return srv.getPlayerList().getPlayers().get(0);
    }
}
