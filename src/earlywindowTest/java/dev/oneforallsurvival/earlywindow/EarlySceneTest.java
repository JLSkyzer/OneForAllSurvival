package dev.oneforallsurvival.earlywindow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class EarlySceneTest {
    @Test
    void entreeMalFormeeIgnoree() {
        assertNull(EarlyScene.pathOrNull("mal\u0000forme.jar"));
        assertNull(EarlyScene.pathOrNull("   "));
        assertNotNull(EarlyScene.pathOrNull(" libs/client-extra.jar "));
    }

    @Test
    void uneEntreeMalFormeeNeFaitPasTomberLaRecherche() throws Exception {
        String previous = System.getProperty("legacyClassPath");
        List<String> logs = new ArrayList<>();
        try {
            System.setProperty("legacyClassPath", "client\u0000.jar" + File.pathSeparator + "introuvable/client-extra.jar");
            ZipFile jar = assertDoesNotThrow(() -> EarlyScene.openClientJar("1.20.1", logs::add));
            assertNull(jar);
        } finally {
            if (previous == null) {
                System.clearProperty("legacyClassPath");
            } else {
                System.setProperty("legacyClassPath", previous);
            }
        }
    }
}
