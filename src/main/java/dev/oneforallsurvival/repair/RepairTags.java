package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.machine.FaultType;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Les pièces de rechange se reconnaissent à leurs étiquettes, pas à leur identifiant : un autre mod
 * ou un pack peut déclarer sa propre pièce. {@code parts/<nom>} pour la pièce normale,
 * {@code treated_parts/<nom>} pour la pièce traitée.
 */
public final class RepairTags {

    /** Une pièce reconnue : la panne qu'elle répare et si elle est traitée. */
    public record PartMatch(FaultType type, boolean treated) {}

    private RepairTags() {}

    /** Nom de la pièce dans les étiquettes et les identifiants d'objets. */
    public static String partName(FaultType type) {
        return switch (type) {
            case FUSE -> "fuse";
            case WINDING -> "coil";
            case BEARING -> "bearing";
            case FOULING -> "cleaning_kit";
        };
    }

    public static TagKey<Item> tag(FaultType type, boolean treated) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(OneForAllSurvival.MODID,
                (treated ? "treated_parts/" : "parts/") + partName(type)));
    }

    /** La pièce de rechange que cette pile représente, s'il y en a une. */
    public static Optional<PartMatch> partFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        for (FaultType type : FaultType.values()) {
            if (stack.is(tag(type, true))) {
                return Optional.of(new PartMatch(type, true));
            }
            if (stack.is(tag(type, false))) {
                return Optional.of(new PartMatch(type, false));
            }
        }
        return Optional.empty();
    }
}
