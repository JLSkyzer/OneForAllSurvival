package dev.oneforallsurvival.client.title;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.oneforallsurvival.client.title.TitleLayout.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.joml.Matrix4f;

/**
 * Écran titre de Lost Island : sous-classe de {@link TitleScreen} substituée à l'ouverture
 * ({@link TitleEvents#onOpening}), pour que les mods qui testent {@code instanceof TitleScreen} continuent d'y
 * ajouter leurs boutons.
 *
 * <p>{@link #init()} laisse Minecraft créer ses widgets ({@code super.init()}), retire Solo, Multijoueur, Realms,
 * Options et Quitter de Minecraft, garde la langue, l'accessibilité, la liste des mods de Forge et la mention de
 * Mojang, puis ajoute nos quatre boutons. {@link #arrange()} place tout ; il est rappelé après tous les autres mods
 * ({@code ScreenEvent.Init.Post} de priorité la plus basse) pour ranger leurs boutons dans la rangée du bas.
 *
 * <p>{@link #render} ne rappelle pas celui de {@code TitleScreen} : ni panorama, ni logo de Minecraft, ni phrase
 * jaune, ni notification de Realms. La scène est demandée à {@link TitleScene} à chaque image ; aucune référence à
 * elle n'est gardée ici.
 */
public class LostIslandTitleScreen extends TitleScreen {
    /** Assombrissement à gauche : opacité au bord gauche, et part de la largeur où elle tombe à 0. */
    private static final float DARKEN_ALPHA = 0.55f;
    private static final float DARKEN_SHARE = 0.45f;
    /** Fondu d'entrée des widgets, la première fois que le titre paraît après le chargement. */
    private static final long INTRO_MS = 600L;
    /** Largeur minimale du bouton des mods, réduit dans la rangée du bas (98 px chez Minecraft). */
    private static final int MODS_MIN_W = 40;

    // Fondu d'entrée : une fois par session, pas à chaque retour d'Options ou d'un monde (thread du jeu seul).
    private static long introStart = -1L;
    private static boolean introDone;

    private final Set<GuiEventListener> ours = Collections.newSetFromMap(new IdentityHashMap<>());
    private Button solo;
    private Button join;
    private Button options;
    private Button quit;
    private AbstractWidget vanillaMods; // bouton des mods créé par Forge dans super.init(), réduit dans la rangée
    private boolean layoutWithScene; // disposition calculée sur le logo de la scène (et non sur le repli)
    private SiteAddress.Status shownStatus; // état de l'adresse appliqué au bouton
    private int arrangedChildren = -1; // nombre de widgets à la dernière disposition

    public LostIslandTitleScreen() {
        // Le drapeau « fading » de TitleScreen ne sert qu'à son propre rendu, remplacé ici.
        super(false);
    }

    @Override
    protected void init() {
        // Widgets déjà là (ajoutés par d'autres mods dans ScreenEvent.Init.Pre) : jamais retirés.
        Set<GuiEventListener> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(children());
        super.init();
        vanillaMods = null;
        for (GuiEventListener listener : List.copyOf(children())) {
            if (before.contains(listener) || !(listener instanceof AbstractWidget widget)) {
                continue;
            }
            String key = keyOf(widget.getMessage());
            if (TitleWidgets.classify(key, false) == TitleWidgets.Kind.VANILLA_REMOVED) {
                removeWidget(listener);
            } else if (TitleWidgets.MODS_KEY.equals(key)) {
                vanillaMods = widget;
            }
        }

        ours.clear();
        solo = addRenderableWidget(Button.builder(Component.translatable("gui.oneforallsurvival.title.solo"),
                b -> minecraft.setScreen(new SelectWorldScreen(this))).size(TitleLayout.MAIN_W, TitleLayout.MAIN_H)
                .build());
        join = addRenderableWidget(new GoldButton(0, 0, TitleLayout.MAIN_W, TitleLayout.MAIN_H,
                Component.translatable("gui.oneforallsurvival.title.join"), b -> joinIsland()));
        applyJoinStatus(SiteAddressClient.status());
        options = addRenderableWidget(Button.builder(Component.translatable("gui.oneforallsurvival.title.options"),
                b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)))
                .size(TitleLayout.MAIN_W, TitleLayout.MAIN_H).build());
        quit = addRenderableWidget(Button.builder(Component.translatable("gui.oneforallsurvival.title.quit"),
                b -> minecraft.stop()).size(TitleLayout.MAIN_W, TitleLayout.MAIN_H).build());
        ours.addAll(List.of(solo, join, options, quit));
        for (Button b : List.of(solo, join, options, quit)) {
            b.setTabOrderGroup(-1); // Tab parcourt nos quatre boutons avant les petits boutons et ceux des autres mods
        }
        arrange();
    }

    /**
     * Place tous les widgets : rangée du bas (petits boutons de Minecraft et de Forge, puis ceux des autres mods,
     * dans leur ordre d'ajout), mention de Mojang en bas à droite, puis nos quatre boutons sous le logo (calculés
     * après la rangée, qu'ils ne doivent pas chevaucher). Rien n'est retiré ni caché ; un widget invisible
     * (choix de son mod) n'est pas déplacé.
     */
    public void arrange() {
        if (solo == null || !children().contains(solo)) {
            // init() pas passé (pas encore, ou Init.Pre annulé après un clearWidgets) : rien à placer, et pas de
            // nouvel essai à chaque image
            arrangedChildren = children().size();
            return;
        }
        List<AbstractWidget> row = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        AbstractWidget copyright = null;
        for (GuiEventListener listener : children()) {
            if (ours.contains(listener) || !(listener instanceof AbstractWidget widget) || !widget.visible) {
                continue;
            }
            String key = keyOf(widget.getMessage());
            if (TitleWidgets.COPYRIGHT_KEY.equals(key)) {
                copyright = widget;
                continue;
            }
            row.add(widget);
            keys.add(key);
        }
        List<Integer> order = TitleWidgets.rowOrder(keys);

        if (vanillaMods != null) {
            vanillaMods.setWidth(Math.max(MODS_MIN_W, font.width(vanillaMods.getMessage()) + 16));
        }
        List<int[]> sizes = new ArrayList<>();
        for (int i : order) {
            sizes.add(new int[] {row.get(i).getWidth(), row.get(i).getHeight()});
        }
        // Sur un écran étroit, la rangée s'arrête avant la mention de Mojang (même bande du bas).
        int maxRight = copyright == null ? width : width - copyright.getWidth() - 2 - TitleLayout.ROW_GAP;
        List<Rect> bottom = TitleLayout.bottomRow(width, height, sizes, maxRight);
        for (int k = 0; k < order.size(); k++) {
            Rect r = bottom.get(k);
            row.get(order.get(k)).setPosition(r.x(), r.y());
        }
        if (copyright != null) {
            copyright.setPosition(width - copyright.getWidth() - 2, height - 10);
        }

        List<Rect> main = TitleLayout.mainButtons(width, height, logoRect(), bottom);
        Button[] buttons = {solo, join, options, quit};
        for (int i = 0; i < buttons.length; i++) {
            Rect r = main.get(i);
            buttons[i].setPosition(r.x(), r.y());
            buttons[i].setWidth(r.w());
        }
        arrangedChildren = children().size();
    }

    /** Logo de la scène si elle est là (manifeste lu le temps de l'appel), sinon ancre de repli. */
    private Rect logoRect() {
        Optional<?> scene = TitleScene.acquire();
        layoutWithScene = scene.isPresent();
        if (layoutWithScene) {
            int guiScale = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
            return SceneLogo.rect(scene.get(), width, height, guiScale);
        }
        return TitleLayout.fallbackLogoRect(width, height);
    }

    /** Active ou grise « Rejoindre l'île » selon l'état de l'adresse (site, adresse gardée, repli, maintenance). */
    private void applyJoinStatus(SiteAddress.Status next) {
        shownStatus = next;
        join.active = next.joinable();
        if (next.joinable()) {
            join.setTooltip(null);
        } else if (next.maintenance()) {
            // texte brut du site : jamais interprété comme un format ou un composant
            join.setTooltip(Tooltip.create(next.message() != null ? Component.literal(next.message())
                    : Component.translatable("gui.oneforallsurvival.title.join.maintenance")));
        } else {
            join.setTooltip(Tooltip.create(Component.translatable("gui.oneforallsurvival.title.join.soon")));
        }
    }

    private void joinIsland() {
        SiteAddress.Status current = SiteAddressClient.status();
        String address = current.address();
        if (address == null || current.maintenance()) {
            return;
        }
        // Connexion standard de Minecraft ; le serveur n'est pas ajouté à la liste. Refus → écran d'erreur habituel.
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(address),
                new ServerData(TitleConfig.SERVER_NAME, address, false), false);
    }

    @Override
    public void tick() {
        // Volontairement sans super.tick() : celui de TitleScreen fait vivre les notifications de Realms
        // (requêtes réseau) et l'avertissement Realms 32 bits ; Realms est absent de cet écran.
        // La réponse du site peut arriver après l'ouverture : relue ici, sur le fil du client.
        SiteAddress.Status latest = SiteAddressClient.status();
        if (join != null && !latest.equals(shownStatus)) {
            applyJoinStatus(latest);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean sceneDrawn = TitleScene.render(graphics, partialTick);
        if (!sceneDrawn) {
            graphics.fillGradient(0, 0, width, height, TitleScene.FALLBACK_TOP, TitleScene.FALLBACK_BOTTOM);
        }
        // La scène a pu arriver après init() (confiée par l'overlay à la fin du fondu) ; un widget a pu s'ajouter.
        if ((sceneDrawn && !layoutWithScene) || children().size() != arrangedChildren) {
            arrange();
        }

        float ui = introAlpha();
        darkenLeft(graphics, ui);
        if (ui < 0.02f) {
            return; // sous ~4/255, la police dessinerait le texte opaque
        }
        for (GuiEventListener listener : children()) {
            if (listener instanceof AbstractWidget widget) {
                widget.setAlpha(ui);
            }
        }
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        String version = "Minecraft " + SharedConstants.getCurrentVersion().getName();
        int alpha = Math.round(ui * 255f) << 24;
        graphics.drawString(font, version, width - font.width(version) - 2, height - 20, alpha | 0xD8D8D8);
    }

    /** Opacité des widgets : 0 tant que l'overlay du chargement est là, puis fondu de {@value #INTRO_MS} ms, une fois. */
    private float introAlpha() {
        if (introDone) {
            return 1f;
        }
        if (minecraft.getOverlay() != null) {
            return 0f;
        }
        long now = Util.getMillis();
        if (introStart < 0L) {
            introStart = now;
        }
        float t = (now - introStart) / (float) INTRO_MS;
        if (t >= 1f) {
            introDone = true;
            return 1f;
        }
        return t * t * (3f - 2f * t);
    }

    /**
     * Assombrissement horizontal à gauche, sous les boutons : noir d'opacité {@value #DARKEN_ALPHA} au bord gauche,
     * nulle à {@value #DARKEN_SHARE} de la largeur. Quadrilatère à couleurs par sommet (le dégradé de
     * {@code GuiGraphics.fillGradient} est vertical seulement), suivant le fondu des widgets.
     */
    private void darkenLeft(GuiGraphics graphics, float ui) {
        float a = DARKEN_ALPHA * ui;
        if (a <= 0f) {
            return;
        }
        float right = width * DARKEN_SHARE;
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer quad = graphics.bufferSource().getBuffer(RenderType.gui());
        quad.vertex(pose, 0f, 0f, 0f).color(0f, 0f, 0f, a).endVertex();
        quad.vertex(pose, 0f, height, 0f).color(0f, 0f, 0f, a).endVertex();
        quad.vertex(pose, right, height, 0f).color(0f, 0f, 0f, 0f).endVertex();
        quad.vertex(pose, right, 0f, 0f).color(0f, 0f, 0f, 0f).endVertex();
        graphics.flush();
    }

    /** Clé de traduction du libellé, {@link TitleWidgets#COPYRIGHT_KEY} pour la mention de Mojang, sinon null. */
    static String keyOf(Component message) {
        if (message == COPYRIGHT_TEXT) {
            return TitleWidgets.COPYRIGHT_KEY;
        }
        return message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
    }
}
