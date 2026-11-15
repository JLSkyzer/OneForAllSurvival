package dev.oneforallsurvival.economy.quest;

import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.loot.MobLoot;
import dev.oneforallsurvival.economy.loot.MobOrigin;
import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * La progression des quêtes par les gestes du joueur, et ses garde-fous. Seul un vrai joueur compte : un
 * {@link ServerPlayer} de la liste des joueurs du serveur, ni faux joueur, ni spectateur, ni créatif. Une ferme
 * (déployeur, distributeur, trémie, piston, contraption) ne fait avancer aucune quête.
 *
 * <ul>
 *   <li><b>Casse</b> : {@code BreakEvent}, bloc non marqué « posé » ({@link PlacedBlocks}) que le joueur a le droit de
 *       récolter ({@code canHarvestBlock}). <b>Récolte</b> : casse d'un {@link CropBlock} mûr (posé ou non).</li>
 *   <li><b>Pose</b> : {@code EntityPlaceEvent}. Tout bloc posé est marqué, par qui que ce soit ; un bloc formé par un
 *       fluide et la destination d'un bloc poussé par un piston aussi. Un joueur qui casse un bloc qu'il a posé depuis
 *       moins de {@link EconomyConfig#questPlaceUndoSeconds()} retire la pose comptée ({@link RecentPlacements}).</li>
 *   <li><b>Combat</b> : {@code LivingDropsEvent}, joueur de {@link MobLoot#creditedPlayer}, mob sauvage et à personne
 *       ({@link MobOrigin}) ; sans cible, un monstre.</li>
 *   <li><b>Pêche</b>, <b>élevage</b> : {@code ItemFishedEvent}, {@code BabyEntitySpawnEvent}.</li>
 *   <li><b>Cuisine</b>, <b>conserve</b> : à la sortie d'un four, d'un fumoir, de la marmite, du séchoir ou du Cooking Pot
 *       de Farmer's Delight, par les crochets {@link #cooked}, {@link #preserved} et {@link #furnaceOutput}.</li>
 *   <li><b>Métier</b> : {@link #job}, appelé par le geste de métier (réparation d'une machine).</li>
 * </ul>
 *
 * <p>Le prédicat d'une catégorie reçoit la cible de la quête, ou {@code null} pour une quête sans cible : c'est lui
 * qui dit ce que « sans cible » couvre (un monstre pour le combat, un aliment pour la cuisine).
 */
public final class QuestProgress {

    private static final RecentPlacements RECENT = new RecentPlacements();
    /** Essais seulement : décalage de l'horloge de jeu vue pour ce joueur (poses et casses). */
    private static final Map<UUID, Long> TIME_SHIFT = new ConcurrentHashMap<>();

    private QuestProgress() {}

    // ---------------------------------------------------------------------------------------------
    // Qui compte
    // ---------------------------------------------------------------------------------------------

    /** Un vrai joueur connecté, en survie ou en aventure. */
    public static boolean eligible(@Nullable Entity entity) {
        if (!(entity instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return false;
        }
        if (player.isSpectator() || player.isCreative()) {
            return false;
        }
        return player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    // ---------------------------------------------------------------------------------------------
    // Progression
    // ---------------------------------------------------------------------------------------------

    /**
     * Fait avancer de {@code amount} toutes les quêtes non finies et non réclamées du joueur (journalières,
     * hebdomadaires, événements) de cette catégorie que {@code matches} accepte ({@code null} : quête sans cible).
     */
    public static void add(ServerPlayer player, QuestCategory category, Predicate<QuestTarget> matches, int amount) {
        advance(player, category, matches, amount);
    }

    /** Comme {@link #add}, et rend les clés des quêtes avancées ({@link #key}). */
    private static List<String> advance(ServerPlayer player, QuestCategory category, Predicate<QuestTarget> matches,
            int amount) {
        if (amount <= 0 || !eligible(player)) {
            return List.of();
        }
        QuestBook book = QuestBook.of(player);
        List<String> keys = new ArrayList<>();
        boolean finished = false;
        for (Series series : Series.values()) {
            List<Quest> list = book.series(series);
            for (int i = 0; i < list.size(); i++) {
                Quest quest = list.get(i);
                if (advances(quest, category, matches)) {
                    Quest next = quest.withProgress((int) Math.min((long) quest.progress() + amount, quest.goal()));
                    book.set(series, i, next);
                    keys.add(key(series, book.seriesKey(series), quest.templateId()));
                    finished |= next.complete();
                }
            }
        }
        for (Map.Entry<String, Quest> entry : book.events().entrySet()) {
            Quest quest = entry.getValue();
            if (advances(quest, category, matches)) {
                Quest next = quest.withProgress((int) Math.min((long) quest.progress() + amount, quest.goal()));
                entry.setValue(next);
                keys.add("event|" + entry.getKey());
                finished |= next.complete();
            }
        }
        if (!keys.isEmpty()) {
            book.saveTo(player);
            if (finished) {
                QuestService.markDirty(player);
            }
        }
        return keys;
    }

    private static boolean advances(Quest quest, QuestCategory category, Predicate<QuestTarget> matches) {
        return quest.category() == category && !quest.claimed() && !quest.complete() && matches.test(quest.target());
    }

    /** La clé d'une quête de série : liste, clé de la série, modèle. */
    private static String key(Series series, @Nullable LocalDate seriesKey, String templateId) {
        return series.name().toLowerCase(java.util.Locale.ROOT) + "|" + seriesKey + "|" + templateId;
    }

    /** Retire 1 à la quête de cette clé, si elle existe encore, n'est pas réclamée et a avancé. */
    private static void undo(ServerPlayer player, String key) {
        QuestBook book = QuestBook.of(player);
        String[] parts = key.split("\\|", 3);
        boolean changed = false;
        boolean wasComplete = false;
        if (parts[0].equals("event") && parts.length == 2) {
            Quest quest = book.events().get(parts[1]);
            if (quest != null && !quest.claimed() && quest.progress() > 0) {
                wasComplete = quest.complete();
                book.events().put(parts[1], quest.withProgress(quest.progress() - 1));
                changed = true;
            }
        } else if (parts.length == 3) {
            Series series = parts[0].equals("daily") ? Series.DAILY : Series.WEEKLY;
            if (String.valueOf(book.seriesKey(series)).equals(parts[1])) {
                List<Quest> list = book.series(series);
                for (int i = 0; i < list.size(); i++) {
                    Quest quest = list.get(i);
                    if (quest.templateId().equals(parts[2]) && !quest.claimed() && quest.progress() > 0) {
                        wasComplete = quest.complete();
                        book.set(series, i, quest.withProgress(quest.progress() - 1));
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            book.saveTo(player);
            if (wasComplete) {
                QuestService.markDirty(player);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Cibles
    // ---------------------------------------------------------------------------------------------

    private static boolean blockMatches(QuestTarget target, BlockState state) {
        if (target.tag()) {
            return state.is(TagKey.create(Registries.BLOCK, target.id()));
        }
        return target.id().equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }

    private static boolean entityMatches(QuestTarget target, EntityType<?> type) {
        if (target.tag()) {
            return type.is(TagKey.create(Registries.ENTITY_TYPE, target.id()));
        }
        return target.id().equals(ForgeRegistries.ENTITY_TYPES.getKey(type));
    }

    private static boolean itemMatches(QuestTarget target, ItemStack stack) {
        if (target.tag()) {
            return stack.is(TagKey.create(Registries.ITEM, target.id()));
        }
        return target.id().equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    // ---------------------------------------------------------------------------------------------
    // Casse, récolte, pose
    // ---------------------------------------------------------------------------------------------

    private static long gameTime(ServerPlayer player, ServerLevel level) {
        return level.getGameTime() + TIME_SHIFT.getOrDefault(player.getUUID(), 0L);
    }

    private static long undoWindowTicks() {
        return EconomyConfig.questPlaceUndoSeconds() * 20L;
    }

    /** {@code LOWEST}, sans les casses annulées. */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        boolean placed = PlacedBlocks.isPlaced(level, pos);
        PlacedBlocks.unmark(level, pos);
        if (!(event.getPlayer() instanceof ServerPlayer player) || !eligible(player)) {
            return;
        }
        // Posé il y a peu par ce joueur : la pose comptée est retirée, la casse ne compte pas (le bloc est marqué).
        long now = gameTime(player, level);
        for (Optional<String> key = RECENT.takeIfRecent(player.getUUID(), level.dimension(), pos, now,
                undoWindowTicks()); key.isPresent(); key = RECENT.takeIfRecent(player.getUUID(), level.dimension(),
                        pos, now, undoWindowTicks())) {
            undo(player, key.get());
        }
        if (!state.canHarvestBlock(level, pos, player)) {
            return;
        }
        if (!placed) {
            add(player, QuestCategory.BREAK, t -> t == null || blockMatches(t, state), 1);
        }
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            add(player, QuestCategory.HARVEST, t -> t == null || blockMatches(t, state), 1);
        }
    }

    /** {@code LOWEST}, sans les poses annulées. Marque toutes les positions, puis compte la pose d'un vrai joueur. */
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BlockPos> positions = new ArrayList<>();
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                positions.add(snapshot.getPos());
            }
        } else {
            positions.add(event.getPos());
        }
        positions.forEach(pos -> PlacedBlocks.mark(level, pos));
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)) {
            return;
        }
        BlockState state = event.getPlacedBlock();
        List<String> keys = advance(player, QuestCategory.PLACE, t -> t == null || blockMatches(t, state), 1);
        long now = gameTime(player, level);
        for (BlockPos pos : positions) {
            for (String key : keys) {
                RECENT.record(player.getUUID(), level.dimension(), pos, now, key);
            }
        }
    }

    /** {@code LOWEST} : un bloc formé par un fluide (générateur de pierre, de basalte) est marqué ; le feu, non. */
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !(event.getNewState().getBlock() instanceof BaseFireBlock)) {
            PlacedBlocks.mark(level, event.getPos());
        }
    }

    /** {@code LOWEST} : la destination de chaque bloc qu'un piston va pousser (ou tirer, s'il colle) est marquée. */
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getPistonMoveType() == PistonEvent.PistonMoveType.RETRACT
                && !level.getBlockState(event.getPos()).is(Blocks.STICKY_PISTON)) {
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        Direction push = resolver.getPushDirection();
        for (BlockPos pos : resolver.getToPush()) {
            PlacedBlocks.mark(level, pos.relative(push));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Combat, pêche, élevage
    // ---------------------------------------------------------------------------------------------

    /** {@code LOWEST}, même annulé : un autre mod qui annule les objets n'annule pas la mort. */
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || !(victim.level() instanceof ServerLevel)) {
            return;
        }
        ServerPlayer player = MobLoot.creditedPlayer(victim, event.getSource(), event.isRecentlyHit());
        if (player == null || !eligible(player) || !MobOrigin.isWild(victim) || MobLoot.isTamed(victim)) {
            return;
        }
        EntityType<?> type = victim.getType();
        add(player, QuestCategory.KILL,
                t -> t == null ? type.getCategory() == MobCategory.MONSTER : entityMatches(t, type), 1);
    }

    public static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)) {
            return;
        }
        for (ItemStack stack : event.getDrops()) {
            if (!stack.isEmpty()) {
                add(player, QuestCategory.FISH, t -> t == null || itemMatches(t, stack), stack.getCount());
            }
        }
    }

    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        Player cause = event.getCausedByPlayer();
        if (!(cause instanceof ServerPlayer player) || !eligible(player)) {
            return;
        }
        Entity parent = event.getParentA();
        EntityType<?> type = event.getChild() != null ? event.getChild().getType() : parent.getType();
        add(player, QuestCategory.BREED, t -> t == null ? parent instanceof Animal : entityMatches(t, type), 1);
    }

    // ---------------------------------------------------------------------------------------------
    // Crochets : cuisine, conserve, métier, livraison
    // ---------------------------------------------------------------------------------------------

    /** Ce que le joueur vient de sortir d'une machine de cuisson (le nombre est celui de la pile). */
    public static void cooked(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            add(player, QuestCategory.COOK, t -> t == null ? stack.isEdible() : itemMatches(t, stack),
                    stack.getCount());
        }
    }

    /** Ce que le joueur vient de sortir d'un séchoir ou d'un fumoir. */
    public static void preserved(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            add(player, QuestCategory.PRESERVE, t -> t == null ? stack.isEdible() : itemMatches(t, stack),
                    stack.getCount());
        }
    }

    /**
     * La case de sortie d'un four vient de rendre {@code count} objets au joueur : cuisine pour un four, conserve pour
     * un fumoir, rien pour un haut fourneau.
     */
    public static void furnaceOutput(Player player, Container container, ItemStack stack, int count) {
        if (count <= 0 || stack.isEmpty() || !(player instanceof ServerPlayer server)) {
            return;
        }
        if (container instanceof SmokerBlockEntity) {
            preserved(server, stack.copyWithCount(count));
        } else if (container instanceof FurnaceBlockEntity) {
            cooked(server, stack.copyWithCount(count));
        }
    }

    /** Un geste de métier ({@link QuestJobs}), {@code count} fois. */
    public static void job(ServerPlayer player, ResourceLocation action, int count) {
        add(player, QuestCategory.JOB, t -> t != null && !t.tag() && t.id().equals(action), count);
    }

    /** Une livraison au grenier communal ; sans appelant tant que {@link QuestCategory#DELIVER} n'est pas comptée. */
    public static void deliver(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            add(player, QuestCategory.DELIVER, t -> t == null || itemMatches(t, stack), stack.getCount());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Cycle de vie, essais
    // ---------------------------------------------------------------------------------------------

    public static void onServerStopped(ServerStoppedEvent event) {
        RECENT.clear();
        TIME_SHIFT.clear();
    }

    /** Essais seulement : l'horloge de jeu vue pour ce joueur avance de {@code ticks} ({@code 0} rend la vraie). */
    public static void setTimeShiftForTest(UUID player, long ticks) {
        if (ticks == 0L) {
            TIME_SHIFT.remove(player);
        } else {
            TIME_SHIFT.put(player, ticks);
        }
    }

    /** Essais seulement : poses retenues pour ce joueur. */
    public static int recentPlacementsForTest(UUID player) {
        return RECENT.size(player);
    }
}
