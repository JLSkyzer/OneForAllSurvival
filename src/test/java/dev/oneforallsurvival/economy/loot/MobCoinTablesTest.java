package dev.oneforallsurvival.economy.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Le rechargement de la table ({@link MobCoinTables#apply}) : un fichier absent, mal formé ou invalide laisse la
 * table précédente en service, et le journal dit lequel des cas c'est.
 */
class MobCoinTablesTest {

    private static final Path SHIPPED =
            Path.of("src/main/resources/data/oneforallsurvival/economy/mob_coins.json");
    private static final ResourceLocation FILE = new ResourceLocation("oneforallsurvival", "mob_coins");

    private final MobCoinTables listener = new MobCoinTables();
    private MobCoinTable before;

    /** Un gestionnaire de ressources où le fichier existe (ce que le chargeur a écarté comme JSON mal formé). */
    private static ResourceManager fileThere() {
        Resource resource = new Resource(null, InputStream::nullInputStream);
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[] {ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getResource")) {
                        return Optional.of(resource);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @BeforeEach
    void chargerLaTableLivree() throws IOException {
        MobCoinTables.setForTest(null);
        JsonElement json = JsonParser.parseString(Files.readString(SHIPPED, StandardCharsets.UTF_8));
        listener.apply(Map.of(FILE, json), ResourceManager.Empty.INSTANCE, null);
        before = MobCoinTables.current();
        assertEquals(4, before.tierCount(), "la table livrée doit être en service avant chaque cas");
    }

    @Test
    void unFichierAbsentGardeLaTablePrecedente() {
        listener.apply(Map.of(), ResourceManager.Empty.INSTANCE, null);
        assertSame(before, MobCoinTables.current());
    }

    @Test
    void unFichierMalFormeGardeLaTablePrecedente() {
        // Le chargeur de Minecraft n'a rien transmis (JSON mal formé), mais le fichier est là.
        listener.apply(Map.of(), fileThere(), null);
        assertSame(before, MobCoinTables.current());
    }

    @Test
    void unFichierInvalideGardeLaTablePrecedente() {
        listener.apply(Map.of(FILE, new JsonPrimitive("pas un objet")), ResourceManager.Empty.INSTANCE, null);
        assertSame(before, MobCoinTables.current());
        listener.apply(Map.of(FILE, JsonParser.parseString("{\"tiers\": []}")), ResourceManager.Empty.INSTANCE, null);
        assertSame(before, MobCoinTables.current());
    }

    @Test
    void unFichierValideRemplaceLaTable() throws IOException {
        JsonElement json = JsonParser.parseString(Files.readString(SHIPPED, StandardCharsets.UTF_8));
        listener.apply(Map.of(FILE, json), ResourceManager.Empty.INSTANCE, null);
        assertNotSame(before, MobCoinTables.current());
    }

    @Test
    void leJournalNeDitPasAbsentDUnFichierMalForme() {
        assertEquals("economy/mob_coins.json absent", MobCoinTables.missingReason(ResourceManager.Empty.INSTANCE));
        String malForme = MobCoinTables.missingReason(fileThere());
        assertTrue(malForme.contains("illisible") && !malForme.contains("absent"), malForme);
    }
}
