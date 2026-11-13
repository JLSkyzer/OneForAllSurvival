package dev.oneforallsurvival.client.title;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Lecture asynchrone, au démarrage du client, de l'adresse du serveur réglée sur le site, et dernière adresse
 * connue gardée sur disque ({@value #CACHE_FILE} dans {@code config/}). Code client seulement : n'est appelé que
 * depuis {@link TitleSetupEvents}. L'état est publié par un champ {@code volatile} ; l'écran titre le relit sur le
 * fil du client.
 */
public final class SiteAddressClient {
    public static final String CACHE_FILE = "oneforallsurvival-adresse.txt";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static volatile SiteAddress.Status status = SiteAddress.choose(null, null, TitleConfig.SERVER_ADDRESS);

    private SiteAddressClient() {}

    /** État courant (adresse gardée ou repli tant que le site n'a pas répondu). */
    public static SiteAddress.Status status() {
        return status;
    }

    /** Charge la dernière adresse gardée puis interroge le site en tâche de fond. Ne lève jamais d'exception. */
    public static void start() {
        try {
            Path cacheFile = FMLPaths.CONFIGDIR.get().resolve(CACHE_FILE);
            String cached = readCache(cacheFile);
            status = SiteAddress.choose(null, cached, TitleConfig.SERVER_ADDRESS);
            if (!TitleConfig.SITE_LOOKUP_ENABLED || TitleConfig.SITE_ADDRESS_URL.isBlank()) {
                return;
            }
            Thread thread = new Thread(() -> fetch(cacheFile, cached), "OneForAllSurvival-adresse");
            thread.setDaemon(true);
            thread.start();
        } catch (RuntimeException e) {
            LOGGER.info("Adresse du serveur : lecture non lancée ({})", e.toString());
        }
    }

    private static void fetch(Path cacheFile, String cached) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(TitleConfig.SITE_ADDRESS_URL)).timeout(TIMEOUT)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream in = response.body()) {
                // un octet de plus que la limite suffit à reconnaître une réponse trop grande
                body = new String(in.readNBytes(SiteAddress.MAX_BODY + 1), StandardCharsets.UTF_8);
            }
            if (response.statusCode() != 200) {
                LOGGER.info("Adresse du serveur : le site répond {}, adresse gardée utilisée", response.statusCode());
                return;
            }
            SiteAddress.Reply reply = SiteAddress.parse(body);
            if (reply == null) {
                LOGGER.info("Adresse du serveur : réponse du site inexploitable, adresse gardée utilisée");
                return;
            }
            status = SiteAddress.choose(reply, cached, TitleConfig.SERVER_ADDRESS);
            if (reply.address() != null && !reply.address().equals(cached)) {
                writeCache(cacheFile, reply.address());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.info("Adresse du serveur : site injoignable ({}), adresse gardée utilisée", e.toString());
        }
    }

    private static String readCache(Path file) {
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= SiteAddress.MAX_ADDRESS * 4L) {
                return SiteAddress.valid(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.info("Adresse du serveur : fichier gardé illisible ({})", e.toString());
        }
        return null;
    }

    private static void writeCache(Path file, String address) {
        try {
            Path tmp = file.resolveSibling(CACHE_FILE + ".tmp");
            Files.writeString(tmp, address + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LOGGER.info("Adresse du serveur : écriture du fichier gardé impossible ({})", e.toString());
        }
    }
}
