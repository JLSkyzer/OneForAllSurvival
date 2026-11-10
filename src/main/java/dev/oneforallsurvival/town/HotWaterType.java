package dev.oneforallsurvival.town;

import dev.oneforallsurvival.town.client.HotWaterRendering;
import java.util.function.Consumer;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

/**
 * Le type de fluide de l'eau chaude. Seul le rendu est du côté client : Forge n'appelle {@link #initializeClient}
 * que sur le client, et la classe de rendu n'est donc jamais chargée sur un serveur.
 */
public final class HotWaterType extends FluidType {

    public HotWaterType(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new HotWaterRendering());
    }
}
