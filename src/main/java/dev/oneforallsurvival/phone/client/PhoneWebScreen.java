package dev.oneforallsurvival.phone.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oneforallsurvival.client.web.WebBridge;
import dev.oneforallsurvival.client.web.WebFailure;
import dev.oneforallsurvival.client.web.WebFallbackNotice;
import dev.oneforallsurvival.client.web.WebPages;
import dev.oneforallsurvival.client.web.WebScreen;
import dev.oneforallsurvival.client.web.WebScript;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import java.util.Map;
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
    private Map<String, int[]> rects = Map.of();
    private boolean byServer;
    private boolean toFallback;

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
        if (isReady()) {
            render();
        }
    }

    @Override
    public void closedByServer() {
        byServer = true;
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

    @Override
    protected void onFallback(WebFailure failure) {
        toFallback = true;
        WebFallbackNotice.warnOnce(failure);
        minecraft.setScreen(new PhoneFallbackScreen(state));
    }

    @Override
    public void removed() {
        super.removed();
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
            case "ready" -> render();
            case "layout" -> rects = PhoneBridgeMessages.layout(message);
            case "close" -> onClose();
            default -> PhoneBridgeMessages.toAction(message, state.session()).ifPresent(PhoneClient::send);
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
