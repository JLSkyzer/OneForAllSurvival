package dev.oneforallsurvival.grid.supplier;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.CoinActions;
import dev.oneforallsurvival.economy.CoinActions.Result;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.SafeBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * La caisse d'un fournisseur : le menu commun du coffre-fort ({@link CoinAccountMenu}), sur la caisse gardée par le
 * serveur ({@link PowerTillStore}). Déclaré rangement permis pour l'argent ; aucune case de contenu, rien ne passe par
 * le curseur.
 *
 * <p>Les gestes de l'écran arrivent par le bouton de menu du jeu ({@link #clickMenuButton}, dont le jeu a déjà vérifié
 * que ce menu est ouvert et toujours valable) : une {@link CoinRequest} y est codée en un entier ({@link #button}), et
 * le serveur la décode et la revalide comme un paquet. Aucun dépôt : la caisse ne reçoit que des pièces prélevées.
 * Retirer et faire le change : le fondateur et l'équipe seulement ({@link PowerRules#mayTakeTill}) ; les autres ayants
 * droit (ingénieur, maire) voient la caisse sans y toucher.
 */
public final class PowerTillMenu extends CoinAccountMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Distance maximale du joueur au poste, au carré (8 blocs). */
    public static final double MAX_DISTANCE_SQR = 64.0D;

    private final BlockPos pos;
    /** Côté serveur : le registre et le fournisseur ; nuls côté client. */
    @Nullable
    private final PowerSuppliers registry;
    @Nullable
    private final String supplierId;
    /** 1 si le joueur peut retirer (calculé par le serveur, envoyé au client pour griser les boutons). */
    private final DataSlot mayTake = DataSlot.standalone();

    /** Côté serveur avec le registre et le fournisseur ; côté client les deux sont nuls ({@link #fromNetwork}). */
    public PowerTillMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable PowerSuppliers registry,
            @Nullable String supplierId) {
        super(GridRegistry.POWER_TILL_MENU.get(), containerId, inventory, -1);
        this.pos = pos.immutable();
        this.registry = registry;
        this.supplierId = supplierId;
        addDataSlot(mayTake);
        if (registry != null && supplierId != null) {
            mayTake.set(registry.byId(supplierId).filter(s -> PowerRules.mayTakeTill(inventory.player, s)).isPresent()
                    ? 1 : 0);
        }
    }

    /** Côté client : ce que {@code PowerStationBlock#openTill} a écrit. */
    public static PowerTillMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new PowerTillMenu(containerId, inventory, buf.readBlockPos(), null, null);
    }

    public BlockPos pos() {
        return pos;
    }

    /** Le joueur peut-il retirer et faire le change (côté client : ce que le serveur a dit) ? */
    public boolean mayTake() {
        return mayTake.get() == 1;
    }

    @Override
    protected boolean isServerSide() {
        return registry != null && supplierId != null;
    }

    @Override
    protected EnumMap<Coin, Long> liveContents() {
        return new PowerTillStore(registry, supplierId).contents();
    }

    @Override
    public boolean usesPurses() {
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Codage d'une demande en numéro de bouton
    // ---------------------------------------------------------------------------------------------

    /** Le numéro de bouton d'une demande de l'écran ; -1 si elle ne correspond à aucun bouton. */
    public static int button(CoinRequest request) {
        int amountIndex = 0;
        if (request.amount() != 0) {
            int[] amounts = CoinRequest.amounts();
            amountIndex = -1;
            for (int i = 0; i < amounts.length; i++) {
                if (amounts[i] == request.amount()) {
                    amountIndex = i + 1;
                }
            }
        }
        if (amountIndex < 0 || request.action() < 0 || request.action() > 31 || request.coin() < 0 || request.coin() > 7) {
            return -1;
        }
        return (((request.action() * 8 + request.coin()) * 4 + amountIndex) * 2) + (request.all() ? 1 : 0);
    }

    /** La demande d'un numéro de bouton, si elle correspond à un bouton de l'écran ({@link CoinRequest#coinIfValid}). */
    public static Optional<CoinRequest> request(int button) {
        if (button < 0) {
            return Optional.empty();
        }
        boolean all = (button & 1) == 1;
        int amountIndex = (button >> 1) & 3;
        int coin = (button >> 3) & 7;
        int action = button >> 6;
        int[] amounts = CoinRequest.amounts();
        if (amountIndex > amounts.length) {
            return Optional.empty();
        }
        CoinRequest request = new CoinRequest(action, coin, amountIndex == 0 ? 0 : amounts[amountIndex - 1], all);
        return request.coinIfValid().isPresent() ? Optional.of(request) : Optional.empty();
    }

    // ---------------------------------------------------------------------------------------------
    // Gestes (serveur)
    // ---------------------------------------------------------------------------------------------

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
            serverPlayer.displayClientMessage(Component.translatable(PowerStationBlock.MESSAGE + "till_denied"), true);
        }
        return true;
    }

    /** Le contrôle et l'opération d'un numéro de bouton, sans le réseau. */
    public Result apply(ServerPlayer player, int id) {
        if (registry == null || supplierId == null) {
            return Result.NOT_OPEN;
        }
        if (player.containerMenu != this || !stillValid(player)) {
            return Result.NOT_OPEN;
        }
        Optional<CoinRequest> decoded = request(id);
        if (decoded.isEmpty()) {
            return Result.BAD_REQUEST;
        }
        CoinRequest request = decoded.get();
        if (request.action() == CoinRequest.DEPOSIT || request.action() == CoinRequest.DEPOSIT_ALL) {
            return Result.BAD_REQUEST;
        }
        Optional<PowerSupplier> supplier = registry.byId(supplierId);
        if (supplier.isEmpty()) {
            return Result.NO_SAFE;
        }
        if (!PowerRules.mayTakeTill(player, supplier.get())) {
            return Result.NOT_ALLOWED;
        }
        Coin coin = request.coinIfValid().orElseThrow();
        Result result = CoinActions.apply(player, new PowerTillStore(registry, supplierId), request.action(), coin,
                request.amount(), request.all());
        if (result == Result.DONE && !supplier.get().founder().equals(player.getUUID())) {
            LOGGER.info("Economie : {} a agi sur la caisse du fournisseur {} ({})", player.getGameProfile().getName(),
                    supplier.get().name(), request);
        }
        return result;
    }

    /**
     * Le poste est toujours là, à portée, toujours celui de ce fournisseur, et le joueur toujours ayant droit. Côté
     * client, seul le bloc compte.
     */
    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(pos).is(GridRegistry.POWER_STATION.get())) {
            return false;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) {
            return false;
        }
        if (registry == null || supplierId == null) {
            return true;
        }
        return player.level().getBlockEntity(pos) instanceof PowerStationBlockEntity be
                && be.supplier().filter(s -> s.id().equals(supplierId) && PowerRules.mayManage(player, s)).isPresent();
    }
}
