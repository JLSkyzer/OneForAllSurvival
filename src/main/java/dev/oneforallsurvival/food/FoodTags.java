package dev.oneforallsurvival.food;

import dev.oneforallsurvival.OneForAllSurvival;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Les tags d'objets de la péremption : ce qui périme, ce qui périme lentement, ce qui ne périme jamais. */
public final class FoodTags {

    public static final TagKey<Item> PERISHABLE = tag("perishable");
    public static final TagKey<Item> SLOW_PERISHABLE = tag("slow_perishable");
    public static final TagKey<Item> NON_PERISHABLE = tag("non_perishable");

    private FoodTags() {}

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(OneForAllSurvival.MODID, name));
    }
}
