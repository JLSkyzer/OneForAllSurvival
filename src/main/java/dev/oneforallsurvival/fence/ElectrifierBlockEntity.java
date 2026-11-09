package dev.oneforallsurvival.fence;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * L'électrificateur. Il relit sa ligne (les clôtures électriques contiguës, les {@link FenceConfig#maxLength()} plus
 * proches) toutes les {@value #SCAN_PERIOD} ticks, au premier appel d'{@link #energyPerTick()} du tick, sans jamais
 * charger de chunk : un bloc d'un chunk déchargé ne compte pas. Il consomme tant que la ligne n'est pas vide, et tous
 * les {@value #SHOCK_PERIOD} ticks de travail il choque les vivants qui touchent une clôture de la ligne.
 *
 * <p>Deux électrificateurs sur la même ligne ne doublent pas le choc : un vivant choqué l'est au plus une fois par
 * {@value #SHOCK_PERIOD} ticks de jeu, quel que soit l'électrificateur.
 */
public final class ElectrifierBlockEntity extends ElectricMachineBlockEntity {

    public static final int CAPACITY = 4000;
    public static final int MAX_RECEIVE = 200;

    private static final int SCAN_PERIOD = 40;
    private static final int SHOCK_PERIOD = 10;
    private static final int SPARK_PERIOD = 20;
    /** Marge dont on gonfle la boîte d'un vivant pour juger qu'il touche une clôture. */
    private static final double CONTACT_MARGIN = 0.08;
    /** Le dernier tick de jeu où chaque vivant a été choqué, par n'importe quel électrificateur (thread serveur seul). */
    private static final Map<Entity, Long> LAST_SHOCK = new WeakHashMap<>();

    private List<BlockPos> line = List.of();
    private int vegetation;
    /** Tick de jeu de la prochaine lecture de la ligne ; non sauvegardé (relue au chargement). */
    private long nextScan;
    private int workTicks;

    public ElectrifierBlockEntity(BlockPos pos, BlockState state) {
        super(FenceRegistry.ELECTRIFIER_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    /** La ligne telle qu'elle a été relue la dernière fois : les clôtures alimentées, de la plus proche à la plus loin. */
    public List<BlockPos> line() {
        return line;
    }

    @Override
    protected boolean wantsToWork() {
        return !line.isEmpty();
    }

    /** Lue une fois par tick, avant {@link #wantsToWork()} : c'est ici que la ligne est relue quand son heure est venue. */
    @Override
    public int energyPerTick() {
        refreshLine();
        boolean storm = level != null && level.isThundering();
        return FenceRules.energyPerTick(line.size(), vegetation, storm, FenceConfig.baseEnergy(),
                FenceConfig.perFenceEnergy(), FenceConfig.perVegetationEnergy(), FenceConfig.stormMultiplier(),
                MAX_RECEIVE);
    }

    @Override
    protected void workTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        workTicks++;
        if (workTicks % SHOCK_PERIOD == 0) {
            shockTouching(serverLevel);
        }
        if (serverLevel.getGameTime() % SPARK_PERIOD == 0 && !line.isEmpty()) {
            BlockPos fence = line.get(serverLevel.random.nextInt(line.size()));
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, fence.getX() + 0.5, fence.getY() + 0.8,
                    fence.getZ() + 0.5, 4, 0.2, 0.3, 0.2, 0.05);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // La ligne
    // ---------------------------------------------------------------------------------------------

    private void refreshLine() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() < nextScan) {
            return;
        }
        nextScan = serverLevel.getGameTime() + SCAN_PERIOD;
        int max = Math.min(FenceConfig.maxLength(), FenceConfig.MAX_LENGTH_LIMIT);
        line = List.copyOf(FenceLine.scan(worldPosition, pos -> isFence(serverLevel, pos), max));
        vegetation = countVegetation(serverLevel, line);
    }

    /** Une clôture électrique dans un chunk déjà chargé ; ne charge rien. */
    private static boolean isFence(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(FenceRegistry.ELECTRIC_FENCE.get());
    }

    /** Les blocs de végétation sur les quatre côtés et au-dessus de chaque clôture, chacun compté une fois. */
    private static int countVegetation(ServerLevel level, List<BlockPos> fences) {
        Set<BlockPos> found = new HashSet<>();
        for (BlockPos fence : fences) {
            for (Direction direction : Direction.values()) {
                if (direction == Direction.DOWN) {
                    continue;
                }
                BlockPos around = fence.relative(direction);
                if (level.hasChunkAt(around) && level.getBlockState(around).is(FenceRegistry.VEGETATION)) {
                    found.add(around.immutable());
                }
            }
        }
        return found.size();
    }

    // ---------------------------------------------------------------------------------------------
    // Le choc
    // ---------------------------------------------------------------------------------------------

    private void shockTouching(ServerLevel serverLevel) {
        if (line.isEmpty()) {
            return;
        }
        AABB zone = new AABB(line.get(0));
        for (BlockPos pos : line) {
            zone = zone.minmax(new AABB(pos));
        }
        zone = zone.expandTowards(0, 0.5, 0).inflate(CONTACT_MARGIN); // une clôture monte à 1,5 bloc
        long now = serverLevel.getGameTime();
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, zone, ElectrifierBlockEntity::canBeShocked)) {
            Vec3 wire = touchedWire(serverLevel, entity);
            if (wire == null) {
                continue;
            }
            Long last = LAST_SHOCK.get(entity);
            if (last != null && now - last < SHOCK_PERIOD) {
                continue;
            }
            LAST_SHOCK.put(entity, now);
            shock(entity, wire);
        }
    }

    /** Qui la clôture peut choquer : ni créatif ni spectateur, ni support d'armure, ni invulnérable, ni mort. */
    private static boolean canBeShocked(LivingEntity entity) {
        if (!entity.isAlive() || entity.isInvulnerable() || entity instanceof ArmorStand) {
            return false;
        }
        return !(entity instanceof Player player) || !(player.isCreative() || player.isSpectator());
    }

    /** Le centre de la clôture de la ligne que la boîte (gonflée) du vivant touche, ou {@code null}. */
    @Nullable
    private Vec3 touchedWire(ServerLevel serverLevel, LivingEntity entity) {
        AABB body = entity.getBoundingBox().inflate(CONTACT_MARGIN);
        for (BlockPos pos : line) {
            BlockState state = serverLevel.getBlockState(pos);
            if (!state.is(FenceRegistry.ELECTRIC_FENCE.get())) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(serverLevel, pos);
            for (AABB part : shape.toAabbs()) {
                if (part.move(pos).intersects(body)) {
                    return Vec3.atCenterOf(pos);
                }
            }
        }
        return null;
    }

    /**
     * Choque un vivant au contact de la clôture {@code wire} : dégâts selon le genre du vivant (voir {@link
     * FenceRules#damageFor}), puis repoussement à chaque contact, blessé ou non.
     */
    public void shock(LivingEntity entity, Vec3 wire) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        float amount;
        boolean lethal;
        if (entity instanceof Enemy) {
            amount = (float) FenceConfig.mobDamage();
            lethal = true;
        } else if (entity instanceof Player) {
            amount = (float) FenceConfig.playerDamage();
            lethal = FenceConfig.lethalForPlayers();
        } else {
            amount = (float) FenceConfig.mobDamage();
            lethal = false;
        }
        float damage = FenceRules.damageFor(entity.getHealth(), amount, (float) FenceConfig.safeHealth(), lethal);
        if (damage > 0) {
            DamageSource source = new DamageSource(
                    serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FenceRegistry.DAMAGE_TYPE));
            entity.hurt(source, damage);
        }
        double knockback = FenceConfig.knockback();
        if (knockback > 0 && entity.isAlive()) {
            // knockback() pousse à l'opposé du point donné, comme un coup venu de la clôture.
            entity.knockback(knockback, wire.x - entity.getX(), wire.z - entity.getZ());
            entity.hurtMarked = true;
        }
    }
}
