package dev.oneforallsurvival.economy.loot;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

/**
 * La marque « sauvage » d'un mob : posée seulement quand le jeu le fait naître de lui-même (apparition naturelle,
 * génération du monde, structure, patrouille, raid, warden). Elle vit dans les données persistantes de l'entité
 * ({@code ForgeData}), écrites avec le chunk. Tout le reste n'en a pas : générateur, œuf, distributeur, commande,
 * seau, élevage, invocation, renfort, jockey, et tout mob créé sans événement d'apparition (petit d'élevage, slime
 * divisé, poule d'un œuf lancé, œuf utilisé sur un mob, mob né avant ce lot). Un chemin oublié donne donc « rien ».
 *
 * <p>Chemins relevés dans Forge 47.4.28 : voir le rapport de la tâche 2 du lot E4.
 */
public final class MobOrigin {

    /** La clé de la marque dans {@code getPersistentData()}. */
    public static final String KEY = "oneforallsurvival:wild";

    /** Les apparitions qui font un mob sauvage. Fixées ici, pas dans la table de l'équipe. */
    public static final Set<MobSpawnType> WILD_TYPES = Set.copyOf(EnumSet.of(
            MobSpawnType.NATURAL,
            MobSpawnType.CHUNK_GENERATION,
            MobSpawnType.STRUCTURE,
            MobSpawnType.PATROL,
            MobSpawnType.EVENT,
            MobSpawnType.TRIGGERED));

    private MobOrigin() {}

    public static boolean isWild(Entity entity) {
        return entity.getPersistentData().getBoolean(KEY);
    }

    public static void markWild(Entity entity) {
        entity.getPersistentData().putBoolean(KEY, true);
    }

    public static void clear(Entity entity) {
        entity.getPersistentData().remove(KEY);
    }

    /**
     * Marque ou efface selon le seul type d'apparition. Reçoit aussi l'événement annulé : un générateur dont
     * l'événement est annulé fait quand même apparaître son mob (avec les données de son NBT), qu'il faut effacer.
     * Le piglin zombifié qu'un portail du Nether fait naître arrive en {@code STRUCTURE} : il n'est pas sauvage.
     */
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Entity mob = event.getEntity();
        MobSpawnType type = event.getSpawnType();
        if (WILD_TYPES.contains(type) && !(type == MobSpawnType.STRUCTURE && inNetherPortal(event))) {
            markWild(mob);
        } else {
            clear(mob);
        }
    }

    private static boolean inNetherPortal(MobSpawnEvent.FinalizeSpawn event) {
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        return event.getLevel().getBlockState(pos).is(Blocks.NETHER_PORTAL);
    }

    /** Une conversion est un pour un : le nouveau mob prend la marque de l'ancien, présente ou absente. */
    public static void onConversion(LivingConversionEvent.Post event) {
        if (isWild(event.getEntity())) {
            markWild(event.getOutcome());
        } else {
            clear(event.getOutcome());
        }
    }

    /** Un petit d'élevage n'est jamais sauvage, même si un autre mod lui a recopié les données des parents. */
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (event.getChild() != null) {
            clear(event.getChild());
        }
    }
}
