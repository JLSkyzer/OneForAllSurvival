package dev.oneforallsurvival.earlywindow;

import dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase;
import dev.oneforallsurvival.earlywindow.scene.StatusMessages;
import dev.oneforallsurvival.earlywindow.scene.StatusMessages.Status;
import java.util.List;

/**
 * Déduit, à chaque image, la phase du démarrage et l'avancement dans la phase à partir des barres et des messages
 * de Forge ({@code StartupNotificationManager}). Logique pure : la fenêtre lui passe un relevé, il rend une lecture.
 *
 * <p>Règles (relevé de la tâche 0, ruling 7) :
 * <ul>
 *   <li>{@link Phase#FORGE_START} dès l'ouverture de la fenêtre ;</li>
 *   <li>{@link Phase#MOD_DISCOVERY} après le message « Forge loading … » (ou « Forge version … »), jusqu'à la
 *       première barre du chargement des mods : « Mod Gather », ou « EARLY » quand elle dit « Loading mods ». La
 *       barre EARLY existe dès le début avec d'autres libellés (« Bootstrapping Minecraft », « Scanning mod
 *       candidates », « Loading bootstrap resources », « Initializing Game Graphics » relevés en tâche 7), qui
 *       restent de la découverte ;</li>
 *   <li>ensuite {@link StatusMessages#phaseOf} (registres) ; la barre « Minecraft Progress » de l'overlay de Forge
 *       donne {@link Phase#RESOURCES}, et sa disparition (fin du rechargement) {@link Phase#DONE}.</li>
 * </ul>
 * La phase ne recule jamais. Dans une phase dont la barre a des étapes (« Mod Gather » 9/18, « Minecraft Progress »
 * 40/100), l'avancement est le rapport des étapes ; sans étapes (découverte, registres), il suit le temps écoulé
 * rapporté à la durée relevée en tâche 0, plafonné à {@link #CREEP_MAX} pour ne jamais annoncer la fin d'une phase
 * qui dure plus que prévu.
 */
public final class ProgressTracker {
    /** Une barre de Forge au moment du relevé. */
    public record Meter(String name, String label, int current, int steps) {
    }

    /** Ce que la barre et le texte doivent montrer. */
    public record Reading(Phase phase, float fraction, Status status) {
    }

    /** Barre ajoutée par {@code ForgeLoadingOverlay} pendant le chargement des ressources de Minecraft. */
    public static final String RESOURCES_METER = "Minecraft Progress";
    /** Plafond de l'avancement estimé au temps dans une phase sans étapes. */
    public static final float CREEP_MAX = 0.9f;

    private static final String EARLY_MODS_LABEL = "Loading mods";
    // Durées relevées en tâche 0 (s), dans l'ordre de Phase : fenêtre, découverte et préparation du jeu, mods,
    // registres, ressources. Elles ne servent qu'aux phases sans étapes.
    private static final double[] EXPECTED = {0.7, 19.8, 8.4, 1.0, 14.0};

    private Phase phase = Phase.FORGE_START;
    private double phaseStart;
    private boolean resourcesMeterSeen;

    /**
     * @param now      secondes depuis l'ouverture de la fenêtre (croissantes)
     * @param meters   barres en cours
     * @param messages textes des derniers messages
     */
    public synchronized Reading update(double now, List<Meter> meters, List<String> messages) {
        Phase target = phase;
        for (String message : messages) {
            Phase p = StatusMessages.phaseOf(null, message);
            // « Forge loading/version » : Forge a démarré, la découverte des mods commence (ruling 7).
            target = max(target, p == Phase.FORGE_START ? Phase.MOD_DISCOVERY : p);
        }
        boolean resourcesMeter = false;
        for (Meter meter : meters) {
            if (RESOURCES_METER.equals(meter.name())) {
                resourcesMeter = true;
            }
            target = max(target, phaseOf(meter));
        }
        if (resourcesMeter) {
            resourcesMeterSeen = true;
        } else if (resourcesMeterSeen) {
            target = Phase.DONE; // ForgeLoadingOverlay retire sa barre quand le rechargement est fini
        }
        enter(target, now);

        Meter detail = meterOf(phase, meters);
        float fraction;
        if (phase == Phase.DONE) {
            fraction = 1f;
        } else if (detail != null && detail.steps() > 0) {
            fraction = Math.max(0f, Math.min(1f, (float) detail.current() / detail.steps()));
        } else {
            fraction = (float) Math.min(CREEP_MAX, Math.max(0.0, now - phaseStart) / EXPECTED[phase.ordinal()]);
        }
        Status status = detail == null
                ? StatusMessages.of(phase, "", 0, 0)
                : StatusMessages.of(phase, detail.label(), detail.current(), detail.steps());
        return new Reading(phase, fraction, status);
    }

    /** Fait passer à {@code target} si elle est plus avancée (l'overlay des ressources, la fin). */
    public synchronized void enter(Phase target, double now) {
        if (target.ordinal() > phase.ordinal()) {
            phase = target;
            phaseStart = now;
        }
    }

    public synchronized Phase phase() {
        return phase;
    }

    private static Phase phaseOf(Meter meter) {
        if (RESOURCES_METER.equals(meter.name())) {
            return Phase.RESOURCES;
        }
        if ("EARLY".equals(meter.name()) && !EARLY_MODS_LABEL.equals(meter.label())) {
            return null; // la barre EARLY existe dès le début : seul son libellé « Loading mods » compte
        }
        return StatusMessages.phaseOf(meter.name(), null);
    }

    /** Barre qui détaille la phase : celle qui a des étapes d'abord (« Mod Gather » plutôt que « EARLY »). */
    private static Meter meterOf(Phase phase, List<Meter> meters) {
        Meter found = null;
        for (Meter meter : meters) {
            if (phaseOf(meter) == phase && (found == null || found.steps() <= 0 && meter.steps() > 0)) {
                found = meter;
            }
        }
        return found;
    }

    private static Phase max(Phase a, Phase b) {
        return b != null && b.ordinal() > a.ordinal() ? b : a;
    }
}
