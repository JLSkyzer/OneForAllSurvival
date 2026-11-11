package dev.oneforallsurvival.light;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Le phare. Il veut travailler seulement en tour libre ({@link LightRules#isFreeTower}) et la nuit
 * ({@link LightRules#shouldShine}) : sous un toit, trop bas ou de jour il ne consomme rien et ne s'use pas. Il n'a pas de travail à produire : sa lumière et ses faisceaux (côté client) suivent son état.
 */
public final class LighthouseBlockEntity extends ElectricMachineBlockEntity {

    public static final int CAPACITY = 4000;
    public static final int MAX_RECEIVE = TOWN_MAX_RECEIVE;
    /** Ticks consécutifs dans l'autre état avant que {@code lit} ne change. */
    public static final int LIT_HOLD_TICKS = 20;

    /** Ticks consécutifs où l'état voulu pour {@code lit} diffère de l'actuel ; non sauvegardé. */
    private int contrary;

    public LighthouseBlockEntity(BlockPos pos, BlockState state) {
        super(LightRegistry.LIGHTHOUSE_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    @Override
    protected boolean wantsToWork() {
        return level != null && LightRules.shouldShine(LighthouseBlock.isFreeTower(level, worldPosition), level.isNight());
    }

    @Override
    protected int energyPerTick() {
        return LightConfig.lighthouseEnergy();
    }

    @Override
    protected void workTick() {}

    /**
     * Allume le bloc quand la machine tourne, l'éteint sinon, avec de l'hystérésis : {@code lit} ne change qu'après
     * {@value #LIT_HOLD_TICKS} ticks consécutifs de l'état contraire (RUNNING pour s'allumer, tout autre état pour
     * s'éteindre). Une source plus faible que le besoin alterne RUNNING et NO_POWER à chaque tick : sans cela le bloc
     * clignoterait et la lumière se recalculerait à chaque tick. Exception : le phare ne veut plus travailler (toit, trop
     * bas, ou le jour qui se lève), ce qui n'est pas un souci d'alimentation, et il s'éteint tout de suite. Le compteur n'est pas
     * sauvegardé ; le changement part avec celui de {@code STATE} en un seul {@code setBlock}.
     */
    @Override
    protected BlockState adjustState(Level level, BlockState target, MachineState tickState) {
        if (!target.is(LightRegistry.LIGHTHOUSE.get())) {
            return target;
        }
        boolean lit = target.getValue(LighthouseBlock.LIT);
        if (!wantsToWork()) {
            contrary = 0;
            return lit ? target.setValue(LighthouseBlock.LIT, false) : target;
        }
        if ((tickState == MachineState.RUNNING) == lit) {
            contrary = 0;
            return target;
        }
        if (++contrary < LIT_HOLD_TICKS) {
            return target;
        }
        contrary = 0;
        return target.setValue(LighthouseBlock.LIT, !lit);
    }

    /** Les faisceaux portent à 96 blocs : le bloc se dessine tant que le monde l'est, où qu'il soit à l'écran. */
    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }
}
