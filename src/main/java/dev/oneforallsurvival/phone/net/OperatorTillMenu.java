package dev.oneforallsurvival.phone.net;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.CoinActions;
import dev.oneforallsurvival.economy.CoinActions.Result;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.CoinStore;
import dev.oneforallsurvival.economy.SafeBlock;
import dev.oneforallsurvival.grid.supplier.PowerTillMenu;
import dev.oneforallsurvival.phone.PhoneRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;

/**
 * La caisse d'un opérateur (lot T2, tâche 9) : le menu commun du coffre-fort ({@link CoinAccountMenu}) sur la caisse
 * gardée par le registre ({@link PhoneOperators#till}), sur le modèle de la caisse du poste de fournisseur
 * ({@link PowerTillMenu}). Déclarée rangement permis pour l'argent ; aucune case de contenu.
 *
 * <p>Seuls le fondateur et l'équipe l'ouvrent ({@link AntennaRules#mayTakeTill}), <b>en personne sur l'antenne
 * principale</b> : jamais depuis le téléphone, jamais par une trémie (aucun conteneur, aucun {@code ITEM_HANDLER}), et le
 * menu se ferme dès que le joueur s'éloigne, que l'antenne disparaît ou n'est plus la principale. Aucun dépôt : la caisse
 * ne reçoit que ce que le lot T6 y versera. Retrait et change ne créent ni ne détruisent aucune pièce
 * ({@link CoinActions}).
 */
public final class OperatorTillMenu extends CoinAccountMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final BlockPos pos;
    /** Côté serveur : le registre et l'opérateur ; nuls côté client. */
    @Nullable
    private final PhoneOperators registry;
    @Nullable
    private final String operatorId;

    public OperatorTillMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable PhoneOperators registry,
            @Nullable String operatorId) {
        super(PhoneRegistry.OPERATOR_TILL_MENU.get(), containerId, inventory, -1);
        this.pos = pos.immutable();
        this.registry = registry;
        this.operatorId = operatorId;
    }

    public static OperatorTillMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new OperatorTillMenu(containerId, inventory, buf.readBlockPos(), null, null);
    }

    /**
     * Ouvre la caisse de {@code op} sur l'antenne en {@code pos} ({@code OPEN_TILL}, déjà jugé par
     * {@link OperatorActions}) ; revérifie le droit et la place.
     *
     * @return vrai si l'écran s'est ouvert
     */
    public static boolean open(ServerPlayer player, BlockPos pos, PhoneOperator op) {
        PhoneOperators registry = PhoneOperators.get(player.server);
        if (player instanceof FakePlayer || !AntennaRules.mayTakeTill(player, op)
                || !OperatorViews.isMainAt(player.serverLevel(), registry, op, pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > OperatorPanels.MAX_DISTANCE_SQR) {
            return false;
        }
        String id = op.id();
        NetworkHooks.openScreen(player, new SimpleMenuProvider((containerId, inventory, p) ->
                new OperatorTillMenu(containerId, inventory, pos, registry, id),
                Component.translatable("container.oneforallsurvival.operator_till", op.name())),
                buf -> buf.writeBlockPos(pos));
        return true;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    protected boolean isServerSide() {
        return registry != null && operatorId != null;
    }

    @Override
    protected EnumMap<Coin, Long> liveContents() {
        return new Store(registry, operatorId).contents();
    }

    @Override
    public boolean usesPurses() {
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Result result = apply(serverPlayer, id);
        if (CoinActions.isTold(result)) {
            serverPlayer.displayClientMessage(
                    Component.translatable(SafeBlock.MESSAGE + result.name().toLowerCase(Locale.ROOT)), true);
        } else if (result == Result.NOT_ALLOWED) {
            serverPlayer.displayClientMessage(Component.translatable(OperatorActions.MESSAGE + "not_allowed"), true);
        }
        return true;
    }

    /** Le contrôle et l'opération d'un numéro de bouton ({@link PowerTillMenu#button}), sans le réseau. */
    public Result apply(ServerPlayer player, int id) {
        if (registry == null || operatorId == null) {
            return Result.NOT_OPEN;
        }
        if (player.containerMenu != this || !stillValid(player)) {
            return Result.NOT_OPEN;
        }
        Optional<CoinRequest> decoded = PowerTillMenu.request(id);
        if (decoded.isEmpty()) {
            return Result.BAD_REQUEST;
        }
        CoinRequest request = decoded.get();
        if (request.action() == CoinRequest.DEPOSIT || request.action() == CoinRequest.DEPOSIT_ALL) {
            return Result.BAD_REQUEST;
        }
        Optional<PhoneOperator> op = registry.byId(operatorId);
        if (op.isEmpty()) {
            return Result.NO_SAFE;
        }
        if (!AntennaRules.mayTakeTill(player, op.get())) {
            return Result.NOT_ALLOWED;
        }
        Coin coin = request.coinIfValid().orElseThrow();
        Result result = CoinActions.apply(player, new Store(registry, operatorId), request.action(), coin,
                request.amount(), request.all());
        if (result == Result.DONE && !op.get().founder().equals(player.getUUID())) {
            LOGGER.info("Economie : {} a agi sur la caisse de l'operateur {} ({})", player.getGameProfile().getName(),
                    op.get().name(), request);
        }
        return result;
    }

    /**
     * L'antenne est toujours là, chargée, à portée, toujours l'antenne principale de cet opérateur, et le joueur
     * toujours fondateur ou de l'équipe. Côté client, seul le bloc compte.
     */
    @Override
    public boolean stillValid(Player player) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > OperatorPanels.MAX_DISTANCE_SQR) {
            return false;
        }
        if (registry == null || operatorId == null) {
            return player.level().getBlockState(pos).is(PhoneRegistry.ANTENNA.get());
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return registry.byId(operatorId).filter(op -> AntennaRules.mayTakeTill(player, op)
                && OperatorViews.isMainAt(level, registry, op, pos)).isPresent();
    }

    /** La caisse vue comme un {@link CoinStore}, comme {@code PowerTillStore} : même plafond par coupure. */
    record Store(PhoneOperators registry, String operatorId) implements CoinStore {

        @Override
        public long count(Coin coin) {
            return registry.till(operatorId).getOrDefault(coin, 0L);
        }

        @Override
        public long room(Coin coin) {
            return registry.tillRoom(operatorId, coin);
        }

        @Override
        public void add(Coin coin, long count) {
            registry.deposit(operatorId, coin, count);
        }

        @Override
        public boolean remove(Coin coin, long count) {
            return registry.withdraw(operatorId, coin, count);
        }

        @Override
        public boolean groupAll() {
            return groupByTens();
        }

        @Override
        public boolean usesPurses() {
            return true;
        }
    }
}
