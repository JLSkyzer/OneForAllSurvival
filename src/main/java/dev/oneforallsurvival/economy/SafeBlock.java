package dev.oneforallsurvival.economy;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;

/**
 * Le coffre-fort personnel : un par joueur, dont le contenu est gardé par le serveur ({@link SafeAccounts}), pas par
 * le bloc.
 *
 * <ul>
 *   <li><b>Pose</b> : elle note le propriétaire, et elle est refusée avec un message si le joueur en a déjà un, ou
 *       s'il n'y a pas de joueur (distributeur, automate).
 *   <li><b>Ouverture</b> : le propriétaire et l'équipe (niveau 2) seulement ({@link #mayAccess}).
 *   <li><b>Casse</b> : le propriétaire quand son compte est vide, l'équipe à tout moment ({@link #mayBreak}). Il est
 *       déclaré incassable (dureté -1, résistance maximale, piston bloqué, tags du Wither et du dragon), ce qui le
 *       garde aussi des foreuses d'autres mods ; la progression de casse est rendue à ceux qui y ont droit par
 *       {@link #getDestroyProgress}, et {@link #onBreak} annule tout autre bris. Il ne lâche que son bloc : casser
 *       un coffre-fort plein par l'équipe laisse le compte intact et efface seulement l'emplacement noté.
 * </ul>
 */
public final class SafeBlock extends Block implements EntityBlock {

    public static final String MESSAGE = "message.oneforallsurvival.safe.";

    /** Distance maximale du joueur au coffre-fort, au carré (8 blocs) : celle de l'écran et des paquets. */
    public static final double MAX_DISTANCE_SQR = 64.0D;

    private static final Logger LOGGER = LogUtils.getLogger();

    public SafeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SafeBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------------------------------------
    // Droits
    // ---------------------------------------------------------------------------------------------

    /**
     * Ce joueur peut-il ouvrir le coffre-fort de {@code owner} ? Le propriétaire et l'équipe (niveau 2).
     *
     * <p>Point d'extension : le crochetage du policier (lot suivant) s'ajoute ici, comme un droit d'ouverture
     * temporaire accordé par ce lot. Ouvrir donne le droit de déposer, retirer et regrouper, rien de plus.
     */
    public static boolean mayAccess(Player player, @Nullable UUID owner) {
        return player.hasPermissions(2) || (owner != null && owner.equals(player.getUUID()));
    }

    /**
     * Ce joueur peut-il casser le coffre-fort de {@code owner} ? L'équipe toujours ; le propriétaire seulement
     * quand son compte est vide ({@code accounts} nul, côté client : il ne voit pas le compte, le serveur tranche).
     */
    public static boolean mayBreak(Player player, @Nullable UUID owner, @Nullable SafeAccounts accounts) {
        if (player.hasPermissions(2)) {
            return true;
        }
        if (owner == null || !owner.equals(player.getUUID())) {
            return false;
        }
        return accounts == null || accounts.total(owner) == 0L;
    }

    /**
     * Ce joueur a-t-il déjà un coffre-fort qui tient ? L'emplacement noté est vérifié quand son chunk est chargé :
     * si le bloc n'y est plus (retiré sans que le jeu l'ait su), la note est effacée au lieu de bloquer le joueur
     * pour toujours. Dans un chunk non chargé on ne peut pas vérifier : le coffre-fort compte.
     */
    public static boolean hasLiveSafe(MinecraftServer server, UUID owner) {
        SafeAccounts accounts = SafeAccounts.get(server);
        Optional<GlobalPos> noted = accounts.safePos(owner);
        if (noted.isEmpty()) {
            return false;
        }
        ServerLevel level = server.getLevel(noted.get().dimension());
        if (level == null) {
            accounts.forgetSafePos(owner);
            return false;
        }
        BlockPos pos = noted.get().pos();
        if (!level.isLoaded(pos)) {
            return true;
        }
        if (level.getBlockState(pos).is(EconomyRegistry.SAFE.get()) && level.getBlockEntity(pos) instanceof SafeBlockEntity be
                && owner.equals(be.owner())) {
            return true;
        }
        LOGGER.warn("Economie : le coffre-fort note de {} n'est plus en {} ; la note est effacee", owner, noted.get());
        accounts.forgetSafePos(owner);
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Pose
    // ---------------------------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player instanceof FakePlayer) {
            return null;
        }
        if (context.getLevel() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer
                && hasLiveSafe(level.getServer(), serverPlayer.getUUID())) {
            serverPlayer.displayClientMessage(Component.translatable(MESSAGE + "has_one"), true);
            return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && placer instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && level.getBlockEntity(pos) instanceof SafeBlockEntity be) {
            be.setOwner(player.getUUID());
            SafeAccounts.get(server.getServer()).setSafePos(player.getUUID(), GlobalPos.of(server.dimension(), pos));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof SafeBlockEntity be && be.owner() != null) {
            SafeAccounts.get(server.getServer()).clearSafePos(be.owner(), GlobalPos.of(server.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    // ---------------------------------------------------------------------------------------------
    // Ouverture
    // ---------------------------------------------------------------------------------------------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, pos);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Ouvre l'écran du coffre-fort en {@code pos} si le joueur y a droit ; sinon le prévient.
     *
     * @return vrai si l'écran s'est ouvert
     */
    public static boolean open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof SafeBlockEntity be) || !level.getBlockState(pos).is(EconomyRegistry.SAFE.get())) {
            return false;
        }
        UUID owner = be.owner();
        if (!mayAccess(player, owner)) {
            player.displayClientMessage(Component.translatable(MESSAGE + "not_yours"), true);
            return false;
        }
        if (owner == null) {
            player.displayClientMessage(Component.translatable(MESSAGE + "unowned"), true);
            return false;
        }
        MinecraftServer server = level.getServer();
        Component title = owner.equals(player.getUUID())
                ? Component.translatable(EconomyRegistry.SAFE.get().getDescriptionId())
                : Component.translatable("container.oneforallsurvival.safe.of", ownerName(server, owner));
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, p) -> new SafeMenu(id, inventory, pos, owner,
                        SafeAccounts.get(server)), title),
                buf -> buf.writeBlockPos(pos));
        return true;
    }

    private static String ownerName(MinecraftServer server, UUID owner) {
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache() == null ? owner.toString()
                : server.getProfileCache().get(owner).map(GameProfile::getName).orElse(owner.toString());
    }

    // ---------------------------------------------------------------------------------------------
    // Casse
    // ---------------------------------------------------------------------------------------------

    /**
     * Dureté -1 : seuls ceux qui ont le droit de le casser en ont une progression, calculée comme celle d'un bloc
     * de dureté 5 sans outil exigé. Pour tous les autres, zéro. Côté client le compte est inconnu : le propriétaire
     * voit la progression, le serveur tranche.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        UUID owner = level.getBlockEntity(pos) instanceof SafeBlockEntity be ? be.owner() : null;
        SafeAccounts accounts = level instanceof ServerLevel server ? SafeAccounts.get(server.getServer()) : null;
        if (!mayBreak(player, owner, accounts)) {
            return 0.0F;
        }
        return player.getDigSpeed(state, pos) / 5.0F / 30.0F;
    }

    /** Au début de la casse (côté serveur), dit pourquoi elle n'avancera pas. */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SafeBlockEntity be) {
            tellWhyNotBreakable(serverPlayer, be.owner(), SafeAccounts.get(serverPlayer.server));
        }
    }

    private static void tellWhyNotBreakable(ServerPlayer player, @Nullable UUID owner, SafeAccounts accounts) {
        if (mayBreak(player, owner, accounts)) {
            return;
        }
        boolean mine = owner != null && owner.equals(player.getUUID());
        player.displayClientMessage(Component.translatable(MESSAGE + (mine ? "not_empty" : "not_yours")), true);
    }

    /**
     * Filet de sécurité : quelle que soit la manière dont un joueur s'y prend (créatif compris), un bris qui n'est
     * pas permis par {@link #mayBreak} est annulé. Branché sur le bus de Forge.
     */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof SafeBlock) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID owner = level.getBlockEntity(event.getPos()) instanceof SafeBlockEntity be ? be.owner() : null;
        SafeAccounts accounts = SafeAccounts.get(level.getServer());
        if (!mayBreak(event.getPlayer(), owner, accounts)) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                tellWhyNotBreakable(player, owner, accounts);
            }
        }
    }
}
