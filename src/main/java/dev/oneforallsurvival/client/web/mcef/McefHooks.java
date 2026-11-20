package dev.oneforallsurvival.client.web.mcef;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFClient;
import dev.oneforallsurvival.client.web.WebFailure;
import dev.oneforallsurvival.client.web.WebMessage;
import dev.oneforallsurvival.client.web.WebPages;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.network.CefRequest;

/**
 * Les gestionnaires du client partagé de MCEF, posés une fois par session de jeu. Ils ne s'ajoutent qu'aux listes de
 * {@link MCEFClient} (chargement, affichage) et un routeur de messages à nom propre ({@code oneforallQuery}) : rien ne
 * remplace un gestionnaire à emplacement unique, dont WebDisplays se sert. Chaque rappel ne sert que les navigateurs
 * inscrits par {@link #register} et laisse passer les autres.
 *
 * <p>Les rappels de CEF tournent sur le fil du rendu, dans la boucle de messages de CEF : ce qu'ils déclenchent dans le
 * jeu passe par {@code Minecraft.tell} (tâche suivante), jamais par {@code execute}, qui l'exécuterait sur place.
 * Fermer un navigateur depuis son propre rappel fait planter la JVM (relevé en jeu le 2026-11-20 : page absente,
 * {@code onLoadError} puis repli, {@code CefBrowser_N.N_Close} dans {@code DoMessageLoopWork}).
 */
final class McefHooks {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-web");
    private static final Map<CefBrowser, McefWebView> VIEWS = new ConcurrentHashMap<>();
    private static boolean installed;

    private McefHooks() {}

    /** Appelé sur le fil du client, à la première création d'une vue. Le routeur n'est jamais retiré. */
    static synchronized void install() {
        if (installed) {
            return;
        }
        MCEFClient client = MCEF.getClient();
        client.addLoadHandler(new LoadHandler());
        client.addDisplayHandler(new DisplayHandler());
        CefMessageRouter router = CefMessageRouter.create(
                new CefMessageRouter.CefMessageRouterConfig("oneforallQuery", "oneforallQueryCancel"), new QueryHandler());
        client.getHandle().addMessageRouter(router);
        installed = true;
        LOG.info("[web] gestionnaires de MCEF posés (chargement, affichage, routeur oneforallQuery)");
    }

    static void register(CefBrowser browser, McefWebView view) {
        VIEWS.put(browser, view);
    }

    static void unregister(CefBrowser browser) {
        VIEWS.remove(browser);
    }

    private static McefWebView mainFrameView(CefBrowser browser, CefFrame frame) {
        if (browser == null || frame == null || !frame.isMain()) {
            return null;
        }
        return VIEWS.get(browser);
    }

    private static final class QueryHandler extends CefMessageRouterHandlerAdapter {
        @Override
        public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent,
                CefQueryCallback callback) {
            McefWebView view = mainFrameView(browser, frame);
            if (view == null) {
                return false;
            }
            if (!WebPages.isAllowed(frame.getURL())) {
                LOG.warn("[web] message d'une page non permise ignoré : {}", frame.getURL());
                callback.failure(403, "page non permise");
                return true;
            }
            callback.success("");
            WebMessage.parse(request).ifPresentOrElse(
                    message -> Minecraft.getInstance().tell(() -> {
                        if (VIEWS.containsValue(view)) {
                            view.bridge().onMessage(message);
                        }
                    }),
                    () -> LOG.warn("[web] message refusé ({} caractères)", request == null ? 0 : request.length()));
            return true;
        }
    }

    private static final class LoadHandler extends CefLoadHandlerAdapter {
        /**
         * Chromium garde l'agrandissement par hôte : une page ouverte après une autre de l'hôte {@code oneforallsurvival}
         * part avec l'agrandissement de la précédente (relevé en jeu le 2026-11-19). Il est remis dès le début du
         * chargement, puis à la fin.
         */
        @Override
        public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
            McefWebView view = mainFrameView(browser, frame);
            if (view != null) {
                view.reapplyZoom();
            }
        }

        @Override
        public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
            McefWebView view = mainFrameView(browser, frame);
            if (view != null) {
                LOG.info("[web] page chargée {} ({})", frame.getURL(), httpStatusCode);
                view.reapplyZoom();
            }
        }

        @Override
        public void onLoadError(CefBrowser browser, CefFrame frame, CefLoadHandler.ErrorCode errorCode, String errorText,
                String failedUrl) {
            McefWebView view = mainFrameView(browser, frame);
            if (view == null || errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) {
                return;
            }
            LOG.warn("[web] échec du chargement de {} : {} {}", failedUrl, errorCode, errorText);
            Minecraft.getInstance().tell(() -> {
                if (VIEWS.containsValue(view)) {
                    view.bridge().onFailure(WebFailure.LOAD_ERROR);
                }
            });
        }
    }

    private static final class DisplayHandler extends CefDisplayHandlerAdapter {
        @Override
        public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
            McefWebView view = mainFrameView(browser, frame);
            if (view != null && !WebPages.isAllowed(url)) {
                LOG.warn("[web] adresse non permise {} : retour à {}", url, view.startUrl());
                // Jamais depuis le rappel lui-même : à la tâche suivante, si la vue est encore inscrite (ni fermée ni
                // passée au repli, qui la ferment et la désinscrivent).
                Minecraft.getInstance().tell(() -> {
                    if (VIEWS.get(browser) == view) {
                        browser.loadURL(view.startUrl());
                    }
                });
            }
        }

        @Override
        public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source,
                int line) {
            if (VIEWS.containsKey(browser)) {
                LOG.info("[web] console {} : {} ({}:{})", level, message, source, line);
            }
            return false;
        }
    }
}
