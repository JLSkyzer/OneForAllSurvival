package dev.oneforallsurvival.client.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Écran qui montre une page du mod dans un cadre. La vue est créée au premier {@code init} à la taille du cadre en
 * pixels réels (cadre x échelle d'interface), mise à l'échelle par {@link #scale()}. La souris n'est relayée que dans le
 * cadre (le relâchement toujours), le clavier toujours sauf Échap, qui ferme. Sans signal {@code {"a":"ready"}} de la
 * page dans les {@value #READY_TICKS} ticks, ou si le navigateur ne sert pas, la sous-classe passe au repli
 * ({@link #onFallback}, une seule fois). Le jeu n'est jamais mis en pause.
 */
public abstract class WebScreen extends Screen {

    /** Délai du signal {@code ready}, en ticks de client. */
    public static final int READY_TICKS = 100;

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }

    private final String page;
    private final WebBridge bridge;
    private WebView view;
    private Rect frame = new Rect(0, 0, 0, 0);
    private boolean ready;
    private boolean closed;
    private final FallbackGate gate = new FallbackGate(this::onFallback, task -> minecraft.tell(task));
    private int ticks;

    protected WebScreen(Component title, String page, WebBridge bridge) {
        super(title);
        this.page = page;
        this.bridge = bridge;
    }

    /** Le cadre de la page, en coordonnées d'interface. */
    protected abstract Rect frame(int guiWidth, int guiHeight);

    /**
     * Le navigateur ne sert pas : la sous-classe montre son interface classique (appelé une fois au plus, jamais après la
     * fermeture). Un repli décidé pendant {@code init()} n'est appelé qu'à la tâche suivante du client
     * ({@code minecraft.tell}) : la sous-classe peut donc changer d'écran ici.
     */
    protected abstract void onFallback(WebFailure failure);

    /** Dessiné sous la page (bordure, fond). */
    protected void drawFrame(GuiGraphics g, Rect frame) {}

    /** Agrandissement de la page (1 = un pixel CSS par pixel réel). */
    protected double scale() {
        return 1.0;
    }

    protected final void run(String script) {
        if (view != null && !closed) {
            view.run(script);
        }
    }

    /** Vrai une fois le signal {@code ready} reçu. */
    protected final boolean isReady() {
        return ready;
    }

    /** Le cadre courant, en coordonnées d'interface. */
    protected final Rect currentFrame() {
        return frame;
    }

    @Override
    protected void init() {
        gate.beginInit();
        try {
            initView();
        } finally {
            gate.endInit();
        }
    }

    private void initView() {
        frame = frame(width, height);
        if (gate.fellBack()) {
            return;
        }
        if (view == null) {
            WebFailure absent = WebAvailability.failure();
            if (absent != null) {
                fallback(absent);
                return;
            }
            Optional<WebView> opened = WebViews.open(page, new Relay());
            if (opened.isEmpty()) {
                fallback(WebFailure.CREATE_FAILED);
                return;
            }
            view = opened.get();
            view.setScale(scale());
            view.focus(true);
        }
        view.resize(pixels(frame.width()), pixels(frame.height()));
        view.setScale(scale());
    }

    private int pixels(double gui) {
        return (int) Math.round(gui * minecraft.getWindow().getGuiScale());
    }

    private int px(double mouseX) {
        return (int) ((mouseX - frame.x()) * minecraft.getWindow().getGuiScale());
    }

    private int py(double mouseY) {
        return (int) ((mouseY - frame.y()) * minecraft.getWindow().getGuiScale());
    }

    private void fallback(WebFailure failure) {
        if (closed || gate.fellBack()) {
            return;
        }
        if (view != null) {
            view.close();
            view = null;
        }
        gate.fail(failure);
    }

    @Override
    public void tick() {
        super.tick();
        if (view != null && !ready && !gate.fellBack() && ++ticks >= READY_TICKS) {
            fallback(WebFailure.NO_READY);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawFrame(g, frame);
        if (view != null && view.painted()) {
            view.draw(g, frame.x(), frame.y(), frame.width(), frame.height());
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (view != null && frame.contains(mouseX, mouseY)) {
            view.mouseMove(px(mouseX), py(mouseY));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (view != null && frame.contains(mouseX, mouseY)) {
            view.mousePress(px(mouseX), py(mouseY), button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (view != null) {
            view.mouseRelease(px(mouseX), py(mouseY), button);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (view != null && frame.contains(mouseX, mouseY)) {
            view.mouseWheel(px(mouseX), py(mouseY), delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (view != null) {
            view.keyPress(key, scan, modifiers);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (view != null && key != GLFW.GLFW_KEY_ESCAPE) {
            view.keyRelease(key, scan, modifiers);
            return true;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (view != null) {
            view.charTyped(c, modifiers);
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        closed = true;
        gate.close();
        if (view != null) {
            view.close();
            view = null;
        }
        super.removed();
    }

    /** Le pont vu par la vue : note le signal {@code ready}, passe au repli sur échec, relaie le reste. */
    private final class Relay implements WebBridge {
        @Override
        public void onMessage(JsonObject message) {
            if (closed || gate.fellBack()) {
                return;
            }
            JsonElement a = message.get("a");
            if (a != null && "ready".equals(a.getAsString())) {
                ready = true;
            }
            bridge.onMessage(message);
        }

        @Override
        public void onFailure(WebFailure failure) {
            if (closed) {
                return;
            }
            bridge.onFailure(failure);
            fallback(failure);
        }
    }
}
