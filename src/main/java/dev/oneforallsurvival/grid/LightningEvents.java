package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * La foudre sur les machines du mod. Chaque machine sans panne à {@link GridConfig#lightningRadius()} blocs ou moins
 * de l'éclair est protégée si un parafoudre actif est à {@link GridConfig#arresterRadius()} blocs ou moins d'elle ;
 * sinon elle perd {@link GridConfig#lightningDamage()} points et risque la panne de fusible
 * ({@link GridConfig#lightningFuseChance()}). Chaque parafoudre qui a protégé au moins une machine s'use une seule fois
 * par éclair. Le parafoudre lui-même n'est jamais frappé, et les blocs en ligne (compteur, disjoncteur), qui ne sont
 * pas des machines, non plus.
 *
 * <p>La recherche ne lit que les chunks déjà chargés : une zone déchargée n'est ni chargée ni générée.
 */
public final class LightningEvents {

    /** {@code LightningBolt.visualOnly}, sans accesseur en 1.20.1 ; nom SRG, traduit par Forge en développement. */
    private static final Field VISUAL_ONLY = ObfuscationReflectionHelper.findField(LightningBolt.class, "f_20862_");

    /** En dernier : un éclair qu'un autre mod annule n'arrive pas jusqu'ici. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof LightningBolt bolt)
                || !(event.getLevel() instanceof ServerLevel level) || isVisualOnly(bolt)) {
            return;
        }
        strike(level, bolt.position(), level.getRandom());
    }

    /** Applique un éclair tombé en {@code pos}. */
    public static void strike(ServerLevel level, Vec3 pos, RandomSource random) {
        BlockPos impact = BlockPos.containing(pos);
        int radius = GridConfig.lightningRadius();
        int reach = GridConfig.arresterRadius();
        List<ElectricMachineBlockEntity> victims = new ArrayList<>();
        List<SurgeArresterBlockEntity> arresters = new ArrayList<>();
        // Un parafoudre utile est à portée d'une machine frappée, donc à radius + reach de l'impact au plus.
        int search = radius + reach;
        for (int cx = (impact.getX() - search) >> 4; cx <= (impact.getX() + search) >> 4; cx++) {
            for (int cz = (impact.getZ() - search) >> 4; cz <= (impact.getZ() + search) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved()) {
                        continue;
                    }
                    if (be instanceof SurgeArresterBlockEntity arrester) {
                        if (arrester.isActive() && within(arrester.getBlockPos(), impact, search)) {
                            arresters.add(arrester);
                        }
                    } else if (be instanceof ElectricMachineBlockEntity machine && machine.fault() == null
                            && within(machine.getBlockPos(), impact, radius)) {
                        victims.add(machine);
                    }
                }
            }
        }
        if (victims.isEmpty()) {
            return;
        }
        Set<SurgeArresterBlockEntity> absorbed = new LinkedHashSet<>();
        double damage = GridConfig.lightningDamage();
        double fuseChance = GridConfig.lightningFuseChance();
        for (ElectricMachineBlockEntity machine : victims) {
            boolean shielded = false;
            for (SurgeArresterBlockEntity arrester : arresters) {
                if (within(arrester.getBlockPos(), machine.getBlockPos(), reach)) {
                    absorbed.add(arrester);
                    shielded = true;
                }
            }
            if (shielded) {
                continue;
            }
            if (random.nextDouble() < fuseChance) {
                machine.breakDown(FaultType.FUSE);
            } else {
                machine.takeDamage(damage);
            }
        }
        for (SurgeArresterBlockEntity arrester : absorbed) {
            arrester.absorbStrike();
        }
    }

    private static boolean within(BlockPos a, BlockPos b, int distance) {
        return a.distSqr(b) <= (double) distance * distance;
    }

    private static boolean isVisualOnly(LightningBolt bolt) {
        try {
            return VISUAL_ONLY.getBoolean(bolt);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("champ visualOnly de LightningBolt illisible", e);
        }
    }
}
