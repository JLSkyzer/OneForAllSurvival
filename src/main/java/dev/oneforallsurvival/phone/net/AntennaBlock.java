package dev.oneforallsurvival.phone.net;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.grid.supplier.PowerRules;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.phone.PhoneRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import org.slf4j.Logger;

/**
 * L'antenne d'un opérateur de téléphone : un mât de 4 x 16 x 4 px sur une semelle de 10 x 2 x 10 px, machine électrique
 * sans orientation, dont le voyant ne s'allume qu'en marche ({@code STATE} à {@code RUNNING}). Modèle hors du cube :
 * {@code noOcclusion()} (dans {@link PhoneRegistry}) et forme exacte.
 *
 * <ul>
 *   <li><b>Pose</b> : refusée sans joueur (distributeur, automate, faux joueur). L'objet d'une antenne démontée porte son
 *       opérateur ({@link AntennaItem#ITEM_OPERATOR}) : reposé par un ayant droit, il le reprend ; par un autre, refusé.
 *       Un objet neuf posé par le fondateur d'un opérateur rejoint le sien. Sinon l'antenne est à fonder : elle
 *       appartient à qui l'a posée, qui fonde l'opérateur en lui donnant un nom ({@link #found}).
 *   <li><b>Casse</b> : l'objet lâché garde l'opérateur ; l'inscription au registre s'efface et le ticket du chunk est
 *       rendu ({@link #onRemove}). L'opérateur, ses cartes et sa caisse restent.
 * </ul>
 *
 * <p>Ayants droit : ceux de {@link PowerRules#mayManage(Player, UUID)} (fondateur, ingénieur, maire, équipe), règle
 * provisoire que la tâche 4 remplace par celle de l'antenne (l'ingénieur avec son outil de maintenance), avec la
 * protection du bloc.
 */
public final class AntennaBlock extends ElectricMachineBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MESSAGE = "message.oneforallsurvival.antenna.";

    /** La semelle (10 x 2 x 10) et le mât (4 x 16 x 4), centrés : mêmes boîtes que les modèles {@code antenna*}. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(6, 0, 6, 10, 16, 10));

    public AntennaBlock(Properties properties) {
        super(properties, () -> PhoneRegistry.ANTENNA_BE.get());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AntennaBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------------------------------------
    // Pose
    // ---------------------------------------------------------------------------------------------

    /**
     * L'opérateur que reprend une antenne posée par {@code player} avec {@code stack}, ou vide pour une antenne à fonder.
     * L'objet qui porte un opérateur existant le reprend ; sinon, l'opérateur de ce joueur s'il en a un.
     */
    private static Optional<PhoneOperator> resumed(PhoneOperators registry, Player player, ItemStack stack) {
        Optional<PhoneOperator> carried = AntennaItem.operatorOf(stack).flatMap(registry::byId);
        return carried.isPresent() ? carried : registry.byFounder(player.getUUID());
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player instanceof FakePlayer) {
            return null;
        }
        if (context.getLevel() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer) {
            Optional<PhoneOperator> operator = resumed(PhoneOperators.get(level.getServer()), player,
                    context.getItemInHand());
            if (operator.isPresent() && !PowerRules.mayManage(player, operator.get().founder())) {
                serverPlayer.displayClientMessage(Component.translatable(MESSAGE + "not_yours", operator.get().name()),
                        true);
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
                || !(level.getBlockEntity(pos) instanceof AntennaBlockEntity be)) {
            return;
        }
        PhoneOperators registry = PhoneOperators.get(server.getServer());
        Optional<PhoneOperator> operator = resumed(registry, player, stack);
        // Une antenne posée ne garde rien de l'objet (un BlockEntityTag copié en créatif) : identité neuve, ou aucune.
        be.detach(player.getUUID(), player.getGameProfile().getName());
        if (operator.isPresent()) {
            inscribe(server, pos, be, operator.get().id());
            LOGGER.info("Telephone : {} a pose une antenne de l'operateur {} en {} ({})",
                    player.getGameProfile().getName(), operator.get().name(), pos.toShortString(),
                    server.dimension().location());
        }
    }

    /** Inscrit l'antenne en {@code pos} au registre sous un identifiant neuf, pour cet opérateur, et tient son chunk. */
    private static void inscribe(ServerLevel level, BlockPos pos, AntennaBlockEntity be, String operatorId) {
        UUID id = UUID.randomUUID();
        be.attach(operatorId, id);
        PhoneOperators.get(level.getServer()).putAntenna(new AntennaRecord(id, operatorId, level.dimension(), pos,
                EconomyClock.now(), false, level.getGameTime(), 0L, 0L, null));
        AntennaChunks.hold(level, pos);
    }

    /**
     * Fonde l'opérateur de l'antenne neuve en {@code pos}, au nom de celle ou celui qui l'a posée, et l'y inscrit comme
     * antenne principale. Réservé aux ayants droit du poseur ; une antenne sans poseur noté ({@code /setblock}), à
     * l'ingénieur, au maire et à l'équipe seulement (le fondateur est alors l'acteur). Refusé si l'antenne a déjà un
     * opérateur qui existe.
     *
     * @return l'issue ; vide si le geste n'est pas permis (pas d'antenne, pas le droit, déjà fondée)
     */
    public static Optional<PhoneOperators.Result> found(ServerPlayer actor, BlockPos pos, String name) {
        ServerLevel level = actor.serverLevel();
        if (!level.getBlockState(pos).is(PhoneRegistry.ANTENNA.get())
                || !(level.getBlockEntity(pos) instanceof AntennaBlockEntity be) || be.operator().isPresent()) {
            return Optional.empty();
        }
        UUID placer = be.placer();
        if (!PowerRules.mayManage(actor, placer)) {
            return Optional.empty();
        }
        UUID founder = placer == null ? actor.getUUID() : placer;
        String founderName = placer == null ? actor.getGameProfile().getName() : be.placerName();
        PhoneOperators registry = PhoneOperators.get(level.getServer());
        PhoneOperators.Result result = registry.found(founder, founderName, name, EconomyClock.now());
        if (result.founded()) {
            inscribe(level, pos, be, result.operator().id());
            registry.setMainAntenna(result.operator().id(), be.antennaId());
            LOGGER.info("Telephone : operateur {} fonde par {} pour {}, antenne en {}", result.operator().name(),
                    actor.getGameProfile().getName(), founderName, pos.toShortString());
        }
        return Optional.of(result);
    }

    // ---------------------------------------------------------------------------------------------
    // Ouverture
    // ---------------------------------------------------------------------------------------------

    /**
     * Le panneau de l'antenne arrive à la tâche 9 : d'ici là, un ayant droit lit un message provisoire, les autres à
     * quel opérateur elle est.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)
                && level.getBlockEntity(pos) instanceof AntennaBlockEntity be) {
            Optional<PhoneOperator> operator = be.operator();
            UUID owner = operator.map(PhoneOperator::founder).orElse(be.placer());
            if (PowerRules.mayManage(serverPlayer, owner)) {
                serverPlayer.displayClientMessage(Component.translatable(MESSAGE + "panel_soon"), true);
            } else if (operator.isPresent()) {
                serverPlayer.displayClientMessage(Component.translatable(MESSAGE + "of", operator.get().name()), true);
            } else {
                serverPlayer.displayClientMessage(Component.translatable(MESSAGE + "no_operator"), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    // ---------------------------------------------------------------------------------------------
    // Casse
    // ---------------------------------------------------------------------------------------------

    /** L'objet de l'antenne garde son opérateur (plus l'usure, par {@link ElectricMachineBlock}). */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        // L'inscription est déjà effacée ici (onRemove passe avant) : on lit l'opérateur par son identifiant.
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof AntennaBlockEntity be
                && be.operatorId() != null) {
            PhoneOperators.get(params.getLevel().getServer()).byId(be.operatorId()).ifPresent(operator -> {
                for (ItemStack drop : drops) {
                    if (drop.is(asItem())) {
                        AntennaItem.markOperator(drop, operator);
                    }
                }
            });
        }
        return drops;
    }

    /**
     * Une antenne qui quitte le monde (cassée, remplacée, explosée) efface son inscription (si c'est bien elle, à cette
     * place) et rend le ticket de son chunk ; l'opérateur, ses cartes et sa caisse restent.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            if (level.getBlockEntity(pos) instanceof AntennaBlockEntity be) {
                be.record().ifPresent(record -> PhoneOperators.get(server.getServer()).removeAntenna(record.id()));
            }
            AntennaChunks.release(server, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
