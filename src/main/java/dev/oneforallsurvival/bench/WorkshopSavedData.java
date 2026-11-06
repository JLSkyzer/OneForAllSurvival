package dev.oneforallsurvival.bench;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/** Les ateliers de tous les joueurs, sauvegardés dans le monde principal. */
public final class WorkshopSavedData extends SavedData {

    public static final String KEY = "oneforallsurvival_workshops";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, PlayerWorkshop> workshops = new LinkedHashMap<>();
    /** Les ateliers dont la file n'est pas vide : seuls ceux-là sont visités à chaque tick. */
    private final Set<PlayerWorkshop> active = new LinkedHashSet<>();
    /**
     * Combien de têtes de file sont rattachées à chaque établi. Un établi demande ainsi en un accès s'il a
     * du travail, sans parcourir les ateliers ; seule la tête de file compte, car seule elle avance.
     */
    private final Map<BenchSite, Integer> heads = new HashMap<>();

    /** Un établi : sa dimension et sa position. */
    record BenchSite(ResourceKey<Level> level, BlockPos pos) {}

    /** Instance autonome (essais) ; en jeu, passer par {@link #get(MinecraftServer)}. */
    public WorkshopSavedData() {}

    public static WorkshopSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorkshopSavedData::load,
                WorkshopSavedData::new, KEY);
    }

    public PlayerWorkshop of(UUID player) {
        return workshops.computeIfAbsent(player, p -> new PlayerWorkshop(this));
    }

    /** Copie des ateliers actifs, car un craft terminé change l'ensemble pendant le parcours. */
    public Collection<PlayerWorkshop> activeWorkshops() {
        return active.isEmpty() ? List.of() : new ArrayList<>(active);
    }

    /** Vrai si la tête de file d'un atelier est rattachée à cet établi. */
    public boolean hasHeadAt(ResourceKey<Level> level, BlockPos pos) {
        return heads.containsKey(new BenchSite(level, pos));
    }

    void queueChanged(PlayerWorkshop workshop) {
        BenchSite now = null;
        if (workshop.isActive()) {
            active.add(workshop);
            WorkshopEntry head = workshop.queue().get(0);
            now = new BenchSite(head.benchLevel(), head.benchPos());
        } else {
            active.remove(workshop);
        }
        BenchSite before = workshop.headSite();
        if (!Objects.equals(before, now)) {
            if (before != null) {
                heads.computeIfPresent(before, (site, n) -> n > 1 ? n - 1 : null);
            }
            if (now != null) {
                heads.merge(now, 1, Integer::sum);
            }
            workshop.setHeadSite(now);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerWorkshop> e : workshops.entrySet()) {
            PlayerWorkshop w = e.getValue();
            if (w.isEmptyWorkshop()) {
                continue;
            }
            CompoundTag t = new CompoundTag();
            t.putUUID("player", e.getKey());
            ListTag queue = new ListTag();
            for (WorkshopEntry entry : w.queue()) {
                queue.add(entry.save());
            }
            t.put("queue", queue);
            t.put("stock", WorkshopEntry.saveStacks(w.stock()));
            list.add(t);
        }
        tag.put("workshops", list);
        return tag;
    }

    /** Relit les ateliers ; une entrée corrompue est ignorée avec un avertissement, jamais fatale. */
    public static WorkshopSavedData load(CompoundTag tag) {
        WorkshopSavedData data = new WorkshopSavedData();
        ListTag list = tag.getList("workshops", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("player")) {
                LOGGER.warn("Atelier ignoré, identifiant de joueur absent ou invalide");
                continue;
            }
            UUID player = t.getUUID("player");
            PlayerWorkshop w = data.of(player);
            ListTag queue = t.getList("queue", Tag.TAG_COMPOUND);
            for (int q = 0; q < queue.size(); q++) {
                try {
                    WorkshopEntry entry = WorkshopEntry.load(queue.getCompound(q));
                    if (entry != null) {
                        w.restoreEntry(entry);
                    }
                } catch (RuntimeException ex) {
                    LOGGER.warn("Fabrication illisible de {} ignorée : {}", player, ex.toString());
                }
            }
            ListTag stock = t.getList("stock", Tag.TAG_COMPOUND);
            for (int s = 0; s < stock.size(); s++) {
                try {
                    ItemStack stack = ItemStack.of(stock.getCompound(s));
                    if (stack.isEmpty()) {
                        LOGGER.warn("Pile du stock de {} ignorée, objet inconnu (mod retiré ?)", player);
                    } else {
                        w.restoreStock(stack);
                    }
                } catch (RuntimeException ex) {
                    LOGGER.warn("Pile illisible du stock de {} ignorée : {}", player, ex.toString());
                }
            }
            data.queueChanged(w);
        }
        return data;
    }
}
