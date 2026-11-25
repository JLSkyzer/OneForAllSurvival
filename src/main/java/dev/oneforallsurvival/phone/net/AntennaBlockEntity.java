package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneRegistry;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L'antenne : une machine électrique qui relaie le réseau de son opérateur. Elle ne garde que son identifiant, celui de
 * son opérateur et, tant qu'elle n'en a pas, qui l'a posée ; ce qu'elle fait (en marche, vue, consommation) vit dans le
 * registre ({@link PhoneOperators}, {@link AntennaRecord}).
 *
 * <p>Elle ne travaille que si son opérateur existe et qu'elle est inscrite au registre à cette place (une copie du bloc
 * ailleurs ne travaille pas). Elle paie alors à chaque tic la veille, plus une part par carte active de l'opérateur,
 * plus le trafic ouvert sur elle ({@link AntennaLoad}, {@link PhoneNetwork#openTraffic}). À
 * la fin de chaque tic elle note au registre si elle a tourné ({@link PhoneOperators#noteTick}) : c'est ce qui la dit
 * « en service » ({@link #inService}).
 */
public final class AntennaBlockEntity extends ElectricMachineBlockEntity {

    /** Tics entre deux révisions du ticket de chunk de l'antenne ({@link AntennaChunks}). */
    public static final int SYNC_TICKS = 20;

    private static final String TAG_ANTENNA = "antenna";
    private static final String TAG_OPERATOR = "operator";
    private static final String TAG_PLACER = "placer";
    private static final String TAG_PLACER_NAME = "placerName";

    @Nullable
    private UUID antennaId;
    @Nullable
    private String operatorId;
    /** Qui a posé une antenne encore sans opérateur : lui seul (et les ayants droit) la fonde. */
    @Nullable
    private UUID placer;
    private String placerName = "";

    /** Non sauvegardés : le décompte des cartes actives, et le détail du dernier tic. */
    private final ActiveCardCache cards = new ActiveCardCache();
    private AntennaLoad.Breakdown breakdown = new AntennaLoad.Breakdown(0, 0, 0);

    public AntennaBlockEntity(BlockPos pos, BlockState state) {
        super(PhoneRegistry.ANTENNA_BE.get(), pos, state, PhoneConfig.antennaCapacity(), PhoneConfig.antennaMaxReceive());
    }

    /**
     * L'antenne est-elle en service ? Elle tournait au dernier tic noté, et ce tic date d'au plus
     * {@code antennaStaleTicks} : une antenne dont le chunk ne tique plus, ou que le jeu n'a plus vue, est hors tension.
     */
    public static boolean inService(AntennaRecord antenna, long gameTime) {
        return antenna.running() && gameTime - antenna.seenGameTime() <= PhoneConfig.antennaStaleTicks();
    }

    @Nullable
    public UUID antennaId() {
        return antennaId;
    }

    @Nullable
    public String operatorId() {
        return operatorId;
    }

    @Nullable
    public UUID placer() {
        return placer;
    }

    public String placerName() {
        return placerName;
    }

    /** Le détail de la consommation du dernier tic (veille, cartes, trafic). */
    public AntennaLoad.Breakdown breakdown() {
        return breakdown;
    }

    /** L'antenne rejoint cet opérateur, sous cet identifiant. */
    void attach(String operator, UUID antenna) {
        operatorId = operator;
        antennaId = antenna;
        setChanged();
    }

    /** L'antenne repart sans opérateur, posée par ce joueur. */
    void detach(UUID placedBy, String placedByName) {
        operatorId = null;
        antennaId = null;
        placer = placedBy;
        placerName = placedByName;
        setChanged();
    }

    /**
     * À qui appartient cette antenne tant qu'elle n'a pas d'opérateur en service : le fondateur, à sa suppression, de
     * l'opérateur qu'elle portait (tâche 11 : elle redevient une antenne à fonder pour lui, comme le poste d'E6) ;
     * sinon qui l'a posée ; nul si personne n'est noté. Côté serveur ; côté client, le poseur.
     */
    @Nullable
    public PhoneOperators.FormerFounder idleOwner() {
        if (operatorId != null && level instanceof ServerLevel server) {
            Optional<PhoneOperators.FormerFounder> former =
                    PhoneOperators.get(server.getServer()).formerFounder(operatorId);
            if (former.isPresent()) {
                return former.get();
            }
        }
        return placer == null ? null : new PhoneOperators.FormerFounder(placer, placerName);
    }

    /** L'opérateur de cette antenne, s'il existe (côté serveur seulement). */
    public Optional<PhoneOperator> operator() {
        if (operatorId == null || !(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        return PhoneOperators.get(server.getServer()).byId(operatorId);
    }

    /**
     * L'inscription de cette antenne au registre, si c'est bien elle : même identifiant, même opérateur, même place
     * (une copie du bloc ailleurs, données comprises, n'est pas inscrite).
     */
    public Optional<AntennaRecord> record() {
        if (antennaId == null || operatorId == null || !(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        return PhoneOperators.get(server.getServer()).antenna(antennaId)
                .filter(r -> r.operatorId().equals(operatorId) && r.dimension().equals(server.dimension())
                        && r.pos().equals(worldPosition));
    }

    /** Opérateur existant et antenne inscrite à cette place. */
    @Override
    protected boolean wantsToWork() {
        return operator().isPresent() && record().isPresent();
    }

    /**
     * Veille, plus une part par carte active de l'opérateur, plus le trafic ouvert sur cette antenne (appels et visios
     * des poignées de {@link PhoneNetwork#openTraffic}, lus dans le registre : une table, rien de parcouru).
     */
    @Override
    protected int energyPerTick() {
        int active = 0;
        int calls = 0;
        int facetimes = 0;
        if (operatorId != null && level instanceof ServerLevel server) {
            PhoneOperators registry = PhoneOperators.get(server.getServer());
            active = cards.count(registry, operatorId);
            if (antennaId != null) {
                calls = registry.traffic(antennaId, TrafficKind.CALL);
                facetimes = registry.traffic(antennaId, TrafficKind.FACETIME);
            }
        }
        breakdown = AntennaLoad.of(PhoneConfig.antennaStandbyFe(), PhoneConfig.antennaPerSimFe(), active,
                calls, PhoneConfig.antennaCallFe(), facetimes, PhoneConfig.antennaFacetimeFe());
        return breakdown.total();
    }

    @Override
    protected void workTick() {}

    /**
     * Note le tic au registre (inscrite seulement) : en marche ou non, vue maintenant, ce qui a été payé (rien hors de
     * la marche) et le détail du coût. Puis, toutes les {@value #SYNC_TICKS} tics : une antenne encore sans opérateur
     * dont le poseur en a fondé un depuis le rejoint (comme si elle avait été posée après la fondation : le poseur est le
     * fondateur, donc un ayant droit) ; et le ticket de son chunk est revu, posé si elle a lieu de le tenir (réglage,
     * inscription, opérateur existant), rendu sinon.
     */
    @Override
    protected void afterTick(Level level, MachineState tickState) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean running = tickState == MachineState.RUNNING;
        Optional<AntennaRecord> mine = record();
        if (mine.isPresent()) {
            PhoneOperators.get(server.getServer()).noteTick(antennaId, running, server.getGameTime(),
                    running ? breakdown.total() : 0, breakdown);
        }
        if (Math.floorMod(server.getGameTime() + worldPosition.hashCode(), SYNC_TICKS) == 0) {
            if (operatorId == null && placer != null) {
                PhoneOperators registry = PhoneOperators.get(server.getServer());
                Optional<PhoneOperator> founded = registry.byFounder(placer);
                if (founded.isPresent()) {
                    AntennaBlock.inscribe(server, worldPosition, this, founded.get().id());
                    mine = record();
                }
            }
            if (mine.isPresent() && PhoneConfig.antennaKeepsChunkLoaded() && operator().isPresent()) {
                AntennaChunks.hold(server, worldPosition);
            } else {
                AntennaChunks.release(server, worldPosition);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (antennaId != null) {
            tag.putUUID(TAG_ANTENNA, antennaId);
        }
        if (operatorId != null) {
            tag.putString(TAG_OPERATOR, operatorId);
        }
        if (placer != null) {
            tag.putUUID(TAG_PLACER, placer);
            tag.putString(TAG_PLACER_NAME, placerName);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        antennaId = tag.hasUUID(TAG_ANTENNA) ? tag.getUUID(TAG_ANTENNA) : null;
        String operator = tag.getString(TAG_OPERATOR);
        operatorId = operator.isEmpty() ? null : operator;
        placer = tag.hasUUID(TAG_PLACER) ? tag.getUUID(TAG_PLACER) : null;
        placerName = tag.getString(TAG_PLACER_NAME);
    }
}
