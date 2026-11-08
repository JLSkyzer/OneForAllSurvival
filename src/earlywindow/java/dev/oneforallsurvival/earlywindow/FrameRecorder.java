package dev.oneforallsurvival.earlywindow;

import static org.lwjgl.opengl.GL32C.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryUtil;

/**
 * Film de preuve (outil de développement) : si {@code -Dlostisland.frames=<dossier>} est posé, une image sur deux
 * de la scène est enregistrée en PNG numérotés ({@code frame_00000.png}, …) dans ce dossier, relatif au dossier
 * du jeu. Les anciennes images {@code frame_*.png} du dossier sont effacées au départ.
 *
 * <p>La lecture des pixels se fait sur le thread GL ({@link #capture}) ; la compression PNG dans deux threads à
 * part, pour ne pas ralentir l'affichage. Si l'écriture prend du retard, les images en trop sont abandonnées (et
 * comptées dans le journal) plutôt que de bloquer le rendu : les numéros restent contigus, l'écart se lit dans
 * le journal.
 */
public final class FrameRecorder implements AutoCloseable {
    private static final int WRITERS = 2;

    private record Job(int index, int width, int height, ByteBuffer pixels) {
    }

    private static final Job STOP = new Job(-1, 0, 0, null);

    private final Path dir;
    private final Consumer<String> log;
    private final BlockingQueue<Job> queue = new ArrayBlockingQueue<>(16);
    private final List<Thread> writers = new ArrayList<>();
    private final AtomicInteger written = new AtomicInteger();
    private int frame;
    private int accepted;
    private int dropped;
    private boolean closed;

    private FrameRecorder(Path dir, Consumer<String> log) {
        this.dir = dir;
        this.log = log;
        for (int i = 0; i < WRITERS; i++) {
            Thread t = new Thread(this::writeLoop, "lostisland-frames-" + i);
            t.setDaemon(true);
            t.start();
            writers.add(t);
        }
    }

    /**
     * Enregistreur actif si {@code dir} est renseigné (valeur de {@code lostisland.frames}), sinon {@code null}.
     *
     * @param base dossier du jeu, base d'un chemin relatif
     */
    public static FrameRecorder maybe(String dir, Path base, Consumer<String> log) {
        if (dir == null || dir.isBlank()) {
            return null;
        }
        Path target = base.resolve(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(target);
            try (DirectoryStream<Path> old = Files.newDirectoryStream(target, "frame_*.png")) {
                for (Path p : old) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException e) {
            log.accept("film : dossier " + target + " inutilisable (" + e.getMessage() + "), aucune image enregistrée");
            return null;
        }
        log.accept("film : une image sur deux dans " + target);
        return new FrameRecorder(target, log);
    }

    /**
     * Lit l'image qui vient d'être dessinée dans le framebuffer de lecture lié ({@code width} x {@code height}),
     * une fois sur deux. Thread GL ; l'alignement de lecture est rendu tel quel.
     */
    public void capture(int width, int height) {
        if (closed || width <= 0 || height <= 0 || (frame++ & 1) != 0) {
            return;
        }
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 3);
        int previousAlignment = glGetInteger(GL_PACK_ALIGNMENT);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixels);
        glPixelStorei(GL_PACK_ALIGNMENT, previousAlignment);
        if (queue.offer(new Job(accepted, width, height, pixels))) {
            accepted++;
        } else {
            MemoryUtil.memFree(pixels);
            dropped++;
        }
    }

    private void writeLoop() {
        while (true) {
            Job job;
            try {
                job = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            if (job == STOP) {
                return;
            }
            ByteBuffer flipped = MemoryUtil.memAlloc(job.pixels().capacity());
            try {
                // glReadPixels range les lignes du bas vers le haut ; un PNG du haut vers le bas.
                int row = job.width() * 3;
                for (int y = 0; y < job.height(); y++) {
                    MemoryUtil.memCopy(MemoryUtil.memAddress(job.pixels()) + (long) (job.height() - 1 - y) * row,
                            MemoryUtil.memAddress(flipped) + (long) y * row, row);
                }
                Path file = dir.resolve(String.format(Locale.ROOT, "frame_%05d.png", job.index()));
                if (STBImageWrite.stbi_write_png(file.toString(), job.width(), job.height(), 3, flipped, row)) {
                    written.incrementAndGet();
                } else {
                    log.accept("film : écriture impossible de " + file);
                }
            } finally {
                MemoryUtil.memFree(flipped);
                MemoryUtil.memFree(job.pixels());
            }
        }
    }

    /** Termine les écritures en attente (quelques secondes au plus) et écrit le bilan au journal. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            for (int i = 0; i < WRITERS; i++) {
                queue.put(STOP);
            }
            for (Thread t : writers) {
                t.join(TimeUnit.SECONDS.toMillis(10));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.accept("film : " + written.get() + " images écrites sur " + accepted + " retenues, " + dropped
                + " abandonnées (écriture en retard), " + frame + " images rendues");
    }
}
