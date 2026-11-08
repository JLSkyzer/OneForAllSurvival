package dev.oneforallsurvival.earlywindow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.ModuleLayer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import net.minecraftforge.fml.loading.progress.Message;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;

/**
 * Fenêtre de chargement anticipée « lostisland », choisie par {@code earlyWindowProvider} dans
 * {@code config/fml.toml}.
 *
 * <p>Pour l'instant, elle prouve seulement le branchement : tout est délégué au {@link DisplayWindow} de
 * Forge (y compris {@link #updateModuleReads}, dont dépend l'overlay de Forge pendant les ressources), et la
 * vraie progression du démarrage est journalisée dans {@code logs/lostisland-earlywindow.log}, une fois par
 * seconde, avec le temps écoulé depuis {@link #initialize}.
 */
public final class LostIslandWindow implements ImmediateWindowProvider {
    private static final String NAME = "lostisland";
    private static final long LOG_INTERVAL_NANOS = 1_000_000_000L;

    private final DisplayWindow forge = new DisplayWindow();

    // Journal de la progression : messages déjà écrits (par identité), date du dernier relevé des barres.
    private final Set<Message> seenMessages = Collections.newSetFromMap(new IdentityHashMap<>());
    private long startNanos = System.nanoTime();
    private long lastLogNanos;
    private BufferedWriter log;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Runnable initialize(String[] arguments) {
        startNanos = System.nanoTime();
        lastLogNanos = startNanos - LOG_INTERVAL_NANOS;
        openLog(gameDir(arguments).resolve("logs").resolve("lostisland-earlywindow.log"));
        write("initialize : fournisseur " + NAME + ", délégué au DisplayWindow de Forge");
        Runnable ticker = forge.initialize(arguments);
        write("initialize : DisplayWindow prêt");
        return ticker;
    }

    @Override
    public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
        forge.updateFramebufferSize(width, height);
    }

    @Override
    public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title,
                                     LongSupplier monitor) {
        write("setupMinecraftWindow : la fenêtre passe à Minecraft");
        return forge.setupMinecraftWindow(width, height, title, monitor);
    }

    @Override
    public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter,
                                  IntConsumer xSetter, IntConsumer ySetter) {
        return forge.positionWindow(monitor, widthSetter, heightSetter, xSetter, ySetter);
    }

    @Override
    public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex,
                                          boolean fade) {
        write("loadingOverlay : phase ressources de Minecraft, overlay de Forge");
        return forge.loadingOverlay(mc, ri, ex, fade);
    }

    @Override
    public void updateModuleReads(ModuleLayer layer) {
        write("updateModuleReads : couche du jeu disponible");
        forge.updateModuleReads(layer);
    }

    @Override
    public void periodicTick() {
        forge.periodicTick();
        long now = System.nanoTime();
        if (now - lastLogNanos >= LOG_INTERVAL_NANOS) {
            lastLogNanos = now;
            logProgress();
        }
    }

    @Override
    public String getGLVersion() {
        return forge.getGLVersion();
    }

    // --- Journal ---------------------------------------------------------------------------------------

    private void logProgress() {
        try {
            List<StartupNotificationManager.AgeMessage> messages = StartupNotificationManager.getMessages();
            // Les messages arrivent les plus récents en tête : on les écrit dans l'ordre d'arrivée.
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i).message();
                if (seenMessages.add(message)) {
                    write("message : " + message.getText());
                }
            }
            for (ProgressMeter meter : StartupNotificationManager.getCurrentProgress()) {
                String label = meter.label() == null ? "" : meter.label().getText();
                write("barre : \"" + meter.name() + "\" " + meter.current() + "/" + meter.steps()
                        + (label.isEmpty() || label.equals(meter.name()) ? "" : " (" + label + ")"));
            }
        } catch (RuntimeException e) {
            // Le journal ne doit jamais gêner le chargement.
            write("relevé impossible : " + e);
        }
    }

    private static Path gameDir(String[] arguments) {
        for (int i = 0; i + 1 < arguments.length; i++) {
            if ("--gameDir".equals(arguments[i])) {
                return Paths.get(arguments[i + 1]);
            }
        }
        return Paths.get(".");
    }

    private void openLog(Path file) {
        try {
            Files.createDirectories(file.getParent());
            log = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log = null;
        }
    }

    private synchronized void write(String line) {
        if (log == null) {
            return;
        }
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        try {
            log.write(String.format(Locale.ROOT, "[%8.3f s] [%s] %s", seconds, Thread.currentThread().getName(), line));
            log.newLine();
            log.flush();
        } catch (IOException e) {
            log = null;
        }
    }
}
