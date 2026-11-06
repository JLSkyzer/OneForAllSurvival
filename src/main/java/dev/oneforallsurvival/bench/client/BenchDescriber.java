package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.BenchRecipe;
import net.minecraft.core.registries.BuiltInRegistries;

/** Le descripteur réel : le nom affiché de l'objet fabriqué, et le mod qui l'a enregistré. */
final class BenchDescriber implements BenchClientState.Describer {

    @Override
    public String name(BenchRecipe recipe) {
        return recipe.result().getHoverName().getString();
    }

    @Override
    public String mod(BenchRecipe recipe) {
        return BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).getNamespace();
    }
}
