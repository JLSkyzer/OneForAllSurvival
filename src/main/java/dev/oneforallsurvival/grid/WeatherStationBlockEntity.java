package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.grid.WeatherForecast.Trend;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * La station météo : toujours au travail, elle consomme {@link GridConfig#weatherEnergy()} FE par tick et s'use comme
 * toute machine. En marche, elle relit la météo du monde toutes les {@value #READ_PERIOD} ticks (et dès qu'elle
 * repart) et affiche la tendance que {@link WeatherForecast} en tire sur son propre horizon.
 *
 * <p>L'horizon propre est tiré à la pose dans {@code [1 − jitter, 1 + jitter] × horizon} et sauvegardé en ticks : deux
 * stations voisines n'annoncent pas l'orage au même tick. Une station cassée puis reposée en tire un nouveau.
 */
public final class WeatherStationBlockEntity extends ElectricMachineBlockEntity {

    /** Clé de l'horizon propre dans la sauvegarde. */
    public static final String TAG_HORIZON = "horizon";
    private static final int CAPACITY = 1000;
    private static final int MAX_RECEIVE = 100;
    private static final int READ_PERIOD = 20;

    private int horizon;
    /** Tick de jeu de la prochaine lecture ; 0 : dès le prochain tick en marche. Non sauvegardé. */
    private long nextRead;
    /** Dernier signal annoncé aux voisins ; -1 : pas encore annoncé depuis le chargement. Non sauvegardé. */
    private int emittedSignal = -1;

    public WeatherStationBlockEntity(BlockPos pos, BlockState state) {
        super(GridRegistry.WEATHER_STATION_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
        this.horizon = WeatherForecast.drawHorizon(GridConfig.weatherHorizon(), GridConfig.weatherJitter(),
                RandomSource.create().nextDouble());
    }

    /** L'horizon propre de cette station, en ticks. */
    public int horizon() {
        return horizon;
    }

    @Override
    protected boolean wantsToWork() {
        return true;
    }

    @Override
    protected int energyPerTick() {
        return GridConfig.weatherEnergy();
    }

    @Override
    protected void workTick() {}

    @Override
    protected void afterTick(Level level, MachineState tickState) {
        BlockState current = level.getBlockState(worldPosition);
        if (!current.is(GridRegistry.WEATHER_STATION.get())) {
            return;
        }
        if (tickState != MachineState.RUNNING) {
            nextRead = 0; // relire dès que le courant revient : pas de tendance périmée affichée
        } else if (level.getGameTime() >= nextRead && level instanceof ServerLevel serverLevel) {
            nextRead = level.getGameTime() + READ_PERIOD;
            Trend trend = read(serverLevel);
            if (current.getValue(WeatherStationBlock.TREND) != trend) {
                current = current.setValue(WeatherStationBlock.TREND, trend);
                level.setBlock(worldPosition, current, Block.UPDATE_CLIENTS);
            }
        }
        // Le socle change l'état de marche sans prévenir les voisins : la station le fait quand son signal change.
        int signal = WeatherStationBlock.signalOf(current);
        if (signal != emittedSignal) {
            emittedSignal = signal;
            level.updateNeighborsAt(worldPosition, current.getBlock());
        }
    }

    /**
     * La tendance que la météo du monde donne sur l'horizon de la station. Une dimension sans ciel (Nether, End) n'a
     * pas de météo ; météo figée ({@code doWeatherCycle} à faux), seul l'état présent compte.
     */
    private Trend read(ServerLevel level) {
        if (!level.dimensionType().hasSkyLight() || !(level.getLevelData() instanceof ServerLevelData data)) {
            return Trend.STABLE;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
            return WeatherForecast.trend(data.isRaining(), 0, data.isThundering(), 0, 0, horizon);
        }
        return WeatherForecast.trend(data.isRaining(), data.getRainTime(), data.isThundering(), data.getThunderTime(),
                data.getClearWeatherTime(), horizon);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_HORIZON, horizon);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_HORIZON)) {
            horizon = Math.max(1, tag.getInt(TAG_HORIZON));
        }
    }
}
