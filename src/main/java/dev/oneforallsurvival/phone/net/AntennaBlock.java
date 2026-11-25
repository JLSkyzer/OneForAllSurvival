package dev.oneforallsurvival.phone.net;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.repair.RepairRegistry;
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
import net.minecraftforge.event.level.BlockEvent;
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
 *   <li><b>Casse</b> : comme le poste de fournisseur, dureté -1, résistance maximale, piston bloqué, tags du Wither, du
 *       dragon et {@code create:non_movable} ; la progression n'est rendue qu'aux ayants droit, et {@link #onBreak}
 *       annule tout autre bris (faux joueur compris). L'objet lâché garde l'opérateur ; l'inscription au registre
 *       s'efface et le ticket du chunk est rendu ({@link #onRemove}). L'opérateur, ses cartes et sa caisse restent.
 * </ul>
 *
 * <p>Ayants droit : ceux de {@link AntennaRules#mayManage} (fondateur, maire, équipe, ingénieur qui tient l'outil de
 * maintenance) ; fonder au nom du poseur : {@link AntennaRules#mayFoundFor}.
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
     * L'objet qui porte un opérateur existant le reprend ; l'objet d'un opérateur supprimé dont l'ancien fondateur est
     * connu ({@link #formerOf}) ne reprend rien (antenne à fonder pour cet ancien fondateur, jamais l'opérateur d'un
     * autre) ; sinon, l'opérateur de ce joueur s'il en a un.
     */
    private static Optional<PhoneOperator> resumed(PhoneOperators registry, Player player, ItemStack stack) {
        Optional<PhoneOperator> carried = AntennaItem.operatorOf(stack).flatMap(registry::byId);
        if (carried.isPresent()) {
            return carried;
        }
        if (formerOf(registry, stack).isPresent()) {
            return Optional.empty();
        }
        return registry.byFounder(player.getUUID());
    }

    /** L'ancien fondateur de l'opérateur supprimé que porte cet objet, s'il est connu du registre. */
    private static Optional<PhoneOperators.FormerFounder> formerOf(PhoneOperators registry, ItemStack stack) {
        return AntennaItem.operatorOf(stack).flatMap(registry::formerFounder);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player instanceof FakePlayer) {
            return null;
        }
        if (context.getLevel() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer) {
            PhoneOperators registry = PhoneOperators.get(level.getServer());
            Optional<PhoneOperator> operator = resumed(registry, player, context.getItemInHand());
            Optional<PhoneOperators.FormerFounder> former = formerOf(registry, context.getItemInHand());
            // L'objet d'un opérateur supprimé appartient à son ancien fondateur : ses ayants droit seuls le reposent.
            boolean refused = operator.isPresent() ? !AntennaRules.mayManage(player, operator.get().founder())
                    : former.isPresent() && !AntennaRules.mayManage(player, former.get().uuid());
            if (refused) {
                serverPlayer.displayClientMessage(operator.isPresent()
                        ? Component.translatable(MESSAGE + "not_yours", operator.get().name())
                        : Component.translatable(MESSAGE + "not_yours_removed", former.get().name()), true);
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
        // L'objet d'un opérateur supprimé redevient une antenne à fonder pour l'ancien fondateur, pas pour le poseur.
        Optional<PhoneOperators.FormerFounder> former = operator.isPresent() ? Optional.empty()
                : formerOf(registry, stack);
        if (former.isPresent()) {
            be.detach(former.get().uuid(), former.get().name());
        } else {
            be.detach(player.getUUID(), player.getGameProfile().getName());
        }
        if (operator.isPresent()) {
            inscribe(server, pos, be, operator.get().id());
            LOGGER.info("Telephone : {} a pose une antenne de l'operateur {} en {} ({})",
                    player.getGameProfile().getName(), operator.get().name(), pos.toShortString(),
                    server.dimension().location());
        }
    }

    /**
     * Inscrit l'antenne en {@code pos} au registre sous un identifiant neuf, pour cet opérateur, et tient son chunk. Aussi
     * appelé par l'antenne elle-même quand son poseur a fondé un opérateur depuis la pose
     * ({@link AntennaBlockEntity#afterTick}).
     */
    static void inscribe(ServerLevel level, BlockPos pos, AntennaBlockEntity be, String operatorId) {
        UUID id = UUID.randomUUID();
        be.attach(operatorId, id);
        PhoneOperators.get(level.getServer()).putAntenna(new AntennaRecord(id, operatorId, level.dimension(), pos,
                EconomyClock.now(), false, level.getGameTime(), 0L, 0L, null));
        AntennaChunks.hold(level, pos);
    }

    /**
     * Fonde l'opérateur de l'antenne neuve en {@code pos}, au nom de celle ou celui qui l'a posée, et l'y inscrit comme
     * antenne principale. Au nom du poseur : le poseur lui-même, le maire et l'équipe ({@link AntennaRules#mayFoundFor} ;
     * l'ingénieur, même outillé, ne fonde pas au nom d'un autre). Une antenne sans poseur noté ({@code /setblock}) : le
     * maire, l'équipe et l'ingénieur outillé, à leur nom. Refusé si l'antenne a déjà un opérateur qui existe.
     *
     * <p>Le bloc doit être chargé (rien n'est chargé de force) et être une antenne. Le panneau (seul chemin d'un joueur)
     * a vérifié juste avant que l'antenne est celle qu'il a ouverte, à {@value OperatorPanels#MAX_DISTANCE} blocs au plus
     * de l'acteur ({@link OperatorActions}) ; les essais fondent aussi de loin, sans panneau.
     *
     * @return l'issue ; vide si le geste n'est pas permis (pas d'antenne, chunk non chargé, pas le droit, déjà fondée)
     */
    public static Optional<PhoneOperators.Result> found(ServerPlayer actor, BlockPos pos, String name) {
        ServerLevel level = actor.serverLevel();
        if (pos == null || !level.isLoaded(pos) || !level.getBlockState(pos).is(PhoneRegistry.ANTENNA.get())
                || !(level.getBlockEntity(pos) instanceof AntennaBlockEntity be) || be.operator().isPresent()) {
            return Optional.empty();
        }
        // Une antenne restée posée d'un opérateur supprimé est à fonder pour son ancien fondateur (tâche 11).
        PhoneOperators.FormerFounder idle = be.idleOwner();
        UUID placer = idle == null ? null : idle.uuid();
        if (!AntennaRules.mayFoundFor(actor, placer)) {
            return Optional.empty();
        }
        UUID founder = placer == null ? actor.getUUID() : placer;
        String founderName = placer == null ? actor.getGameProfile().getName() : idle.name();
        PhoneOperators registry = PhoneOperators.get(level.getServer());
        PhoneOperators.Result result = registry.found(founder, founderName, name, EconomyClock.now());
        if (result.founded()) {
            // Une inscription laissée à cette place par un opérateur retiré depuis ne doit pas survivre en fantôme.
            be.record().ifPresent(ghost -> registry.removeAntenna(ghost.id()));
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
     * Le testeur en main principale est laissé au testeur (rapport de machine, comme au lot 2). Sinon un ayant droit
     * ({@link AntennaRules#mayManage}) ouvre le panneau de l'opérateur ({@link OperatorPanels#openFromBlock}) ; les
     * autres lisent à quel opérateur elle est.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.getMainHandItem().is(RepairRegistry.TESTER.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)
                && level.getBlockEntity(pos) instanceof AntennaBlockEntity be) {
            Optional<PhoneOperator> operator = be.operator();
            if (AntennaRules.mayManage(serverPlayer, owner(level, pos))) {
                OperatorPanels.openFromBlock(serverPlayer, pos);
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

    /**
     * Le propriétaire de l'antenne : le fondateur de son opérateur ; sans opérateur en service, l'ancien fondateur de
     * l'opérateur supprimé qu'elle portait, sinon qui l'a posée ({@link AntennaBlockEntity#idleOwner} ; nul si personne).
     */
    @Nullable
    static UUID owner(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AntennaBlockEntity be)) {
            return null;
        }
        Optional<PhoneOperator> operator = be.operator();
        if (operator.isPresent()) {
            return operator.get().founder();
        }
        PhoneOperators.FormerFounder idle = be.idleOwner();
        return idle == null ? null : idle.uuid();
    }

    /**
     * Dureté -1 : seuls les ayants droit en ont une progression, calculée comme celle d'un bloc de dureté 5 sans outil
     * exigé. Côté client les rôles sont inconnus : la progression s'affiche, le serveur tranche.
     */
    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!AntennaRules.mayManage(player, owner(level, pos))) {
            return 0.0F;
        }
        return player.getDigSpeed(state, pos) / 5.0F / 30.0F;
    }

    /** Au début de la casse (côté serveur), dit pourquoi elle n'avancera pas. */
    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && !AntennaRules.mayManage(player, owner(level, pos))) {
            serverPlayer.displayClientMessage(Component.translatable(MESSAGE + "break_denied"), true);
        }
    }

    /**
     * Filet de sécurité : quelle que soit la manière dont un joueur s'y prend (créatif, faux joueur d'un automate), un
     * bris par qui n'a pas la main est annulé. Branché sur le bus de Forge.
     */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof AntennaBlock) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!AntennaRules.mayManage(event.getPlayer(), owner(level, event.getPos()))) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                player.displayClientMessage(Component.translatable(MESSAGE + "break_denied"), true);
            }
        }
    }

    /**
     * L'objet de l'antenne garde son opérateur (plus l'usure, par {@link ElectricMachineBlock}) ; celui d'une antenne
     * dont l'opérateur a été supprimé garde la marque de l'opérateur supprimé (reposé, il redevient une antenne à fonder
     * pour l'ancien fondateur).
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        // L'inscription est déjà effacée ici (onRemove passe avant) : on lit l'opérateur par son identifiant.
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof AntennaBlockEntity be
                && be.operatorId() != null) {
            PhoneOperators registry = PhoneOperators.get(params.getLevel().getServer());
            Optional<PhoneOperator> operator = registry.byId(be.operatorId());
            boolean removed = operator.isEmpty() && registry.formerFounder(be.operatorId()).isPresent();
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    if (operator.isPresent()) {
                        AntennaItem.markOperator(drop, operator.get());
                    } else if (removed) {
                        AntennaItem.markRemovedOperator(drop, be.operatorId());
                    }
                }
            }
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
