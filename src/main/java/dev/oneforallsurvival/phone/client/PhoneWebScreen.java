package dev.oneforallsurvival.phone.client;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.client.web.WebBridge;
import dev.oneforallsurvival.client.web.WebFailure;
import dev.oneforallsurvival.client.web.WebFallbackNotice;
import dev.oneforallsurvival.client.web.WebPages;
import dev.oneforallsurvival.client.web.WebScreen;
import dev.oneforallsurvival.client.web.WebScript;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.net.OperatorActionPacket;
import dev.oneforallsurvival.phone.net.OperatorNoticePacket;
import dev.oneforallsurvival.phone.net.OperatorView;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Le téléphone dans le navigateur du jeu : la page {@code phone/index.html} (propriété de développement
 * {@code oneforallsurvival.phonePage} pour une autre page du mod) dans le cadre de {@link PhoneFrameLayout}, entourée de
 * {@code phone_frame.png}. Chaque état du serveur est passé à {@code window.phone.render} ; chaque message de la page
 * est relu par {@link PhoneBridgeMessages} avant de partir. La dernière mise en page reçue ({@code layout}, en px CSS)
 * est gardée pour un automate ({@link #rects()}). Si le navigateur ne sert pas, l'écran de repli prend la suite sur le
 * même état. Aucun message de la page n'est écrit au journal (il peut contenir un code).
 *
 * <p>Appli « Antenne » (lot T2, tâche 10) : chaque vue de source {@code PHONE} part à {@code window.phone.antenna}
 * ({@link OperatorViewJson}), {@code null} à la fermeture par le serveur ; le retour d'un geste à
 * {@code window.phone.antennaStatus} ; les gestes {@code op*} de la page sont relus par {@link AntennaBridgeMessages} au
 * numéro du panneau que cet écran tient, jamais à celui de la page.
 *
 * <p>Lot T3, tâche 4b : chaque état d'appel part à {@code window.phone.call} et son carnet à {@code window.phone.directory}
 * ({@link PhoneCallJson}) ; les gestes d'appel et de carnet de la page sont relus par {@link CallBridgeMessages}, le
 * numéro de l'appel visé pris au dernier état reçu.
 *
 * <p>Retours d'essai du 27/11 : les touches du téléphone sont traduites ici ({@link PhoneKeys#press}) et données à la
 * page par {@code window.phone.key}, jamais aussi au navigateur ; un champ de texte qui a la main (message
 * {@code textFocus} de la page, relu par {@link PhoneBridgeMessages#textFocus}) garde sa saisie. Les touches « parler »
 * et « chuchoter » du voice chat ne sont pas prises : l'événement clavier de Forge part, que Simple Voice Chat écoute.
 * Rien de ce qui est tapé n'est écrit au journal.
 */
public final class PhoneWebScreen extends WebScreen implements PhoneClient.Shown {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-phone");
    static final ResourceLocation FRAME = new ResourceLocation("oneforallsurvival", "textures/gui/phone_frame.png");
    /** Taille de {@code phone_frame.png} et de son bord (l'écran fait 112 x 244 au milieu). */
    static final int FRAME_TEX_W = 124;
    static final int FRAME_TEX_H = 256;
    static final int FRAME_TEX_BORDER = 6;
    private static final String PAGE_PROPERTY = "oneforallsurvival.phonePage";
    private static final String DEFAULT_PAGE = "phone/index.html";

    private PhoneStatePacket state;
    /** L'heure locale ({@code System.currentTimeMillis()}) à la réception de {@link #state}, pour l'heure du serveur. */
    private long stateAt = System.currentTimeMillis();
    /** Lot T3 : le dernier état d'appel reçu (nul avant le premier). */
    @Nullable private PhoneCallStatePacket call = PhoneClient.lastCall().orElse(null);
    private Map<String, int[]> rects = Map.of();
    private boolean byServer;
    private boolean toFallback;
    /** Le panneau de l'appli « Antenne » montré par la page (son numéro), et sa dernière vue. */
    private final AntennaSession antenna = new AntennaSession();
    @Nullable private OperatorView antennaView;
    /** Un champ de texte de la page a la main (dernier {@code textFocus} reçu). */
    private boolean textFocus;
    private final PhoneKeys.Held held = new PhoneKeys.Held();

    public PhoneWebScreen(PhoneStatePacket state) {
        this(state, new Bridge());
    }

    private PhoneWebScreen(PhoneStatePacket state, Bridge bridge) {
        super(Component.translatable("item.oneforallsurvival.phone"), page(), bridge);
        this.state = state;
        bridge.screen = this;
    }

    /** La page ouverte : celle du téléphone, ou une autre page du mod donnée par la propriété de développement. */
    static String page() {
        String asked = System.getProperty(PAGE_PROPERTY);
        if (asked != null && !asked.isBlank()) {
            try {
                return WebPages.url(asked.trim());
            } catch (IllegalArgumentException e) {
                LOG.warn("[telephone] page de développement refusée : {}", asked);
            }
        }
        return WebPages.url(DEFAULT_PAGE);
    }

    // --- PhoneClient.Shown -----------------------------------------------------------------------------------------

    @Override
    public int session() {
        return state.session();
    }

    @Override
    public void show(PhoneStatePacket next) {
        state = next;
        stateAt = System.currentTimeMillis();
        if (isReady()) {
            render();
        }
    }

    @Override
    public void closedByServer() {
        byServer = true;
    }

    /**
     * Lot T3, tâche 4b : l'état d'appel part à {@code window.phone.call} ({@link PhoneCallJson}, un appel porté par un
     * autre téléphone y arrive vide), le carnet qu'il porte à {@code window.phone.directory}.
     */
    @Override
    public void showCall(PhoneCallStatePacket next) {
        call = next;
        if (isReady()) {
            pushCall(next);
        }
    }

    /**
     * Retours d'essai du 27/11 : la liste « Partager mon numéro » part à {@code window.phone.share}
     * ({@link PhoneCallJson#share}).
     */
    @Override
    public void showShare(dev.oneforallsurvival.phone.call.PhoneSharePacket share) {
        if (isReady()) {
            run(WebScript.call("phone.share", PhoneCallJson.share(share, share.message().getString())));
        }
    }

    private void pushCall(PhoneCallStatePacket c) {
        run(WebScript.call("phone.call", PhoneCallJson.call(c, serverNow(), c.message().getString())));
        PhoneCallJson.directoryOf(c).ifPresent(d -> run(WebScript.call("phone.directory", d)));
    }

    /** L'heure du serveur estimée : celle du dernier état, plus le temps écoulé ici depuis (aucune horloge à croire). */
    private long serverNow() {
        return state.serverMillis() + Math.max(0L, System.currentTimeMillis() - stateAt);
    }

    /** Le dernier état d'appel reçu par cet écran, ou nul (lu par un automate). */
    @Nullable
    public PhoneCallStatePacket call() {
        return call;
    }

    // --- Appli « Antenne » -----------------------------------------------------------------------------------------

    /** Une vue de l'appli, reçue du serveur. */
    void antenna(OperatorView view) {
        if (antenna.view(view.session()) == AntennaSession.OnView.CLOSE) {
            // « Accueil » touché avant la première vue : ce panneau-là se ferme sans être montré.
            antennaView = null;
            closeAntennaPanel(view.session());
            return;
        }
        antennaView = view;
        if (isReady()) {
            run(WebScript.call("phone.antenna", OperatorViewJson.toJson(view)));
        }
    }

    /** Un mot du serveur pour l'appli : la ligne d'état, ou la fermeture ({@code phone.antenna(null)}). */
    void antennaNotice(OperatorNoticePacket notice) {
        if (notice.session() != antenna.session()) {
            return;
        }
        if (notice.closed()) {
            antenna.serverClosed(notice.session());
            antennaView = null;
            if (isReady()) {
                run(WebScript.call("phone.antenna", JsonNull.INSTANCE));
            }
        } else if (isReady()) {
            run(WebScript.call("phone.antennaStatus", new JsonPrimitive(notice.message().getString())));
        }
    }

    /** La dernière vue de l'appli reçue, ou nulle (lu par un automate). */
    @Nullable
    public OperatorView antennaView() {
        return antennaView;
    }

    /** Le numéro du panneau de l'appli ouvert, -1 sans appli (lu par un automate). */
    public int antennaSession() {
        return antenna.session();
    }

    // --- Lu par un automate (tâche 11) -----------------------------------------------------------------------------

    /** Le dernier état montré. */
    public PhoneStatePacket state() {
        return state;
    }

    /** Les boîtes de la dernière mise en page reçue, en px CSS ({@code [x, y, largeur, hauteur]}), par identifiant. */
    public Map<String, int[]> rects() {
        return rects;
    }

    /** Le cadre de la page, en coordonnées d'interface. */
    public Rect frameRect() {
        return currentFrame();
    }

    /** Vrai une fois le signal {@code ready} de la page reçu. */
    public boolean pageReady() {
        return isReady();
    }

    /** L'agrandissement de la page : px CSS vers pixels réels. */
    public double pageScale() {
        return scale();
    }

    // --- WebScreen -------------------------------------------------------------------------------------------------

    @Override
    protected Rect frame(int guiWidth, int guiHeight) {
        return PhoneFrameLayout.fit(guiWidth, guiHeight);
    }

    @Override
    protected double scale() {
        return PhoneFrameLayout.scale((int) Math.round(currentFrame().width() * minecraft.getWindow().getGuiScale()));
    }

    @Override
    protected void drawFrame(GuiGraphics g, Rect frame) {
        // Le verre du cadre est transparent : du noir dessous, que les coins arrondis de la page laissent voir.
        g.fill(frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height(), 0xFF000000);
        drawBezel(g, frame);
    }

    /**
     * Le cadre du téléphone autour de {@code screen} : bord proportionnel à la largeur de l'écran. Son verre est
     * transparent : posé par-dessus un fond, il en arrondit les coins.
     */
    static void drawBezel(GuiGraphics g, Rect screen) {
        int border = Math.max(2, (int) Math.round(screen.width() * FRAME_TEX_BORDER
                / (double) (FRAME_TEX_W - 2 * FRAME_TEX_BORDER)));
        RenderSystem.enableBlend();
        g.blit(FRAME, screen.x() - border, screen.y() - border, screen.width() + 2 * border,
                screen.height() + 2 * border, 0, 0, FRAME_TEX_W, FRAME_TEX_H, FRAME_TEX_W, FRAME_TEX_H);
        RenderSystem.disableBlend();
    }

    /**
     * Vrai si cette touche est liée à « parler » ou « chuchoter » de Simple Voice Chat ({@link PhoneKeys#VOICE_MAPPINGS}),
     * telle que le joueur l'a réglée. Une liaison à la souris ne répond jamais : la souris passe déjà par un événement de
     * Forge publié avant l'écran.
     */
    static boolean voiceKey(net.minecraft.client.Minecraft mc, int key, int scan) {
        if (mc == null || mc.options == null) {
            return false;
        }
        for (net.minecraft.client.KeyMapping mapping : mc.options.keyMappings) {
            if (PhoneKeys.isVoiceMapping(mapping.getName()) && mapping.matches(key, scan)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(key, scan, modifiers);
        }
        PhoneKeys.Decision d = PhoneKeys.press(key, modifiers, voiceKey(minecraft, key, scan), textFocus);
        switch (d.route()) {
            case VOICE -> {
                return false;
            }
            case PAGE -> {
                boolean repeat = held.press(key);
                if (isReady()) {
                    run(WebScript.call("phone.key", new JsonPrimitive(d.key()), new JsonPrimitive(repeat)));
                }
                return true;
            }
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        held.release(key);
        if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && voiceKey(minecraft, key, scan)) {
            return false;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    protected void onFallback(WebFailure failure) {
        toFallback = true;
        WebFallbackNotice.warnOnce(failure);
        minecraft.setScreen(new PhoneFallbackScreen(state));
    }

    @Override
    public void removed() {
        super.removed();
        antenna.removed().ifPresent(this::closeAntennaPanel);
        if (!byServer && !toFallback) {
            PhoneClient.sendClose(state.session());
        }
    }

    private void render() {
        run(WebScript.call("phone.render", PhoneViewJson.toJson(state)));
    }

    private void onPageMessage(JsonObject message) {
        String a = message.get("a").getAsString();
        switch (a) {
            case "ready" -> {
                textFocus = false; // page (re)chargée : aucun champ n'a la main
                held.clear();
                render();
                if (call != null && call.here() && call.phase() != PhoneCallStatePacket.Phase.NONE) {
                    // Un appel en cours au chargement de la page : montré tout de suite (sans carnet, l'appli le demande).
                    run(WebScript.call("phone.call", PhoneCallJson.call(call, serverNow(), "")));
                }
                // Page (re)chargée : elle ne montre plus l'appli, le panneau encore ouvert se ferme (revue, mineur 4).
                antennaView = null;
                antenna.ready().ifPresent(this::closeAntennaPanel);
            }
            case "layout" -> rects = PhoneBridgeMessages.layout(message);
            case "textFocus" -> PhoneBridgeMessages.textFocus(message).ifPresent(on -> textFocus = on);
            case "close" -> onClose();
            default -> {
                if (AntennaBridgeMessages.isAntenna(a)) {
                    onAntennaMessage(message);
                } else if (CallBridgeMessages.isCall(a)) {
                    // Le numéro de l'appel visé vient du dernier état reçu ici, jamais de la page.
                    CallBridgeMessages.toAction(message, state.session(), call).ifPresent(this::sendToServer);
                } else {
                    PhoneBridgeMessages.toAction(message, state.session()).ifPresent(p -> {
                        if (p.action() == dev.oneforallsurvival.phone.PhoneActionPacket.Action.OPEN_OPERATOR_APP) {
                            antenna.appRequested();
                        }
                        PhoneClient.send(p);
                    });
                }
            }
        }
    }

    /** Un geste de l'appli, relu ; {@code opClose} ferme le panneau montré, ou le prochain s'il n'est pas encore là. */
    private void onAntennaMessage(JsonObject message) {
        java.util.Optional<OperatorActionPacket> relu = AntennaBridgeMessages.toAction(message, antenna.session());
        if (relu.isEmpty()) {
            return;
        }
        if (relu.get().action() == OperatorActionPacket.Action.CLOSE) {
            antennaView = null;
            antenna.pageClose().ifPresent(this::closeAntennaPanel);
        } else if (antenna.session() >= 0) {
            sendAntenna(relu.get());
        }
    }

    /** Ferme côté serveur le panneau de ce numéro ; une vue de ce numéro en route ne le rouvre pas. */
    private void closeAntennaPanel(int number) {
        PhoneClient.antennaClosed(number);
        sendAntenna(new OperatorActionPacket(number, OperatorActionPacket.Action.CLOSE, "", 0L));
    }

    private void sendAntenna(OperatorActionPacket packet) {
        sendToServer(packet);
    }

    private void sendToServer(Object packet) {
        if (minecraft != null && minecraft.getConnection() != null) {
            BenchNetwork.CHANNEL.sendToServer(packet);
        }
    }

    private static final class Bridge implements WebBridge {
        private PhoneWebScreen screen;

        @Override
        public void onMessage(JsonObject message) {
            if (screen != null) {
                screen.onPageMessage(message);
            }
        }
    }
}
