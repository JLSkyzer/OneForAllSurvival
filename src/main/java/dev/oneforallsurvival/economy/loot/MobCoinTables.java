package dev.oneforallsurvival.economy.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Table de butin en service, rechargée avec les données ({@code /reload}, datapacks). Un fichier absent ou invalide
 * garde la table précédente ({@link MobCoinTable#EMPTY} au premier chargement) et écrit l'erreur au journal.
 */
public final class MobCoinTables extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    /** Le fichier lu : {@code data/oneforallsurvival/economy/mob_coins.json}. */
    private static final ResourceLocation FILE = new ResourceLocation("oneforallsurvival", "mob_coins");

    private static volatile MobCoinTable loaded = MobCoinTable.EMPTY;
    @Nullable
    private static volatile MobCoinTable forTest;

    public MobCoinTables() {
        super(GSON, "economy");
    }

    public static MobCoinTable current() {
        MobCoinTable test = forTest;
        return test != null ? test : loaded;
    }

    /** Impose une table (tests) tant qu'elle n'est pas rendue à {@code null}. */
    public static void setForTest(@Nullable MobCoinTable table) {
        forTest = table;
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new MobCoinTables());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement file = files.get(FILE);
        if (file == null) {
            LOGGER.error("Butin des mobs : economy/mob_coins.json absent, table precedente conservee");
            return;
        }
        try {
            JsonObject json = GsonHelper.convertToJsonObject(file, "mob_coins.json");
            MobCoinTable table = MobCoinTable.parse(json);
            loaded = table;
            LOGGER.info("Butin des mobs : {} paliers, {} regles", table.tierCount(), table.ruleCount());
        } catch (RuntimeException e) {
            LOGGER.error("Butin des mobs : mob_coins.json invalide, table precedente conservee : {}", e.getMessage());
        }
    }
}
