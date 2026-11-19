package dev.oneforallsurvival.dev;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.oneforallsurvival.client.web.WebAvailability;
import dev.oneforallsurvival.client.web.WebBridge;
import dev.oneforallsurvival.client.web.WebFailure;
import dev.oneforallsurvival.client.web.WebFallbackNotice;
import dev.oneforallsurvival.client.web.WebPages;
import dev.oneforallsurvival.client.web.WebScreen;
import dev.oneforallsurvival.client.web.WebScript;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Commande cliente de développement {@code /webessai [échelle]} : ouvre la page d'essai du socle navigateur
 * ({@code web/selftest.html}) en plein cadre. Le pont répond à chaque message par {@code selftest.echo(message)} et
 * l'écrit au journal ({@code [web] reçu …}). Sans navigateur : aucun écran, l'avis de repli (une fois par session).
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WebEssai {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-web");

    /** Messages reçus depuis le lancement, lus par {@link WebEssaiShots}. */
    static final List<JsonObject> RECUS = new CopyOnWriteArrayList<>();
    /** Replis vus depuis le lancement. */
    static final List<WebFailure> REPLIS = new CopyOnWriteArrayList<>();

    private WebEssai() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("webessai")
                .executes(c -> ouvrir(1.0))
                .then(Commands.argument("echelle", DoubleArgumentType.doubleArg(0.25, 4.0))
                        .executes(c -> ouvrir(DoubleArgumentType.getDouble(c, "echelle")))));
    }

    static int ouvrir(double echelle) {
        Minecraft mc = Minecraft.getInstance();
        WebFailure absent = WebAvailability.failure();
        if (absent != null) {
            REPLIS.add(absent);
            WebFallbackNotice.warnOnce(absent);
            return 0;
        }
        // Après la fermeture du chat, qui remettrait l'écran à vide.
        mc.tell(() -> mc.setScreen(new Ecran(echelle)));
        return 1;
    }

    /** L'écran d'essai : plein cadre, échelle donnée, pont en écho. */
    static final class Ecran extends WebScreen {
        private final double echelle;

        Ecran(double echelle) {
            this(echelle, new Pont());
        }

        private Ecran(double echelle, Pont pont) {
            super(Component.literal("Essai du navigateur"), WebPages.url("web/selftest.html"), pont);
            this.echelle = echelle;
            pont.ecran = this;
        }

        @Override
        protected Rect frame(int guiWidth, int guiHeight) {
            return new Rect(0, 0, guiWidth, guiHeight);
        }

        @Override
        protected double scale() {
            return echelle;
        }

        @Override
        protected void drawFrame(GuiGraphics g, Rect frame) {}

        @Override
        protected void onFallback(WebFailure failure) {
            REPLIS.add(failure);
            WebFallbackNotice.warnOnce(failure);
            onClose();
        }

        void echo(JsonObject message) {
            run(WebScript.call("selftest.echo", message));
        }

        Rect cadre() {
            return currentFrame();
        }

        boolean pret() {
            return isReady();
        }
    }

    private static final class Pont implements WebBridge {
        private Ecran ecran;

        @Override
        public void onMessage(JsonObject message) {
            LOG.info("[web] reçu {}", message);
            RECUS.add(message);
            if (ecran != null) {
                ecran.echo(message);
            }
        }

        @Override
        public void onFailure(WebFailure failure) {
            LOG.warn("[web] échec signalé par la vue : {}", failure);
        }
    }
}
