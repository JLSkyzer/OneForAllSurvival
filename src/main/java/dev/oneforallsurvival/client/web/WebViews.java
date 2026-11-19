package dev.oneforallsurvival.client.web;

import dev.oneforallsurvival.client.web.mcef.McefWebView;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Ouvre une page du mod dans le navigateur, si et seulement si MCEF est là et prêt. */
public final class WebViews {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-web");

    private WebViews() {}

    /**
     * Vide si le navigateur n'est pas prêt ({@link WebAvailability#failure()} dit pourquoi) ou si la création lève (le
     * pont reçoit alors {@link WebFailure#CREATE_FAILED}). Seules les adresses de {@link WebPages#isAllowed} s'ouvrent.
     */
    public static Optional<WebView> open(String url, WebBridge bridge) {
        if (!WebPages.isAllowed(url)) {
            throw new IllegalArgumentException("adresse refusée : " + url);
        }
        if (!WebAvailability.ready()) {
            return Optional.empty();
        }
        try {
            return Optional.of(McefWebView.create(url, bridge));
        } catch (RuntimeException | LinkageError e) {
            LOG.error("[web] création du navigateur impossible pour {}", url, e);
            bridge.onFailure(WebFailure.CREATE_FAILED);
            return Optional.empty();
        }
    }
}
