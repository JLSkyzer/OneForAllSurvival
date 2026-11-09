package dev.oneforallsurvival.food;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * La liste « œuf, animal » de la couveuse, lue dans la configuration (entrées {@code objet=entité}). Une entrée mal
 * formée est ignorée et journalisée ; un objet ou une entité qui n'existe pas (un mod absent) l'est aussi.
 *
 * <p>La table des objets et des entités est construite une fois pour une liste de configuration donnée : tant que
 * {@link FoodConfig#incubatorEggs()} renvoie la même liste, on ne relit rien ; un rechargement de la configuration en
 * installe une autre, et la table suit.
 */
public final class IncubatorEggs {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** La table construite pour {@code source}, qui est l'identité de la liste de configuration lue. */
    private record Resolved(List<? extends String> source, Map<Item, EntityType<?>> animals) {}

    private static volatile Resolved resolved;

    private IncubatorEggs() {}

    /** Lit les entrées {@code objet=entité}, dans l'ordre ; les entrées mal formées sont ignorées. */
    public static Map<String, String> parse(List<? extends String> entries) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String entry : entries) {
            int cut = entry.indexOf('=');
            String item = cut < 0 ? "" : entry.substring(0, cut).trim();
            String entity = cut < 0 ? "" : entry.substring(cut + 1).trim();
            // tryParse accepte la chaîne vide : on la refuse ici.
            if (item.isEmpty() || entity.isEmpty()
                    || ResourceLocation.tryParse(item) == null || ResourceLocation.tryParse(entity) == null) {
                LOGGER.warn("Couveuse : entrée ignorée, attendu « objet=entité » : {}", entry);
                continue;
            }
            result.put(item, entity);
        }
        return result;
    }

    /** L'animal qui sort de cet œuf, ou {@code null} si ce n'est pas un œuf de la liste. */
    @Nullable
    public static EntityType<?> entityFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return table().get(stack.getItem());
    }

    private static Map<Item, EntityType<?>> table() {
        List<? extends String> source = FoodConfig.incubatorEggs();
        Resolved current = resolved;
        if (current != null && current.source() == source) {
            return current.animals();
        }
        Map<Item, EntityType<?>> animals = new HashMap<>();
        for (Map.Entry<String, String> entry : parse(source).entrySet()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
            ResourceLocation entityId = ResourceLocation.tryParse(entry.getValue());
            if (itemId == null || entityId == null || !ForgeRegistries.ITEMS.containsKey(itemId)
                    || !ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
                LOGGER.warn("Couveuse : objet ou entité inconnu, entrée ignorée : {}={}", entry.getKey(), entry.getValue());
                continue;
            }
            animals.put(ForgeRegistries.ITEMS.getValue(itemId), ForgeRegistries.ENTITY_TYPES.getValue(entityId));
        }
        resolved = new Resolved(source, Map.copyOf(animals));
        return resolved.animals();
    }
}
