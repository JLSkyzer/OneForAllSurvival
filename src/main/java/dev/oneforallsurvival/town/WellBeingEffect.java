package dev.oneforallsurvival.town;

import java.util.UUID;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Le Bien-être : le plaisir d'une douche chaude. Un petit gain de vitesse de marche (le minage va plus vite par
 * {@link WellBeing#onBreakSpeed}) et rien d'autre. Il ne soigne jamais : ni {@code applyEffectTick} ni
 * {@code isDurationEffectTick} ne sont surchargés, l'effet n'a aucun tic.
 */
public final class WellBeingEffect extends MobEffect {

    private static final String SPEED_MODIFIER_ID = "7c1e5f6a-3b52-4a0d-9d8e-2f0b6c4a91d3";

    public WellBeingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF2B45A);
        // Le montant est lu dans la config à chaque application (voir getAttributeModifierValue) : la config
        // n'est pas chargée quand l'effet est enregistré.
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_ID, 0.0,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** L'identifiant du modificateur de vitesse, pour les tests. */
    public static UUID speedModifierId() {
        return UUID.fromString(SPEED_MODIFIER_ID);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return TownConfig.wellBeingSpeedBonus();
    }
}
