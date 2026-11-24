package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * La seule fabrique de la vue de l'opérateur ({@link OperatorView}, lot T2, tâche 9) : le panneau du bloc, celui du
 * téléphone et l'appli « Antenne » montrent ce qu'elle rend, et {@link OperatorActions#apply} juge les gestes sur les
 * mêmes droits.
 *
 * <p>Droits ({@link AntennaRules}) : les cartes au fondateur ; le tarif au fondateur, au maire et à l'équipe ; la caisse
 * au fondateur et à l'équipe, sur le bloc de l'antenne principale seulement (jamais depuis le téléphone) ; le nom
 * seulement pour une antenne à fonder, sur le bloc, par qui peut la fonder ({@link AntennaRules#mayFoundFor}).
 *
 * <p>Rien n'y entre d'une carte que son {@code sim_id}, son numéro, son titulaire et son état, et seulement pour les
 * cartes de cet opérateur : ni jeton, ni téléphone. Les listes sont coupées à leur borne. Rien ne charge un chunk : l'état
 * d'une antenne se lit sur son bloc si son chunk est chargé, sinon d'après le registre.
 */
public final class OperatorViews {

    /** Une page de cartes. */
    public static final int CARDS_PER_PAGE = OperatorView.MAX_LIST;

    private OperatorViews() {}

    /**
     * La vue de {@code operator} (nul : antenne à fonder) pour {@code viewer}.
     *
     * @param pos l'antenne cliquée (source {@code BLOCK}) ; ignorée pour le téléphone, la vue porte alors
     *        {@link BlockPos#ZERO}
     * @param page la page de cartes demandée, ramenée entre la première et la dernière
     */
    public static OperatorView build(ServerPlayer viewer, PhoneOperators registry, @Nullable PhoneOperator operator,
            OperatorView.Source source, BlockPos pos, int session, int page) {
        boolean block = source == OperatorView.Source.BLOCK;
        BlockPos shown = block ? pos.immutable() : BlockPos.ZERO;
        if (operator == null) {
            boolean name = block && mayName(viewer, pos);
            return new OperatorView(source, shown, session, "", "", new OperatorView.Rights(false, false, false, name),
                    0L, null, 0L, 0L, List.of(), 0, 0, 0, List.of(), List.of(), List.of());
        }
        PhoneOperator op = registry.byId(operator.id()).orElse(operator);
        OperatorView.Rights rights = new OperatorView.Rights(AntennaRules.mayManageCards(viewer, op),
                AntennaRules.maySetTariff(viewer, op), block && AntennaRules.mayTakeTill(viewer, op)
                        && isMainAt(viewer.serverLevel(), registry, op, pos), false);
        MinecraftServer server = viewer.server;
        long gameTime = server.overworld().getGameTime();
        List<OperatorView.AntennaLine> antennas = new ArrayList<>();
        for (AntennaRecord a : registry.antennasOf(op.id())) {
            if (antennas.size() >= OperatorView.MAX_LIST) {
                break;
            }
            antennas.add(new OperatorView.AntennaLine(a.id(), OperatorView.clip(a.dimension().location().toString()),
                    a.pos(), state(server, a, gameTime), a.last(), a.feToday(), a.feYesterday(),
                    a.id().equals(op.mainAntenna()), a.last().total() > PhoneConfig.antennaMaxReceive()));
        }
        List<SimCard> all = registry.cardsOf(op.id());
        int pages = Math.max(1, (all.size() + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE);
        int shownPage = Math.max(0, Math.min(pages - 1, page));
        List<OperatorView.CardLine> cards = new ArrayList<>();
        for (SimCard c : all.subList(Math.min(all.size(), shownPage * CARDS_PER_PAGE),
                Math.min(all.size(), (shownPage + 1) * CARDS_PER_PAGE))) {
            cards.add(new OperatorView.CardLine(c.simId(), c.number(), OperatorView.clip(c.holderName()), c.state()));
        }
        List<OperatorView.RequestLine> requests = new ArrayList<>();
        for (SimRequest r : registry.requestsOf(op.id())) {
            if (requests.size() >= OperatorView.MAX_LIST) {
                break;
            }
            requests.add(new OperatorView.RequestLine(r.id(), OperatorView.clip(r.playerName()), r.at()));
        }
        List<PhoneNetwork.RelayState> relay = new ArrayList<>();
        for (PhoneNetwork.RelayState s : PhoneNetwork.relayStates(server)) {
            if (relay.size() >= OperatorView.MAX_LIST) {
                break;
            }
            relay.add(new PhoneNetwork.RelayState(OperatorView.clip(s.operatorId()), OperatorView.clip(s.name()),
                    s.antennas(), s.inService()));
        }
        long cap = registry.nextTariffCap().orElse(registry.tariffCap());
        return new OperatorView(source, shown, session, op.id(), OperatorView.clip(op.name()), rights, op.tariff(),
                op.nextTariff(), cap, CoinMath.valueOf(registry.till(op.id())), antennas, shownPage, pages, all.size(),
                cards, requests, relay);
    }

    /** L'antenne de ce bloc est-elle l'antenne principale de cet opérateur (chunk chargé, bloc d'antenne) ? */
    static boolean isMainAt(ServerLevel level, PhoneOperators registry, PhoneOperator op, BlockPos pos) {
        if (op.mainAntenna() == null) {
            return false;
        }
        return registry.antenna(op.mainAntenna())
                .filter(a -> a.dimension().equals(level.dimension()) && a.pos().equals(pos))
                .filter(a -> {
                    AntennaBlockEntity be = loadedAntenna(level, pos);
                    return be != null && a.id().equals(be.antennaId()) && op.id().equals(be.operatorId());
                })
                .isPresent();
    }

    /** Peut-il nommer l'antenne à fonder de ce bloc (chunk chargé, bloc d'antenne sans opérateur) ? */
    private static boolean mayName(ServerPlayer viewer, BlockPos pos) {
        AntennaBlockEntity be = loadedAntenna(viewer.serverLevel(), pos);
        if (be == null || be.operator().isPresent()) {
            return false;
        }
        return AntennaRules.mayFoundFor(viewer, be.placer());
    }

    /** L'entité de l'antenne de ce bloc, si son chunk est chargé (rien n'est chargé ici) ; nulle sinon. */
    @Nullable
    static AntennaBlockEntity loadedAntenna(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.getBlockState(pos).is(PhoneRegistry.ANTENNA.get())) {
            return null;
        }
        return chunk.getBlockEntity(pos) instanceof AntennaBlockEntity be ? be : null;
    }

    /** L'état de machine d'une antenne : celui de son bloc s'il est chargé, sinon « en marche » ou « à l'arrêt ». */
    private static MachineState state(MinecraftServer server, AntennaRecord a, long gameTime) {
        ServerLevel level = server.getLevel(a.dimension());
        if (level != null) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(a.pos().getX() >> 4, a.pos().getZ() >> 4);
            if (chunk != null) {
                BlockState state = chunk.getBlockState(a.pos());
                if (state.is(PhoneRegistry.ANTENNA.get()) && state.hasProperty(ElectricMachineBlock.STATE)) {
                    return Objects.requireNonNull(state.getValue(ElectricMachineBlock.STATE));
                }
            }
        }
        return AntennaBlockEntity.inService(a, gameTime) ? MachineState.RUNNING : MachineState.STOPPED;
    }
}
