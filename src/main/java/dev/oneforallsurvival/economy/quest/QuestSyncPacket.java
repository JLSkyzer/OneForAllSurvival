package dev.oneforallsurvival.economy.quest;

import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyConfig;
import io.netty.handler.codec.DecoderException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur vers client : la vue des quêtes d'un joueur. Pour chaque quête (journalières, hebdomadaires, événements) :
 * sa référence (renvoyée telle quelle dans {@link QuestActionPacket}), son titre (composant traduit chez le client ;
 * un titre d'événement est un texte libre, en {@code Component.literal}), son icône, sa progression, son but, sa
 * récompense, ce qui en serait payé maintenant (plafond) et son état. Puis le plafond payé et le plafond, les relances
 * restantes du jour, les délais jusqu'aux prochains renouvellements (en millisecondes, relatifs : l'horloge du client
 * n'a pas à être celle du serveur) et le nombre de récompenses en attente.
 *
 * <p>Taille bornée : au plus {@value #MAX_QUESTS} quêtes, titres de {@value #MAX_TITLE} caractères.
 */
public record QuestSyncPacket(long capPaid, long cap, int rerollsLeft, long dailyResetIn, long weeklyResetIn,
        int pendingCount, List<Entry> quests) {

    public static final int MAX_QUESTS = 64;
    public static final int MAX_TITLE = 64;
    static final String TARGET_ANY = "quest.oneforallsurvival.target.any.";

    public enum State { IN_PROGRESS, COMPLETE, CLAIMED }

    public record Entry(QuestRef ref, Component title, ItemStack icon, int progress, int goal, long reward,
            long payable, State state) {}

    public QuestSyncPacket {
        quests = List.copyOf(quests);
    }

    // ---------------------------------------------------------------------------------------------
    // Construction (serveur)
    // ---------------------------------------------------------------------------------------------

    /** L'état du carnet de ce joueur à cet instant (le carnet n'est pas renouvelé ici). */
    public static QuestSyncPacket of(ServerPlayer player, long now) {
        QuestBook book = QuestBook.of(player);
        LocalDate today = QuestService.today(now);
        LocalDate week = QuestService.week(now);
        long cap = EconomyConfig.questWeeklyCap();
        long capLeft = book.capRemaining(week, cap);
        List<Entry> quests = new ArrayList<>();
        for (int i = 0; i < book.daily().size() && quests.size() < MAX_QUESTS; i++) {
            quests.add(entry(book.ref(QuestRef.Kind.DAILY, i), book.daily().get(i), false, capLeft));
        }
        for (int i = 0; i < book.weekly().size() && quests.size() < MAX_QUESTS; i++) {
            quests.add(entry(book.ref(QuestRef.Kind.WEEKLY, i), book.weekly().get(i), false, capLeft));
        }
        for (Map.Entry<String, Quest> event : book.events().entrySet()) {
            if (quests.size() >= MAX_QUESTS) {
                break;
            }
            quests.add(entry(book.eventRef(event.getKey()), event.getValue(), true, capLeft));
        }
        int rerollsLeft = Math.max(0, EconomyConfig.questRerollsPerDay() - book.rerollsUsedOn(today));
        int hour = EconomyConfig.questResetHour();
        long daily = QuestPeriods.nextDailyReset(now, EconomyCalendar.configuredZone(), hour) - now;
        long weekly = QuestPeriods.nextWeeklyReset(now, EconomyCalendar.configuredZone(), hour) - now;
        return new QuestSyncPacket(cap - capLeft, cap, rerollsLeft, Math.max(0L, daily), Math.max(0L, weekly),
                book.pending().size(), quests);
    }

    private static Entry entry(QuestRef ref, Quest quest, boolean event, long capLeft) {
        State state = quest.claimed() ? State.CLAIMED : quest.complete() ? State.COMPLETE : State.IN_PROGRESS;
        long payable = state == State.COMPLETE ? Math.min(quest.reward(), Math.max(0L, capLeft)) : 0L;
        return new Entry(ref, title(quest, event), icon(quest), quest.progress(), quest.goal(), quest.reward(), payable,
                state);
    }

    /**
     * Le titre : texte libre de l'équipe pour un événement (jamais une clé de langue) ; clé du modèle sinon, avec la
     * quantité ; à défaut, la catégorie, la quantité et la cible.
     */
    static Component title(Quest quest, boolean event) {
        if (quest.titleKey() != null && !quest.titleKey().isBlank()) {
            if (event) {
                String text = quest.titleKey();
                return Component.literal(text.length() > MAX_TITLE ? text.substring(0, MAX_TITLE) : text);
            }
            return Component.translatable(quest.titleKey(), quest.goal());
        }
        return Component.translatable("quest.oneforallsurvival.category." + quest.category().key(), quest.goal(),
                targetName(quest));
    }

    private static Component targetName(Quest quest) {
        QuestTarget target = quest.target();
        if (target == null) {
            return Component.translatable(TARGET_ANY + quest.category().key());
        }
        if (target.tag()) {
            return Component.literal("#" + target.id());
        }
        return switch (quest.category().targetKind()) {
            case BLOCK -> BuiltInRegistries.BLOCK.getOptional(target.id()).map(Block::getName)
                    .orElse(Component.literal(target.id().toString()));
            case ITEM -> BuiltInRegistries.ITEM.getOptional(target.id()).map(Item::getDescription)
                    .orElse(Component.literal(target.id().toString()));
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(target.id()).map(EntityType::getDescription)
                    .orElse(Component.literal(target.id().toString()));
            case ACTION -> Component.literal(target.id().getPath());
        };
    }

    /** L'icône de la cible : l'objet du bloc, l'objet, l'œuf d'apparition, le premier du tag ; sinon celle du geste. */
    static ItemStack icon(Quest quest) {
        QuestTarget target = quest.target();
        Item item = null;
        if (target != null) {
            item = switch (quest.category().targetKind()) {
                case BLOCK -> target.tag()
                        ? first(BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, target.id())))
                                .map(Block::asItem).orElse(null)
                        : BuiltInRegistries.BLOCK.getOptional(target.id()).map(Block::asItem).orElse(null);
                case ITEM -> target.tag()
                        ? first(BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, target.id()))).orElse(null)
                        : BuiltInRegistries.ITEM.getOptional(target.id()).orElse(null);
                case ENTITY -> target.tag() ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(target.id())
                        .map(SpawnEggItem::byId).map(Item.class::cast).orElse(null);
                case ACTION -> null;
            };
        }
        if (item == null || item == Items.AIR) {
            item = switch (quest.category()) {
                case BREAK -> Items.IRON_PICKAXE;
                case PLACE -> Items.BRICKS;
                case KILL -> Items.IRON_SWORD;
                case HARVEST -> Items.WHEAT;
                case FISH -> Items.FISHING_ROD;
                case COOK -> Items.COOKED_BEEF;
                case PRESERVE -> Items.SMOKER;
                case BREED -> Items.EGG;
                case JOB -> Items.ANVIL;
                case DELIVER -> Items.CHEST;
            };
        }
        return new ItemStack(item);
    }

    private static <T> Optional<T> first(Optional<? extends Iterable<Holder<T>>> tag) {
        if (tag.isEmpty()) {
            return Optional.empty();
        }
        for (Holder<T> holder : tag.get()) {
            return Optional.of(holder.value());
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------------------------------------
    // Réseau
    // ---------------------------------------------------------------------------------------------

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(capPaid);
        buf.writeVarLong(cap);
        buf.writeVarInt(rerollsLeft);
        buf.writeVarLong(dailyResetIn);
        buf.writeVarLong(weeklyResetIn);
        buf.writeVarInt(pendingCount);
        int n = Math.min(MAX_QUESTS, quests.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = quests.get(i);
            QuestActionPacket.writeRef(buf, e.ref());
            buf.writeComponent(e.title());
            buf.writeItem(e.icon());
            buf.writeVarInt(e.progress());
            buf.writeVarInt(e.goal());
            buf.writeVarLong(e.reward());
            buf.writeVarLong(e.payable());
            buf.writeByte(e.state().ordinal());
        }
    }

    public static QuestSyncPacket decode(FriendlyByteBuf buf) {
        long capPaid = buf.readVarLong();
        long cap = buf.readVarLong();
        int rerollsLeft = buf.readVarInt();
        long daily = buf.readVarLong();
        long weekly = buf.readVarLong();
        int pending = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_QUESTS) {
            throw new DecoderException("quêtes : " + n + " au lieu de " + MAX_QUESTS + " au plus");
        }
        List<Entry> quests = new ArrayList<>(n);
        State[] states = State.values();
        for (int i = 0; i < n; i++) {
            QuestRef ref = QuestActionPacket.readRef(buf);
            Component title = buf.readComponent();
            ItemStack icon = buf.readItem();
            int progress = buf.readVarInt();
            int goal = buf.readVarInt();
            long reward = buf.readVarLong();
            long payable = buf.readVarLong();
            int state = buf.readByte();
            if (ref == null || state < 0 || state >= states.length) {
                throw new DecoderException("quête mal formée");
            }
            quests.add(new Entry(ref, title, icon, progress, goal, reward, payable, states[state]));
        }
        return new QuestSyncPacket(capPaid, cap, rerollsLeft, daily, weekly, pending, quests);
    }

    public static void handle(QuestSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.oneforallsurvival.economy.quest.client.QuestClientCache.accept(packet));
    }
}
