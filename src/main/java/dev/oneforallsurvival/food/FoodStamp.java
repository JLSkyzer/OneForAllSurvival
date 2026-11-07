package dev.oneforallsurvival.food;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** La date de péremption posée sur une pile, en NBT. L'état se calcule à la lecture, jamais en tâche de fond. */
public final class FoodStamp {

    public static final String KEY = "oneforallsurvival_stamp";

    private FoodStamp() {}

    /** Vrai si l'objet périme : le tag non périssable l'emporte, puis le tag normal ou le tag lent. */
    public static boolean isPerishable(ItemStack stack) {
        if (stack.isEmpty() || stack.is(FoodTags.NON_PERISHABLE)) {
            return false;
        }
        return stack.is(FoodTags.PERISHABLE) || stack.is(FoodTags.SLOW_PERISHABLE);
    }

    public static boolean isStamped(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEY, Tag.TAG_LONG);
    }

    /** La date de la pile, ou 0 si elle n'en a pas : tester {@link #isStamped} avant. */
    public static long stamp(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEY, Tag.TAG_LONG) ? tag.getLong(KEY) : 0L;
    }

    public static void setStamp(ItemStack stack, long stamp) {
        stack.getOrCreateTag().putLong(KEY, stamp);
    }

    /** Date un périssable qui n'en a pas, au quart d'heure inférieur. Renvoie vrai si la pile a changé. */
    public static boolean stampIfNeeded(ItemStack stack, long now) {
        if (!isPerishable(stack) || isStamped(stack)) {
            return false;
        }
        setStamp(stack, FoodDecay.roundDown(now));
        return true;
    }

    /** L'état de la pile : frais sans date ; durées multipliées par le facteur lent pour le tag lent. */
    public static DecayState state(ItemStack stack, long now) {
        if (!isStamped(stack)) {
            return DecayState.FRESH;
        }
        long fresh = FoodConfig.freshTicks();
        long stale = FoodConfig.staleTicks();
        if (stack.is(FoodTags.SLOW_PERISHABLE)) {
            double factor = FoodConfig.slowFactor();
            fresh = scale(fresh, factor);
            stale = scale(stale, factor);
        }
        return FoodDecay.stateAt(stamp(stack), now, fresh, stale);
    }

    public static long now(Level level) {
        return level.getGameTime();
    }

    private static long scale(long ticks, double factor) {
        double scaled = ticks * factor;
        return scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(scaled);
    }
}
