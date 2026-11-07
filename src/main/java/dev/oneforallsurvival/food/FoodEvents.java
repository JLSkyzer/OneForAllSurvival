package dev.oneforallsurvival.food;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * La péremption vécue : on date les aliments quand on les voit, on moyenne les dates quand deux piles se
 * rejoignent, on retire ce qui est avarié, et un aliment rassis nourrit moitié moins. Rien ne tourne en tâche de
 * fond : un joueur est observé une fois par seconde, un menu à son ouverture, un objet au sol à son apparition.
 */
public final class FoodEvents {

    /** Une passe par seconde et par joueur. */
    private static final int SCAN_PERIOD = 20;

    /** La faim d'un joueur au début d'un repas rassis, pour en reprendre la moitié à la fin. Fil serveur seulement. */
    private static final Map<UUID, Meal> MEALS = new HashMap<>();

    private record Meal(Item item, int food, float saturation) {}

    // ------------------------------------------------------------------ règles

    /**
     * Date l'aliment s'il ne l'est pas. Renvoie le déchet (même nombre) s'il est avarié, sinon la pile elle-même,
     * sans en changer l'instance. Une pile non périssable n'est jamais touchée.
     */
    public static ItemStack observe(ItemStack stack, long now) {
        if (!FoodStamp.isPerishable(stack)) {
            return stack;
        }
        FoodStamp.stampIfNeeded(stack, now);
        if (FoodStamp.state(stack, now) == DecayState.SPOILED) {
            return new ItemStack(FoodRegistry.SPOILED_FOOD.get(), stack.getCount());
        }
        return stack;
    }

    /**
     * Verse jusqu'à {@code max} objets de {@code from} dans {@code into}, deux piles datées du même objet, et donne
     * à {@code into} la moyenne pondérée des dates. Renvoie le nombre déplacé : rien n'est créé ni perdu.
     */
    public static int merge(ItemStack into, ItemStack from, int max) {
        if (!FoodStamp.isStamped(into) || !FoodStamp.isStamped(from)) {
            return 0;
        }
        int moved = Math.min(from.getCount(), max - into.getCount());
        if (moved <= 0) {
            return 0;
        }
        FoodStamp.setStamp(into, FoodDecay.weightedStamp(
                FoodStamp.stamp(into), into.getCount(), FoodStamp.stamp(from), moved));
        into.grow(moved);
        from.shrink(moved);
        return moved;
    }

    /**
     * Regroupe, parmi ces emplacements, les piles d'un même objet qui ne diffèrent que par leur date : chaque pile
     * se vide dans la première qui peut la recevoir, dans la limite de {@code slot.getMaxStackSize(stack)}.
     */
    public static void consolidate(List<Slot> slots) {
        List<Slot> food = null;
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (FoodStamp.isPerishable(stack) && FoodStamp.isStamped(stack)) {
                if (food == null) {
                    food = new ArrayList<>();
                }
                food.add(slot);
            }
        }
        if (food == null || food.size() < 2) {
            return;
        }
        for (int i = 0; i < food.size(); i++) {
            Slot target = food.get(i);
            ItemStack into = target.getItem().copy();
            int max = target.getMaxStackSize(into);
            boolean changed = false;
            for (int j = i + 1; j < food.size() && into.getCount() < max; j++) {
                Slot source = food.get(j);
                ItemStack from = source.getItem().copy();
                if (!mergeable(into, from) || !target.mayPlace(from)) {
                    continue;
                }
                if (merge(into, from, max) > 0) {
                    changed = true;
                    source.set(from.isEmpty() ? ItemStack.EMPTY : from);
                }
            }
            if (changed) {
                target.set(into);
            }
        }
    }

    /** Deux piles du même objet, datées, qui ne diffèrent que par la date. */
    private static boolean mergeable(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty() || !a.is(b.getItem()) || !FoodStamp.isPerishable(a)
                || !FoodStamp.isStamped(a) || !FoodStamp.isStamped(b) || FoodStamp.stamp(a) == FoodStamp.stamp(b)) {
            return false;
        }
        return a.areCapsCompatible(b) && Objects.equals(withoutStamp(a), withoutStamp(b));
    }

    /** Deux piles du même objet qui ne diffèrent que par la date (ou son absence). */
    static boolean sameWithoutStamp(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && a.is(b.getItem()) && a.areCapsCompatible(b)
                && Objects.equals(withoutStamp(a), withoutStamp(b));
    }

    private static CompoundTag withoutStamp(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return null;
        }
        CompoundTag copy = tag.copy();
        copy.remove(FoodStamp.KEY);
        return copy.isEmpty() ? null : copy;
    }

    /** Observe l'inventaire du joueur puis en regroupe les piles : une seule passe sur ses cases. */
    public static void scan(ServerPlayer player) {
        long now = FoodStamp.now(player.level());
        Inventory inventory = player.getInventory();
        // Ce qu'on est en train de manger reste en place jusqu'à la fin du repas, sinon le repas serait gratuit.
        ItemStack eating = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        int perishable = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == eating || !FoodStamp.isPerishable(stack)) {
                continue;
            }
            ItemStack seen = observe(stack, now);
            if (seen != stack) {
                inventory.setItem(i, seen);
            } else {
                perishable++;
            }
        }
        if (perishable >= 2) {
            List<Slot> slots = new ArrayList<>(Inventory.INVENTORY_SIZE + 1);
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                slots.add(new Slot(inventory, i, 0, 0));
            }
            slots.add(new Slot(inventory, Inventory.SLOT_OFFHAND, 0, 0));
            consolidate(slots);
        }
    }

    /**
     * Les menus de rangement dont la fermeture regroupe les piles : coffres (grands coffres compris) et boîtes de
     * shulker, plus l'armoire frigorifique, dont les 27 cases sont du rangement. Jamais un four, un alambic ni la
     * machine d'un mod, dont les cases ont un rôle. Point d'extension unique de ce regroupement.
     */
    private static boolean groupsOnClose(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof FridgeMenu;
    }

    private static long now(Level level) {
        return FoodStamp.now(level);
    }

    // ------------------------------------------------------------- abonnements

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer() && event.player instanceof ServerPlayer player
                && player.tickCount % SCAN_PERIOD == 0) {
            scan(player);
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = now(player.level());
        for (Slot slot : event.getContainer().slots) {
            ItemStack stack = slot.getItem();
            if (!FoodStamp.isPerishable(stack)) {
                continue;
            }
            if (slot.getClass() != Slot.class) {
                // Case fantôme ou de filtre d'un mod : on la date, mais slot.set ignore mayPlace, donc jamais de déchet.
                FoodStamp.stampIfNeeded(stack, now);
                continue;
            }
            ItemStack seen = observe(stack, now);
            if (seen != stack) {
                slot.set(seen);
            }
        }
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !groupsOnClose(event.getContainer())) {
            return;
        }
        List<Slot> storage = new ArrayList<>();
        for (Slot slot : event.getContainer().slots) {
            // L'inventaire du joueur a sa propre passe, une fois par seconde.
            if (slot.container != player.getInventory()) {
                storage.add(slot);
            }
        }
        consolidate(storage);
    }

    @SubscribeEvent
    public void onItemEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity entity) {
            FoodStamp.stampIfNeeded(entity.getItem(), now(event.getLevel()));
        }
    }

    /**
     * Une pile portée posée sur une pile du même objet à date différente : on les fond avec la moyenne des dates.
     * Pile pleine, ou objets différents : on ne touche à rien, le comportement vanilla s'applique.
     */
    @SubscribeEvent
    public void onItemStackedOn(ItemStackedOnOtherEvent event) {
        ItemStack carried = event.getCarriedSlotAccess().get();
        Slot slot = event.getSlot();
        ItemStack clicked = slot.getItem();
        if (carried.isEmpty() || clicked.isEmpty() || !carried.is(clicked.getItem())
                || !FoodStamp.isPerishable(carried)) {
            return;
        }
        long now = now(event.getPlayer().level());
        FoodStamp.stampIfNeeded(carried, now);
        FoodStamp.stampIfNeeded(clicked, now);
        if (FoodStamp.state(carried, now) == DecayState.SPOILED || FoodStamp.state(clicked, now) == DecayState.SPOILED
                || !mergeable(clicked, carried) || !slot.mayPlace(carried)) {
            return;
        }
        ItemStack into = clicked.copy();
        ItemStack from = carried.copy();
        int max = slot.getMaxStackSize(into);
        int moved;
        if (event.getClickAction() == ClickAction.SECONDARY) {
            ItemStack one = from.copy();
            one.setCount(1);
            moved = merge(into, one, max);
            from.shrink(moved);
        } else {
            moved = merge(into, from, max);
        }
        if (moved == 0) {
            return;
        }
        slot.set(into);
        event.getCarriedSlotAccess().set(from.isEmpty() ? ItemStack.EMPTY : from);
        event.setCanceled(true);
    }

    /** Un aliment avarié ne se mange pas : la consommation est annulée et le déchet prend sa place. */
    @SubscribeEvent
    public void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getItem();
        if (!FoodStamp.isPerishable(item)) {
            return;
        }
        long now = now(player.level());
        FoodStamp.stampIfNeeded(item, now);
        DecayState state = FoodStamp.state(item, now);
        if (state == DecayState.SPOILED) {
            event.setCanceled(true);
            if (!player.level().isClientSide) {
                InteractionHand hand = player.getMainHandItem() == item ? InteractionHand.MAIN_HAND
                        : player.getOffhandItem() == item ? InteractionHand.OFF_HAND : null;
                if (hand != null) {
                    player.setItemInHand(hand, new ItemStack(FoodRegistry.SPOILED_FOOD.get(), item.getCount()));
                }
            }
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        if (state == DecayState.STALE) {
            FoodData food = player.getFoodData();
            MEALS.put(player.getUUID(), new Meal(item.getItem(), food.getFoodLevel(), food.getSaturationLevel()));
        } else {
            MEALS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onUseStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            MEALS.remove(player.getUUID());
        }
    }

    /**
     * Un repas rassis reprend la moitié de ce qu'il a donné, d'après la faim notée au début, et ne fait jamais
     * passer sous cette faim. L'état noté au début compte : un aliment qui s'avarie en route reste un repas rassis.
     */
    @SubscribeEvent
    public void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        Meal meal = MEALS.remove(player.getUUID());
        if (meal == null || meal.item() != event.getItem().getItem()) {
            return;
        }
        FoodData food = player.getFoodData();
        int gained = food.getFoodLevel() - meal.food();
        if (gained > 0) {
            food.setFoodLevel(food.getFoodLevel() - gained / 2);
        }
        float satGained = food.getSaturationLevel() - meal.saturation();
        if (satGained > 0) {
            float kept = food.getSaturationLevel() - satGained / 2;
            food.setSaturation(Math.max(meal.saturation(), Math.min(kept, food.getFoodLevel())));
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MEALS.remove(event.getEntity().getUUID());
    }
}
