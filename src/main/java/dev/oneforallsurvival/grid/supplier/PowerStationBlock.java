package dev.oneforallsurvival.grid.supplier;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;

/**
 * Le poste de fournisseur, en tête du réseau d'un fournisseur d'électricité. Bloc en ligne (sortie devant, entrée
 * derrière), fabriqué par l'ingénieur seul (recette à l'établi), posé par n'importe quel joueur.
 *
 * <ul>
 *   <li><b>Pose</b> : refusée sans joueur (distributeur, automate). L'objet d'un poste démonté porte l'identifiant de
 *       son fournisseur ({@link #ITEM_SUPPLIER}) : reposé par un ayant droit, il reprend ce fournisseur, caisse et
 *       clients gardés par le serveur. Un objet neuf posé par un joueur qui a déjà un fournisseur reprend le sien.
 *       Un second poste du même fournisseur est refusé tant que le premier tient. Sinon le poste est neuf : il
 *       appartient à qui l'a posé, qui le fonde en lui donnant un nom ({@link #found}).
 *   <li><b>Ouverture</b> : un ayant droit ({@link PowerRules#mayManage}) voit la caisse ({@link PowerTillMenu}), dont
 *       seuls le fondateur et l'équipe retirent ; les autres lisent à qui il est.
 *   <li><b>Casse</b> : comme le coffre-fort, dureté -1, résistance maximale, piston bloqué, tags du Wither et du
 *       dragon ; la progression n'est rendue qu'aux ayants droit, et {@link #onBreak} annule tout autre bris. Il lâche
 *       son objet, qui garde le fournisseur ; l'emplacement noté s'efface.
 * </ul>
 */
public final class PowerStationBlock extends InlineEnergyBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MESSAGE = "message.oneforallsurvival.power_station.";

    /** Clés de l'objet : l'identifiant du fournisseur (seul compté) et son nom (pour l'infobulle). */
    public static final String ITEM_SUPPLIER = "oneforallsurvival_supplier";
    public static final String ITEM_SUPPLIER_NAME = "oneforallsurvival_supplier_name";

    public PowerStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerStationBlockEntity(pos, state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---------------------------------------------------------------------------------------------
    // Objet
    // ---------------------------------------------------------------------------------------------

    /** L'identifiant du fournisseur porté par l'objet, s'il y en a un. */
    public static Optional<String> supplierOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ITEM_SUPPLIER)) {
            return Optional.empty();
        }
        String id = tag.getString(ITEM_SUPPLIER);
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }

    /** Écrit le fournisseur sur l'objet d'un poste démonté. */
    public static void markItem(ItemStack stack, PowerSupplier supplier) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(ITEM_SUPPLIER, supplier.id());
        tag.putString(ITEM_SUPPLIER_NAME, supplier.name());
    }

    // ---------------------------------------------------------------------------------------------
    // Le poste unique d'un fournisseur
    // ---------------------------------------------------------------------------------------------

    /**
     * Le poste de ce fournisseur tient-il ? Comme {@code SafeBlock.hasLiveSafe} : sans emplacement noté, non ; dans un
     * chunk non chargé, on ne peut pas vérifier, il compte ; si le bloc n'y est plus (retiré sans que le jeu l'ait
     * su), la note est périmée et ne compte pas.
     */
    public static boolean hasLiveStation(MinecraftServer server, PowerSupplier supplier) {
        GlobalPos noted = supplier.station();
        if (noted == null) {
            return false;
        }
        ServerLevel level = server.getLevel(noted.dimension());
        if (level == null) {
            return false;
        }
        BlockPos pos = noted.pos();
        if (!level.isLoaded(pos)) {
            return true;
        }
        return level.getBlockState(pos).is(GridRegistry.POWER_STATION.get())
                && level.getBlockEntity(pos) instanceof PowerStationBlockEntity be && supplier.id().equals(be.supplierId());
    }

    /**
     * Le fournisseur que reprend un poste posé par {@code player} avec {@code stack}, ou vide pour un poste neuf.
     * L'objet qui porte un fournisseur existant le reprend ; sinon, le fournisseur de ce joueur s'il en a un.
     */
    private static Optional<PowerSupplier> resumed(PowerSuppliers registry, Player player, ItemStack stack) {
        Optional<PowerSupplier> carried = supplierOf(stack).flatMap(registry::byId);
        return carried.isPresent() ? carried : registry.byFounder(player.getUUID());
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
        if (context.getLevel() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = level.getServer();
            Optional<PowerSupplier> supplier = resumed(PowerSuppliers.get(server), player, context.getItemInHand());
            Component refusal = null;
            if (supplier.isPresent() && !PowerRules.mayManage(player, supplier.get())) {
                refusal = Component.translatable(MESSAGE + "not_yours", supplier.get().name());
            } else if (supplier.isPresent() && hasLiveStation(server, supplier.get())) {
                BlockPos at = supplier.get().station().pos();
                refusal = Component.translatable(MESSAGE + "has_station", supplier.get().name(), at.getX(), at.getY(),
                        at.getZ());
            }
            if (refusal != null) {
                serverPlayer.displayClientMessage(refusal, true);
                // Le client a prédit la pose et retiré l'objet de sa main : on lui renvoie l'inventaire réel.
                serverPlayer.containerMenu.sendAllDataToRemote();
                return null;
            }
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server) || !(placer instanceof ServerPlayer player) || player instanceof FakePlayer
                || !(level.getBlockEntity(pos) instanceof PowerStationBlockEntity be)) {
            return;
        }
        PowerSuppliers registry = PowerSuppliers.get(server.getServer());
        Optional<PowerSupplier> supplier = resumed(registry, player, stack);
        if (supplier.isPresent()) {
            be.setFounder(supplier.get().founder(), supplier.get().founderName());
            be.setSupplierId(supplier.get().id());
            registry.setStation(supplier.get().id(), GlobalPos.of(server.dimension(), pos));
            LOGGER.info("Electricite : {} a pose le poste du fournisseur {} en {} ({})", player.getGameProfile().getName(),
                    supplier.get().name(), pos.toShortString(), server.dimension().location());
        } else {
            // Un poste neuf ne garde aucun identifiant venu de l'objet (un BlockEntityTag copié en créatif).
            be.setSupplierId(null);
            be.setFounder(player.getUUID(), player.getGameProfile().getName());
        }
    }

    /**
     * Fonde le fournisseur du poste neuf en {@code pos}, au nom de celui qui l'a posé, et le note comme son poste.
     * Réservé aux ayants droit de ce poste ; un poste sans poseur noté, à l'ingénieur, au maire et à l'équipe seulement
     * (le fondateur est alors l'acteur). Refusé si le poste a déjà un fournisseur.
     *
     * @return l'issue ; vide si le geste n'est pas permis (pas de poste, pas le droit, déjà fondé)
     */
    public static Optional<PowerSuppliers.Result> found(ServerPlayer actor, BlockPos pos, String name) {
        ServerLevel level = actor.serverLevel();
        if (!level.getBlockState(pos).is(GridRegistry.POWER_STATION.get())
                || !(level.getBlockEntity(pos) instanceof PowerStationBlockEntity be) || be.supplier().isPresent()) {
            return Optional.empty();
        }
        // Un poste sans poseur noté (/setblock, structure) n'appartient à personne : seuls l'ingénieur, le maire et
        // l'équipe le fondent (à leur nom). Sinon, les ayants droit du poseur, au nom du poseur.
        if (!PowerRules.mayManage(actor, be.founder())) {
            return Optional.empty();
        }
        UUID founder = be.founder() == null ? actor.getUUID() : be.founder();
        String founderName = be.founder() == null ? actor.getGameProfile().getName() : be.founderName();
        PowerSuppliers registry = PowerSuppliers.get(level.getServer());
        PowerSuppliers.Result result = registry.found(founder, founderName, name);
        if (result.founded()) {
            be.setFounder(founder, founderName);
            be.setSupplierId(result.supplier().id());
            registry.setStation(result.supplier().id(), GlobalPos.of(level.dimension(), pos));
            LOGGER.info("Electricite : fournisseur {} fonde par {} pour {}, poste en {}", result.supplier().name(),
                    actor.getGameProfile().getName(), founderName, pos.toShortString());
        }
        return Optional.of(result);
    }

    // ---------------------------------------------------------------------------------------------
    // Ouverture
    // ---------------------------------------------------------------------------------------------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)) {
            open(serverPlayer, pos);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Ouvre la caisse du poste en {@code pos} pour un ayant droit ; sinon dit à qui est le poste.
     *
     * @return vrai si l'écran s'est ouvert
     */
    public static boolean open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.getBlockState(pos).is(GridRegistry.POWER_STATION.get())
                || !(level.getBlockEntity(pos) instanceof PowerStationBlockEntity be)) {
            return false;
        }
        Optional<PowerSupplier> supplier = be.supplier();
        if (supplier.isEmpty()) {
            player.displayClientMessage(Component.translatable(MESSAGE
                    + (PowerRules.mayManage(player, be.founder()) ? "unnamed" : "no_supplier")), true);
            return false;
        }
        if (!PowerRules.mayManage(player, supplier.get())) {
            player.displayClientMessage(Component.translatable(MESSAGE + "of", supplier.get().name()), true);
            return false;
        }
        PowerSuppliers registry = PowerSuppliers.get(level.getServer());
        String id = supplier.get().id();
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((containerId, inventory, p) -> new PowerTillMenu(containerId, inventory, pos,
                        registry, id), Component.translatable("container.oneforallsurvival.power_till",
                        supplier.get().name())),
                buf -> buf.writeBlockPos(pos));
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Casse
    // ---------------------------------------------------------------------------------------------

    /** Le propriétaire du poste : le fondateur de son fournisseur, ou qui l'a posé tant qu'il n'en a pas. */
    @Nullable
    private static UUID owner(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof PowerStationBlockEntity be)) {
            return null;
        }
        return be.supplier().map(PowerSupplier::founder).orElse(be.founder());
    }

    /**
     * Dureté -1 : seuls les ayants droit en ont une progression, calculée comme celle d'un bloc de dureté 5 sans outil
     * exigé. Côté client les rôles sont inconnus : la progression s'affiche, le serveur tranche.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!PowerRules.mayManage(player, owner(level, pos))) {
            return 0.0F;
        }
        return player.getDigSpeed(state, pos) / 5.0F / 30.0F;
    }

    /** Au début de la casse (côté serveur), dit pourquoi elle n'avancera pas. */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && !PowerRules.mayManage(player, owner(level, pos))) {
            serverPlayer.displayClientMessage(Component.translatable(MESSAGE + "break_denied"), true);
        }
    }

    /**
     * Filet de sécurité : quelle que soit la manière dont un joueur s'y prend (créatif, faux joueur d'un automate), un
     * bris par qui n'a pas la main est annulé. Branché sur le bus de Forge.
     */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof PowerStationBlock) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!PowerRules.mayManage(event.getPlayer(), owner(level, event.getPos()))) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                player.displayClientMessage(Component.translatable(MESSAGE + "break_denied"), true);
            }
        }
    }

    /** L'objet du poste garde son fournisseur : reposé, il le reprend. Un poste neuf laisse un objet nu. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        // Le bloc est déjà retiré ici (son emplacement noté effacé) : on lit le fournisseur par son identifiant.
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PowerStationBlockEntity be
                && be.supplierId() != null) {
            PowerSuppliers.get(params.getLevel().getServer()).byId(be.supplierId()).ifPresent(supplier -> {
                for (ItemStack drop : drops) {
                    if (drop.is(asItem())) {
                        markItem(drop, supplier);
                    }
                }
            });
        }
        return drops;
    }

    /** Un poste retiré (cassé, remplacé) efface l'emplacement noté ; le fournisseur, sa caisse et ses clients restent. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof PowerStationBlockEntity be && be.supplierId() != null) {
            PowerSuppliers.get(server.getServer()).clearStation(be.supplierId(), GlobalPos.of(server.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
