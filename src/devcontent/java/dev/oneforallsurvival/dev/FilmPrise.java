package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Une suite d'images à cadence fixe (30 par seconde, JPEG) dans un dossier, partagée par les films montés
 * ({@link FilmBandeAnnonce}, {@link FilmEconomie} ; développement seulement, jamais empaquetée). La fenêtre du jeu est lue
 * dans son tampon, les légendes et un curseur sont dessinés par dessus ; une image tardive est recopiée pour garder la
 * cadence. {@code plans.csv} note la première image de chaque plan, {@code termine.json} le bilan. {@code legendes} faux :
 * l'image du jeu seule.
 */
final class FilmPrise {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    static final int LARGEUR = 1280;
    static final int HAUTEUR = 720;
    static final long CADENCE_NS = 1_000_000_000L / 30;

    private final Path dossier;
    private final boolean legendes;
    private final ThreadPoolExecutor ecrivains = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(48), new ThreadPoolExecutor.CallerRunsPolicy());
    private long prochaineNs;
    private int numero;
    private int copies;
    private long debutPlanNs;
    private long debutTitreNs;
    private String titre = "";
    private boolean coupe = true;
    private volatile double[] gros;

    FilmPrise(Path dossier, boolean legendes) throws IOException {
        this.dossier = dossier;
        this.legendes = legendes;
        Files.createDirectories(dossier);
        try (Stream<Path> vieux = Files.list(dossier)) {
            for (Path f : vieux.toList()) {
                if (Files.isRegularFile(f)) {
                    Files.deleteIfExists(f);
                }
            }
        }
    }

    int images() {
        return numero;
    }

    void grosPlan(double cx, double cy, double facteur) {
        gros = new double[] {cx, cy, facteur};
    }

    void pleinCadre() {
        gros = null;
    }

    /** Le début d'un pas filmé : noté dans {@code plans.csv}. */
    void plan(String nom, String titrePlan) {
        if (!titrePlan.equals(titre)) {
            titre = titrePlan;
            debutTitreNs = System.nanoTime();
        }
        try {
            Files.writeString(dossier.resolve("plans.csv"), (numero + 1) + ";" + nom + ";" + titrePlan + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("[film] plans.csv non écrit", e);
        }
    }

    /** Rien n'est filmé : la prochaine image ouvre un nouveau plan (fondu d'entrée). */
    void couper() {
        if (!coupe) {
            coupe = true;
            prochaineNs = 0;
        }
    }

    /** Combien d'images écrire maintenant pour garder 30 par seconde (0 : trop tôt) ; le compte est avancé d'autant. */
    private int cadence() {
        long maintenant = System.nanoTime();
        if (coupe) {
            coupe = false;
            debutPlanNs = maintenant;
            prochaineNs = maintenant;
        }
        if (maintenant < prochaineNs) {
            return 0;
        }
        // Images en retard : la même est écrite autant de fois qu'il le faut (au plus 6), pour garder 30 par seconde.
        int fois = 1 + (int) Math.min(5, (maintenant - prochaineNs) / CADENCE_NS);
        prochaineNs += (long) fois * CADENCE_NS;
        if (maintenant - prochaineNs > 6 * CADENCE_NS) {
            prochaineNs = maintenant + CADENCE_NS;
        }
        copies += fois - 1;
        numero += fois;
        return fois;
    }

    void capturer(Minecraft mc, String titrePlan, String legende, double[] curseur, boolean enBas) {
        int fois = cadence();
        if (fois == 0) {
            return;
        }
        int premier = numero - fois + 1;
        // Coupe franche entre deux plans : le seul fondu voulu est celui de la fin (au montage).
        double fondu = 1.0;
        double apparition = Math.min(1.0, (System.nanoTime() - debutTitreNs) / 400_000_000.0);
        float echelle = (float) mc.getWindow().getGuiScale();
        double[] zoom = gros;
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int l = image.getWidth();
            int h = image.getHeight();
            int[] rgb = new int[l * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < l; x++) {
                    int p = image.getPixelRGBA(x, y);
                    rgb[y * l + x] = ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
                }
            }
            ecrivains.execute(() -> ecrire(premier, fois, rgb, l, h, titrePlan, legende, curseur, echelle, fondu, apparition, zoom, enBas));
        }
    }

    /**
     * Un carton titre : fond sombre, numéro et titre de la scène, sous-titre ; {@code avance} (0 à 1) fait entrer le texte
     * en fondu et glisser le filet (le carton n'est jamais une image figée).
     */
    void carton(String numeroScene, String titreScene, String sousTitre, double avance) {
        int fois = cadence();
        if (fois == 0) {
            return;
        }
        int premier = numero - fois + 1;
        ecrivains.execute(() -> ecrireCarton(premier, fois, numeroScene, titreScene, sousTitre, avance));
    }

    private void ecrireCarton(int premier, int fois, String numeroScene, String titreScene, String sousTitre, double avance) {
        BufferedImage img = new BufferedImage(LARGEUR, HAUTEUR, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(10, 16, 24), 0, HAUTEUR, new Color(22, 30, 40)));
            g.fillRect(0, 0, LARGEUR, HAUTEUR);
            double a = Math.min(1.0, avance * 2.5);
            double e = a * a * (3 - 2 * a);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) e));
            int cx = LARGEUR / 2;
            g.setFont(new Font("Segoe UI", Font.BOLD, 24));
            g.setColor(new Color(0xE0, 0x9A, 0x2B));
            centre(g, numeroScene, cx, 300);
            g.setFont(new Font("Segoe UI", Font.BOLD, 54));
            g.setColor(new Color(0xF2, 0xC2, 0x6B));
            centre(g, titreScene, cx, 372);
            int filet = (int) (40 + 220 * e);
            g.setColor(new Color(0xE0, 0x9A, 0x2B));
            g.fillRect(cx - filet / 2, 396, filet, 4);
            if (!sousTitre.isEmpty()) {
                g.setFont(new Font("Segoe UI", Font.PLAIN, 24));
                g.setColor(new Color(230, 234, 240));
                centre(g, sousTitre, cx, 446);
            }
        } finally {
            g.dispose();
        }
        ecrireFois(img, premier, fois);
    }

    private static void centre(Graphics2D g, String texte, int cx, int base) {
        FontMetrics m = g.getFontMetrics();
        g.drawString(texte, cx - m.stringWidth(texte) / 2, base);
    }

    private void ecrire(int premier, int fois, int[] rgb, int l, int h, String titrePlan, String legende, double[] curseur,
            float echelle, double fondu, double apparition, double[] zoom, boolean enBas) {
        BufferedImage img = new BufferedImage(LARGEUR, HAUTEUR, BufferedImage.TYPE_INT_RGB);
        BufferedImage jeu = new BufferedImage(l, h, BufferedImage.TYPE_INT_RGB);
        jeu.setRGB(0, 0, l, h, rgb, 0, l);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (zoom != null) {
                int sw = (int) Math.round(l / zoom[2]);
                int sh = (int) Math.round(h / zoom[2]);
                int sx = (int) Math.max(0, Math.min(l - sw, zoom[0] * l / LARGEUR - sw / 2.0));
                int sy = (int) Math.max(0, Math.min(h - sh, zoom[1] * h / HAUTEUR - sh / 2.0));
                g.drawImage(jeu, 0, 0, LARGEUR, HAUTEUR, sx, sy, sx + sw, sy + sh, null);
            } else {
                g.drawImage(jeu, 0, 0, LARGEUR, HAUTEUR, null);
            }
            if (curseur != null && zoom == null) {
                dessinerCurseur(g, curseur[0] * echelle * LARGEUR / l, curseur[1] * echelle * HAUTEUR / h);
            }
            if (legendes && !titrePlan.isEmpty()) {
                dessinerLegende(g, titrePlan, legende, apparition, enBas);
            }
            if (fondu < 1.0) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1.0 - fondu)));
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, LARGEUR, HAUTEUR);
            }
        } finally {
            g.dispose();
        }
        ecrireFois(img, premier, fois);
    }

    private void ecrireFois(BufferedImage img, int premier, int fois) {
        Path premierFichier = dossier.resolve(String.format(Locale.ROOT, "f%05d.jpg", premier));
        try {
            ecrireJpeg(img, premierFichier);
            for (int i = 1; i < fois; i++) {
                Files.copy(premierFichier, dossier.resolve(String.format(Locale.ROOT, "f%05d.jpg", premier + i)));
            }
        } catch (IOException ex) {
            LOG.error("[film] image non écrite : {}", premierFichier, ex);
        }
    }

    private static void ecrireJpeg(BufferedImage img, Path fichier) throws IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = w.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.93f);
        try (ImageOutputStream sortie = ImageIO.createImageOutputStream(fichier.toFile())) {
            w.setOutput(sortie);
            w.write(null, new IIOImage(img, null, null), param);
        } finally {
            w.dispose();
        }
    }

    /** Une flèche blanche bordée de noir, la pointe sur le point du clic. */
    private static void dessinerCurseur(Graphics2D g, double x, double y) {
        int px = (int) Math.round(x);
        int py = (int) Math.round(y);
        Polygon fleche = new Polygon(new int[] {0, 0, 5, 9, 12, 8, 14}, new int[] {0, 19, 14, 22, 21, 13, 13}, 7);
        fleche.translate(px, py);
        g.setColor(Color.WHITE);
        g.fillPolygon(fleche);
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(Color.BLACK);
        g.drawPolygon(fleche);
    }

    /**
     * Le titre de la scène (or) et la légende du pas, sur un dégradé sombre : en haut à gauche (la barre d'action et le chat
     * restent libres), en bas quand le haut est pris (F3, liste des joueurs).
     */
    private static void dessinerLegende(Graphics2D g, String titrePlan, String legende, double apparition, boolean enBas) {
        float a = (float) apparition;
        int hauteur = 112;
        int y0 = enBas ? HAUTEUR - hauteur : 0;
        int base = enBas ? HAUTEUR - 82 : 22;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f * a));
        g.setPaint(enBas ? new GradientPaint(0, y0, new Color(0, 0, 0, 0), 0, HAUTEUR, new Color(8, 12, 18, 235))
                : new GradientPaint(0, 0, new Color(8, 12, 18, 235), 0, hauteur, new Color(0, 0, 0, 0)));
        g.fillRect(0, y0, LARGEUR, hauteur);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setColor(new Color(0xE0, 0x9A, 0x2B));
        g.fillRect(40, base, 5, 54);
        g.setFont(new Font("Segoe UI", Font.BOLD, 28));
        g.setColor(new Color(0xF2, 0xC2, 0x6B));
        g.drawString(titrePlan, 58, base + 26);
        if (!legende.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.PLAIN, 21));
            g.setColor(Color.WHITE);
            g.drawString(legende, 58, base + 54);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    void terminer(String echecFinal) {
        ecrivains.shutdown();
        try {
            ecrivains.awaitTermination(3, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        String e = echecFinal == null ? "null" : "\"" + echecFinal.replace('"', '\'').replace('\\', '/') + "\"";
        try {
            Files.writeString(dossier.resolve("termine.json"), "{\"images\":" + numero + ",\"copies\":" + copies + ",\"fps\":30,\"echec\":"
                    + e + "}", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOG.error("[film] termine.json non écrit", ex);
        }
    }
}
