package dev.oneforallsurvival.bench;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Une fabrication commandée. Les piles payées gardent leur NBT et leur usure, pour être rendues telles
 * quelles à l'annulation. Seule la progression change ; le reste est fixé à la commande.
 */
public final class WorkshopEntry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final UUID id;
    private final ResourceLocation recipeId;
    private final ItemStack result;
    private final List<ItemStack> paid;
    private final List<ItemStack> remainders;
    private final int totalTicks;
    private double doneTicks;
    private final ResourceKey<Level> benchLevel;
    private final BlockPos benchPos;
    private boolean lastKnownPowered;

    public WorkshopEntry(UUID id, ResourceLocation recipeId, ItemStack result, List<ItemStack> paid,
            List<ItemStack> remainders, int totalTicks, double doneTicks, ResourceKey<Level> benchLevel,
            BlockPos benchPos, boolean lastKnownPowered) {
        this.id = id;
        this.recipeId = recipeId;
        this.result = result.copy();
        this.paid = copyAll(paid);
        this.remainders = copyAll(remainders);
        this.totalTicks = totalTicks;
        this.doneTicks = doneTicks;
        this.benchLevel = benchLevel;
        this.benchPos = benchPos.immutable();
        this.lastKnownPowered = lastKnownPowered;
    }

    public UUID id() { return id; }
    public ResourceLocation recipeId() { return recipeId; }
    public ItemStack result() { return result; }
    public List<ItemStack> paid() { return paid; }
    public List<ItemStack> remainders() { return remainders; }
    public int totalTicks() { return totalTicks; }
    public double doneTicks() { return doneTicks; }
    public ResourceKey<Level> benchLevel() { return benchLevel; }
    public BlockPos benchPos() { return benchPos; }
    public boolean lastKnownPowered() { return lastKnownPowered; }

    void setDoneTicks(double doneTicks) {
        this.doneTicks = doneTicks;
    }

    /** Dernier état connu de l'établi ; écrit par le ticker, sauvegardé avec la progression. */
    void setLastKnownPowered(boolean lastKnownPowered) {
        this.lastKnownPowered = lastKnownPowered;
    }

    public boolean isDone() {
        return doneTicks >= totalTicks;
    }

    private static List<ItemStack> copyAll(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>(stacks.size());
        for (ItemStack s : stacks) {
            out.add(s.copy());
        }
        return out;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("recipe", recipeId.toString());
        tag.put("result", result.save(new CompoundTag()));
        tag.put("paid", saveStacks(paid));
        tag.put("remainders", saveStacks(remainders));
        tag.putInt("total", totalTicks);
        tag.putDouble("done", doneTicks);
        tag.putString("level", benchLevel.location().toString());
        tag.put("pos", NbtUtils.writeBlockPos(benchPos));
        tag.putBoolean("powered", lastKnownPowered);
        return tag;
    }

    /**
     * Relit une entrée ; {@code null} (avec un avertissement) si elle ne peut pas être relancée. Quand elle est
     * abandonnée, les piles payées encore lisibles sont confiées à {@code salvage} (le stock du joueur) :
     * ce que le joueur a payé ne disparaît pas avec un mod retiré. Une pile illisible est sautée et
     * journalisée, sans perdre les autres.
     */
    public static WorkshopEntry load(CompoundTag tag, Consumer<ItemStack> salvage) {
        String label = tag.hasUUID("id") ? tag.getUUID("id").toString() : "(sans identifiant)";
        if (!tag.hasUUID("id")) {
            LOGGER.warn("Fabrication ignorée, identifiant absent ou invalide");
            salvagePaid(tag, label, salvage);
            return null;
        }
        UUID id = tag.getUUID("id");
        ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("recipe"));
        ResourceLocation level = ResourceLocation.tryParse(tag.getString("level"));
        if (recipe == null || level == null || !tag.contains("result", Tag.TAG_COMPOUND)
                || !tag.contains("pos", Tag.TAG_COMPOUND)
                || !tag.contains("total", Tag.TAG_INT)) {
            LOGGER.warn("Fabrication {} ignorée, champs absents ou invalides", id);
            salvagePaid(tag, label, salvage);
            return null;
        }
        ItemStack result = ItemStack.of(tag.getCompound("result"));
        if (result.isEmpty()) {
            LOGGER.warn("Fabrication {} ignorée, résultat inconnu (mod retiré ?) : {}", id,
                    tag.getCompound("result").getString("id"));
            salvagePaid(tag, label, salvage);
            return null;
        }
        List<ItemStack> paid = loadStacks(tag.getList("paid", Tag.TAG_COMPOUND), "payée", label);
        List<ItemStack> remainders = loadStacks(tag.getList("remainders", Tag.TAG_COMPOUND), "de reste", label);
        return new WorkshopEntry(id, recipe, result, paid, remainders, tag.getInt("total"), tag.getDouble("done"),
                ResourceKey.create(Registries.DIMENSION, level), NbtUtils.readBlockPos(tag.getCompound("pos")),
                tag.getBoolean("powered"));
    }

    public static ListTag saveStacks(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack s : stacks) {
            list.add(s.save(new CompoundTag()));
        }
        return list;
    }

    /** Les piles lisibles de {@code list} ; une pile illisible (objet disparu) est sautée et journalisée. */
    private static List<ItemStack> loadStacks(ListTag list, String kind, String entry) {
        List<ItemStack> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            ItemStack s = ItemStack.of(raw);
            if (s.isEmpty()) {
                LOGGER.warn("Fabrication {} : pile {} perdue, objet inconnu {} x{} (mod retiré ?)", entry, kind,
                        raw.getString("id"), raw.getInt("Count"));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    /** Remet au stock les piles payées lisibles d'une entrée abandonnée, et journalise ce qui est perdu. */
    private static void salvagePaid(CompoundTag tag, String entry, Consumer<ItemStack> salvage) {
        List<ItemStack> paid = loadStacks(tag.getList("paid", Tag.TAG_COMPOUND), "payée", entry);
        for (ItemStack s : paid) {
            salvage.accept(s);
        }
        if (!paid.isEmpty()) {
            LOGGER.warn("Fabrication {} abandonnée : {} pile(s) payée(s) remise(s) au stock du joueur", entry,
                    paid.size());
        }
    }
}
