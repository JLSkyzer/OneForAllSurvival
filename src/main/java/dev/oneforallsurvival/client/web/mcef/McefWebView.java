package dev.oneforallsurvival.client.web.mcef;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.oneforallsurvival.client.web.WebBridge;
import dev.oneforallsurvival.client.web.WebView;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.cef.browser.CefBrowser;
import org.joml.Matrix4f;

/**
 * Une page dans un navigateur MCEF hors écran, transparent. Le navigateur est celui que fait {@code MCEF.createBrowser}
 * (même constructeur, {@code setCloseAllowed}, {@code createImmediately}), dans une sous-classe qui note la première
 * image peinte : MCEF crée la texture dès la construction, son identifiant ne dit donc pas si la page a peint.
 */
public final class McefWebView implements WebView {

    private final Browser browser;
    private final String startUrl;
    private final WebBridge bridge;
    private volatile double zoomLevel;
    private boolean closed;

    private McefWebView(String url, WebBridge bridge) {
        McefHooks.install();
        this.startUrl = url;
        this.bridge = bridge;
        this.browser = new Browser(url);
        // Inscrit avant la création, pour qu'aucun rappel de la page ne parte vers une vue inconnue ; défait si elle lève.
        McefHooks.register(browser, this);
        CreateGuard.run(() -> {
            browser.setCloseAllowed();
            browser.createImmediately();
        }, () -> {
            closed = true;
            McefHooks.unregister(browser);
            browser.close();
        });
    }

    public static McefWebView create(String url, WebBridge bridge) {
        return new McefWebView(url, bridge);
    }

    String startUrl() {
        return startUrl;
    }

    WebBridge bridge() {
        return bridge;
    }

    /** Remet l'agrandissement voulu : Chromium peut le perdre à chaque chargement de page. */
    void reapplyZoom() {
        browser.setZoomLevel(zoomLevel);
    }

    @Override
    public void resize(int pixelWidth, int pixelHeight) {
        if (pixelWidth > 0 && pixelHeight > 0) {
            browser.resize(pixelWidth, pixelHeight);
        }
    }

    @Override
    public void setScale(double scale) {
        double s = Double.isFinite(scale) && scale > 0 ? scale : 1.0;
        zoomLevel = Math.log(s) / Math.log(1.2);
        browser.setZoomLevel(zoomLevel);
    }

    @Override
    public void draw(GuiGraphics g, int x, int y, int width, int height) {
        if (!browser.painted) {
            return;
        }
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        // Chromium peint en alpha prémultiplié : coins transparents sans liseré sombre.
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
        Tesselator t = Tesselator.getInstance();
        BufferBuilder b = t.getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(m, x, y + height, 0).uv(0f, 1f).color(255, 255, 255, 255).endVertex();
        b.vertex(m, x + width, y + height, 0).uv(1f, 1f).color(255, 255, 255, 255).endVertex();
        b.vertex(m, x + width, y, 0).uv(1f, 0f).color(255, 255, 255, 255).endVertex();
        b.vertex(m, x, y, 0).uv(0f, 0f).color(255, 255, 255, 255).endVertex();
        t.end();
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    @Override
    public void mouseMove(int px, int py) {
        browser.sendMouseMove(px, py);
    }

    @Override
    public void mousePress(int px, int py, int button) {
        browser.sendMousePress(px, py, button);
        browser.setFocus(true);
    }

    @Override
    public void mouseRelease(int px, int py, int button) {
        browser.sendMouseRelease(px, py, button);
    }

    @Override
    public void mouseWheel(int px, int py, double amount) {
        // Avec Ctrl, MCEF change le zoom de la page : jamais de modificateur ici.
        browser.sendMouseWheel(px, py, amount, 0);
    }

    @Override
    public void keyPress(int key, int scan, int modifiers) {
        browser.sendKeyPress(key, scan, modifiers);
    }

    @Override
    public void keyRelease(int key, int scan, int modifiers) {
        browser.sendKeyRelease(key, scan, modifiers);
    }

    @Override
    public void charTyped(char c, int modifiers) {
        browser.sendKeyTyped(c, modifiers);
    }

    @Override
    public void focus(boolean focused) {
        browser.setFocus(focused);
    }

    @Override
    public void run(String script) {
        if (!closed) {
            browser.executeJavaScript(script, browser.getURL(), 0);
        }
    }

    @Override
    public boolean painted() {
        return browser.painted;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        McefHooks.unregister(browser);
        browser.close();
    }

    /** Le navigateur de MCEF, qui retient sa première image peinte. */
    private static final class Browser extends MCEFBrowser {
        private volatile boolean painted;

        Browser(String url) {
            super(MCEF.getClient(), url, true);
        }

        @Override
        public void onPaint(CefBrowser source, boolean popup, Rectangle[] dirty, ByteBuffer buffer, int width, int height) {
            super.onPaint(source, popup, dirty, buffer, width, height);
            if (!popup && width > 0 && height > 0) {
                painted = true;
            }
        }
    }
}
