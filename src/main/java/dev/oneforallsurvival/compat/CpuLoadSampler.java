package dev.oneforallsurvival.compat;

import java.util.function.DoubleSupplier;

/**
 * Charge processeur mesurée sur un fil de fond, lue sans attendre. Sous Windows,
 * {@code OperatingSystemMXBean.getProcessCpuLoad()} peut durer une centaine de millisecondes ; appelée sur le fil de
 * rendu (panneau de Reggadium, toutes les 500 ms), elle fait sauter des images. Ici, un fil démon, démarré à la
 * première lecture, mesure toutes les {@code periodMs} et garde la dernière valeur ; une lecture ne fait que la
 * rendre. Sans lecture pendant {@code idleMs}, le fil s'endort jusqu'à la suivante.
 *
 * <p>Même règle que la mesure d'origine : la charge du système n'est mesurée que si celle du processus est
 * indisponible (négative). Une mesure qui lève vaut -1, le fil continue. Valeurs : de 0 à 1, ou -1 tant qu'il n'y a
 * pas de mesure.
 */
public final class CpuLoadSampler {
    private final DoubleSupplier process;
    private final DoubleSupplier system;
    private final long periodMs;
    private final long idleNs;
    private final String threadName;
    private final Object lock = new Object();

    private volatile double processLoad = -1.0;
    private volatile double systemLoad = -1.0;
    private volatile long lastReadNs;
    private volatile boolean idle;
    private volatile long samples;
    private Thread thread; // sous lock

    public CpuLoadSampler(DoubleSupplier process, DoubleSupplier system, long periodMs, long idleMs, String threadName) {
        this.process = process;
        this.system = system;
        this.periodMs = periodMs;
        this.idleNs = idleMs * 1_000_000L;
        this.threadName = threadName;
    }

    /** Dernière charge du processus mesurée (0 à 1), -1 si indisponible. Ne bloque jamais sur une mesure. */
    public double processLoad() {
        touch();
        return processLoad;
    }

    /** Dernière charge du système mesurée (0 à 1), -1 si indisponible ou pas encore demandée. */
    public double systemLoad() {
        touch();
        return systemLoad;
    }

    /** Vrai une fois le fil de mesure démarré (première lecture). */
    public boolean started() {
        synchronized (lock) {
            return thread != null;
        }
    }

    /** Nombre de mesures faites (essais). */
    public long samples() {
        return samples;
    }

    /** Vrai si le fil dort faute de lecture (essais). */
    public boolean idle() {
        return idle;
    }

    private void touch() {
        lastReadNs = System.nanoTime();
        if (thread == null || idle) {
            synchronized (lock) {
                if (thread == null) {
                    thread = new Thread(this::run, threadName);
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    thread.start();
                } else if (idle) {
                    idle = false;
                    lock.notifyAll();
                }
            }
        }
    }

    private void run() {
        try {
            while (true) {
                if (System.nanoTime() - lastReadNs > idleNs) {
                    synchronized (lock) {
                        idle = true;
                        while (idle) {
                            lock.wait();
                        }
                    }
                }
                double p = measure(process);
                processLoad = p;
                if (p < 0.0) {
                    systemLoad = measure(system);
                }
                samples++;
                Thread.sleep(periodMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static double measure(DoubleSupplier source) {
        try {
            double v = source.getAsDouble();
            return Double.isNaN(v) ? -1.0 : v;
        } catch (Throwable t) {
            return -1.0;
        }
    }
}
