package dev.oneforallsurvival.food;

import dev.oneforallsurvival.food.client.PotableWaterRendering;
import java.util.function.Consumer;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

/**
 * Le type de fluide de l'eau potable. Seul le rendu est du côté client : Forge n'appelle
 * {@link #initializeClient} que sur le client, et la classe de rendu n'est donc jamais chargée sur un serveur.
 */
public final class PotableWaterType extends FluidType {

    public PotableWaterType(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new PotableWaterRendering());
    }
}
