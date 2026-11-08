package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.WeatherForecast.Trend;
import dev.oneforallsurvival.grid.WeatherStationBlock;
import dev.oneforallsurvival.grid.WeatherStationBlockEntity;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La station météo. La météo est globale : chaque test qui la règle a son propre lot, et l'{@link AfterBatch} de ce lot
 * rend la météo d'avant (ciel clair du serveur de test, compteurs et règle {@code doWeatherCycle}), que le test passe ou
 * échoue. La météo se règle par les setters de {@link ServerLevelData}, qui seuls séparent les compteurs de pluie et
 * d'orage. Le serveur de test fige la météo ({@code doWeatherCycle} à faux) : les tests qui veulent une prévision la
 * relancent.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class MeteoGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    /** Plus long que tout horizon tiré (6 000 × 1,2) : ciel clair jusqu'au bout de la prévision. */
    private static final int CLAIR_LONG = 20_000;
    private static final int LOIN = 100_000;

    private MeteoGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Météo : réglage et remise en état
    // ---------------------------------------------------------------------------------------------

    /** La météo d'avant le test en cours ; rendue par l'{@link AfterBatch} de son lot. */
    private static Sauvegarde sauvegarde;

    private record Sauvegarde(int clear, int rain, boolean raining, int thunder, boolean thundering, boolean cycle) {}

    private static ServerLevelData donnees(ServerLevel level) {
        if (!(level.getLevelData() instanceof ServerLevelData data)) {
            throw new GameTestAssertException("les données du monde ne sont pas des ServerLevelData");
        }
        return data;
    }

    /** Règle la météo, cycle vanilla en marche ou figé. La première fois dans un test, garde la météo d'avant. */
    private static void meteo(GameTestHelper helper, boolean cycle, int clear, boolean raining, int rain,
            boolean thundering, int thunder) {
        ServerLevel level = helper.getLevel();
        ServerLevelData data = donnees(level);
        GameRules.BooleanValue regle = level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE);
        if (sauvegarde == null) {
            sauvegarde = new Sauvegarde(data.getClearWeatherTime(), data.getRainTime(), data.isRaining(),
                    data.getThunderTime(), data.isThundering(), regle.get());
        }
        regle.set(cycle, level.getServer());
        data.setClearWeatherTime(clear);
        data.setRainTime(rain);
        data.setRaining(raining);
        data.setThunderTime(thunder);
        data.setThundering(thundering);
    }

    private static void meteo(GameTestHelper helper, int clear, boolean raining, int rain, boolean thundering,
            int thunder) {
        meteo(helper, true, clear, raining, rain, thundering, thunder);
    }

    /** Rend la météo d'avant le test. Appelé par chaque {@link AfterBatch}, même après un échec. */
    private static void restaurer(ServerLevel level) {
        Sauvegarde s = sauvegarde;
        if (s == null) {
            return;
        }
        sauvegarde = null;
        ServerLevelData data = donnees(level);
        data.setClearWeatherTime(s.clear());
        data.setRainTime(s.rain());
        data.setRaining(s.raining());
        data.setThunderTime(s.thunder());
        data.setThundering(s.thundering());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(s.cycle(), level.getServer());
    }

    @AfterBatch(batch = "meteo_beau")
    public static void apresBeau(ServerLevel level) {
        restaurer(level);
    }

    @AfterBatch(batch = "meteo_pluie")
    public static void apresPluie(ServerLevel level) {
        restaurer(level);
    }

    @AfterBatch(batch = "meteo_orage")
    public static void apresOrage(ServerLevel level) {
        restaurer(level);
    }

    @AfterBatch(batch = "meteo_sans_courant")
    public static void apresSansCourant(ServerLevel level) {
        restaurer(level);
    }

    @AfterBatch(batch = "meteo_figee")
    public static void apresFigee(ServerLevel level) {
        restaurer(level);
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static void verifier(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Pose une station dont la face affiche déjà {@code affichee} : la lecture doit la corriger. */
    private static WeatherStationBlockEntity station(GameTestHelper helper, BlockPos pos, Trend affichee, int energie) {
        helper.setBlock(pos, GridRegistry.WEATHER_STATION.get().defaultBlockState()
                .setValue(WeatherStationBlock.TREND, affichee));
        verifier(helper.getBlockEntity(pos) instanceof WeatherStationBlockEntity, "pas de station en " + pos);
        WeatherStationBlockEntity be = (WeatherStationBlockEntity) helper.getBlockEntity(pos);
        alimenter(be, energie);
        return be;
    }

    private static void alimenter(WeatherStationBlockEntity be, int energie) {
        int reste = energie;
        while (reste > 0) {
            int recu = be.energy().receiveEnergy(reste, false);
            if (recu <= 0) {
                break;
            }
            reste -= recu;
        }
    }

    /** Fixe l'horizon d'une station par sa sauvegarde, comme au rechargement d'un monde. */
    private static void horizon(WeatherStationBlockEntity be, int ticks) {
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putInt(WeatherStationBlockEntity.TAG_HORIZON, ticks);
        be.load(tag);
        verifier(be.horizon() == ticks, "horizon " + ticks + " attendu, vu " + be.horizon());
    }

    private static Trend tendance(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(WeatherStationBlock.TREND);
    }

    private static MachineState etat(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(ElectricMachineBlock.STATE);
    }

    private static void enMarcheAvec(GameTestHelper helper, BlockPos pos, Trend attendue) {
        verifier(etat(helper, pos) == MachineState.RUNNING, "station " + pos + " en marche attendue, vue " + etat(helper, pos));
        verifier(tendance(helper, pos) == attendue, "station " + pos + " : " + attendue + " attendu, vu " + tendance(helper, pos));
    }

    /** Le signal de la station sur chacune de ses faces, et ce que chaque voisin en reçoit. */
    private static void signal(GameTestHelper helper, BlockPos pos, int attendu) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        for (Direction d : Direction.values()) {
            int vu = level.getSignal(abs, d);
            verifier(vu == attendu, "signal " + attendu + " attendu vers " + d + ", vu " + vu);
            boolean recu = level.hasNeighborSignal(abs.relative(d.getOpposite()));
            verifier(recu == (attendu > 0), "le voisin côté " + d.getOpposite() + " devait "
                    + (attendu > 0 ? "" : "ne pas ") + "recevoir le signal");
        }
    }

    private static void lampe(GameTestHelper helper, BlockPos pos, boolean allumee) {
        BlockState s = helper.getBlockState(pos);
        verifier(s.is(Blocks.REDSTONE_LAMP), "pas de lampe en " + pos);
        verifier(s.getValue(RedstoneLampBlock.LIT) == allumee,
                "la lampe en " + pos + " devait être " + (allumee ? "allumée" : "éteinte"));
    }

    // ---------------------------------------------------------------------------------------------
    // Prévision
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "meteo_beau", setupTicks = 20, timeoutTicks = 200)
    public static void parBeauTempsStable(GameTestHelper helper) {
        meteo(helper, CLAIR_LONG, false, 1, false, 1);
        station(helper, STATION, Trend.STORM_SOON, 1000);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    enMarcheAvec(helper, STATION, Trend.STABLE);
                    signal(helper, STATION, 0);
                })
                // /weather clear 600 : au tick qui suit la fin du ciel forcé, vanilla lance pluie et orage ensemble.
                .thenExecute(() -> meteo(helper, 600, false, 0, false, 0))
                .thenWaitUntil(() -> enMarcheAvec(helper, STATION, Trend.STORM_SOON))
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = "meteo_pluie", setupTicks = 20, timeoutTicks = 200)
    public static void pluieProche(GameTestHelper helper) {
        // Pluie dans 5 500 ticks, orage lointain. La station à 6 000 ticks d'horizon la voit, celle à 5 000 non.
        meteo(helper, 0, false, 5500, false, LOIN);
        BlockPos courte = new BlockPos(3, 1, 1);
        WeatherStationBlockEntity longue = station(helper, STATION, Trend.STABLE, 0);
        WeatherStationBlockEntity breve = station(helper, courte, Trend.STORM_SOON, 0);
        horizon(longue, 6000);
        horizon(breve, 5000);
        alimenter(longue, 1000);
        alimenter(breve, 1000);
        helper.succeedWhen(() -> {
            enMarcheAvec(helper, STATION, Trend.RAIN_SOON);
            enMarcheAvec(helper, courte, Trend.STABLE);
            signal(helper, STATION, 0);
            signal(helper, courte, 0);
        });
    }

    @GameTest(template = "test/plancher", batch = "meteo_orage", setupTicks = 20, timeoutTicks = 200)
    public static void orageProcheEtSignal(GameTestHelper helper) {
        BlockPos lampe = STATION.east();
        helper.setBlock(lampe, Blocks.REDSTONE_LAMP);
        // Il pleut, l'orage éclate dans 1 000 ticks, pendant la pluie.
        meteo(helper, 0, true, 5000, false, 1000);
        station(helper, STATION, Trend.STABLE, 1000);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    enMarcheAvec(helper, STATION, Trend.STORM_SOON);
                    signal(helper, STATION, 15);
                    lampe(helper, lampe, true);
                })
                // Le ciel se dégage : le signal tombe et le voisin en est prévenu.
                .thenExecute(() -> meteo(helper, CLAIR_LONG, false, 1, false, 1))
                .thenWaitUntil(() -> {
                    enMarcheAvec(helper, STATION, Trend.STABLE);
                    signal(helper, STATION, 0);
                    lampe(helper, lampe, false);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = "meteo_sans_courant", setupTicks = 20, timeoutTicks = 300)
    public static void sansCourantRienNiSignal(GameTestHelper helper) {
        // Station jamais alimentée, et station témoin qui n'a que 100 FE (50 ticks à 2 FE).
        BlockPos lampeEteinte = new BlockPos(0, 1, 1);
        // Aucun voisin commun entre les deux stations : chacune son signal.
        BlockPos temoin = new BlockPos(4, 1, 1);
        BlockPos lampeTemoin = new BlockPos(5, 1, 1);
        helper.setBlock(lampeEteinte, Blocks.REDSTONE_LAMP);
        helper.setBlock(lampeTemoin, Blocks.REDSTONE_LAMP);
        meteo(helper, 0, true, 5000, false, 1000);
        station(helper, STATION, Trend.STORM_SOON, 0);
        station(helper, temoin, Trend.STABLE, 100);
        Runnable sansCourant = () -> {
            verifier(etat(helper, STATION) == MachineState.NO_POWER, "sans courant attendu, vu " + etat(helper, STATION));
            signal(helper, STATION, 0);
            lampe(helper, lampeEteinte, false);
        };
        helper.startSequence()
                .thenWaitUntil(() -> {
                    // Le témoin prouve que l'orage est bien annoncé.
                    enMarcheAvec(helper, temoin, Trend.STORM_SOON);
                    signal(helper, temoin, 15);
                    lampe(helper, lampeTemoin, true);
                    sansCourant.run();
                })
                // Le témoin épuise sa réserve : plus rien, et sa lampe s'éteint.
                .thenWaitUntil(() -> {
                    verifier(etat(helper, temoin) == MachineState.NO_POWER, "témoin sans courant attendu, vu " + etat(helper, temoin));
                    signal(helper, temoin, 0);
                    lampe(helper, lampeTemoin, false);
                    sansCourant.run();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = "meteo_figee", setupTicks = 20, timeoutTicks = 200)
    public static void meteoFigeeRienNeChange(GameTestHelper helper) {
        // doWeatherCycle à faux : la pluie prévue dans 100 ticks ne viendra jamais.
        meteo(helper, false, 0, false, 100, false, LOIN);
        station(helper, STATION, Trend.STORM_SOON, 1000);
        helper.startSequence()
                .thenWaitUntil(() -> enMarcheAvec(helper, STATION, Trend.STABLE))
                .thenExecute(() -> meteo(helper, true, 0, false, 100, false, LOIN))
                .thenWaitUntil(() -> enMarcheAvec(helper, STATION, Trend.RAIN_SOON))
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Machine : horizon propre, usure
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "meteo_horizon")
    public static void lHorizonPropreSurvitAuRechargement(GameTestHelper helper) {
        Set<Integer> vus = new HashSet<>();
        for (BlockPos pos : List.of(new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), new BlockPos(0, 1, 2),
                new BlockPos(2, 1, 2))) {
            WeatherStationBlockEntity be = station(helper, pos, Trend.STABLE, 0);
            int h = be.horizon();
            verifier(h >= 4800 && h <= 7200, "horizon tiré dans [4800, 7200] attendu, vu " + h);
            WeatherStationBlockEntity copie = new WeatherStationBlockEntity(be.getBlockPos(), be.getBlockState());
            copie.load(be.saveWithoutMetadata());
            verifier(copie.horizon() == h, "horizon rechargé : " + copie.horizon() + " au lieu de " + h);
            vus.add(h);
        }
        verifier(vus.size() > 1, "quatre stations, un seul horizon : le décalage n'est pas tiré par station");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "meteo_usure", setupTicks = 20)
    public static void elleSUseEnMarche(GameTestHelper helper) {
        WeatherStationBlockEntity marche = station(helper, STATION, Trend.STABLE, 1000);
        WeatherStationBlockEntity arret = station(helper, new BlockPos(3, 1, 1), Trend.STABLE, 0);
        helper.runAfterDelay(40, () -> {
            verifier(marche.condition() < 100.0, "en marche l'état devait baisser, vu " + marche.condition());
            int consomme = 1000 - marche.energy().getEnergyStored();
            verifier(consomme >= 70 && consomme <= 90, "2 FE par tick : environ 80 FE attendus sur 40 ticks, vus " + consomme);
            verifier(arret.condition() == 100.0, "sans courant l'état devait rester à 100, vu " + arret.condition());
            helper.succeed();
        });
    }
}
