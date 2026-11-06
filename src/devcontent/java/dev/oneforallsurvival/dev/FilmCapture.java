package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Capture des films de développement (jamais empaquetée) : le jeu en haut (1280 x 656), un bandeau de
 * 64 pixels dessous avec la légende et les valeurs lues en direct, à 10 images par seconde dans un dossier.
 */
final class FilmCapture {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final int BANDEAU = 64;
    private static final long INTERVALLE_NS = 100_000_000L;

    private final Path dossier;
    private final String titre;
    private final ThreadPoolExecutor ecrivains = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(24), new ThreadPoolExecutor.CallerRunsPolicy());
    private final long debutNs = System.nanoTime();
    private long derniereNs;
    private int numero;

    FilmCapture(Path dossier, String titre) throws IOException {
        this.dossier = dossier;
        this.titre = titre;
        Files.createDirectories(dossier);
    }

    int images() {
        return numero;
    }

    /** Une image si 100 ms se sont écoulées depuis la précédente. */
    void capturer(Minecraft mc, String legende, String valeurs) {
        long maintenant = System.nanoTime();
        if (derniereNs != 0 && maintenant - derniereNs < INTERVALLE_NS) {
            return;
        }
        derniereNs = derniereNs == 0 ? maintenant : derniereNs + INTERVALLE_NS;
        if (maintenant - derniereNs > 5 * INTERVALLE_NS) {
            derniereNs = maintenant;
        }
        long ecoule = (maintenant - debutNs) / 1_000_000_000L;
        int n = ++numero;
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int l = image.getWidth();
            int h = image.getHeight();
            int[] pixels = new int[l * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < l; x++) {
                    pixels[y * l + x] = image.getPixelRGBA(x, y);
                }
            }
            Path fichier = dossier.resolve(String.format("f%05d.png", n));
            ecrivains.execute(() -> ecrire(fichier, pixels, l, h, legende, valeurs, ecoule));
        }
    }

    private void ecrire(Path fichier, int[] abgr, int l, int h, String legende, String valeurs, long ecoule) {
        BufferedImage img = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        int[] rgb = new int[abgr.length];
        for (int i = 0; i < abgr.length; i++) {
            int p = abgr[i];
            rgb[i] = ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
        }
        img.setRGB(0, 0, Math.min(l, 1280), Math.min(h, 720 - BANDEAU), rgb, 0, l);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int y0 = 720 - BANDEAU;
            g.setColor(new Color(0x14, 0x1B, 0x25));
            g.fillRect(0, y0, 1280, BANDEAU);
            g.setColor(new Color(0xE0, 0x9A, 0x2B));
            g.fillRect(0, y0, 1280, 3);
            g.setFont(new Font("Segoe UI", Font.BOLD, 23));
            g.setColor(Color.WHITE);
            g.drawString(legende, 16, y0 + 28);
            g.setFont(new Font("Consolas", Font.PLAIN, 17));
            g.setColor(new Color(0xBF, 0xE3, 0xFF));
            g.drawString(valeurs, 16, y0 + 54);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            g.setColor(new Color(0x9A, 0xA7, 0xB5));
            g.drawString(titre + " | " + String.format("%02d:%02d", ecoule / 60, ecoule % 60), 1280 - 400, y0 + 24);
        } finally {
            g.dispose();
        }
        try {
            ImageIO.write(img, "png", fichier.toFile());
        } catch (IOException ex) {
            LOG.error("[film] image non écrite : {}", fichier, ex);
        }
    }

    /** Attend les écritures et dépose {@code termine.json}. */
    void terminer(String echec) {
        ecrivains.shutdown();
        try {
            ecrivains.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        String e = echec == null ? "null" : "\"" + echec.replace('"', '\'') + "\"";
        try {
            Files.writeString(dossier.resolve("termine.json"),
                    "{\"images\":" + numero + ",\"fps\":10,\"echec\":" + e + "}", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOG.error("[film] termine.json non écrit", ex);
        }
    }
}
