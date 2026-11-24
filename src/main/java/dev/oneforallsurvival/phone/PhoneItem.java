package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.machine.ItemBattery;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.SimCardItem;
import dev.oneforallsurvival.phone.net.SimNumbers;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Le téléphone : un objet à batterie qui porte seulement un identifiant ({@code phone_id}). Tout le reste (code,
 * propriétaire, réglages) vit dans {@link PhoneData}, côté serveur.
 *
 * <p>Deux piles de même {@code phone_id} (copie en créatif, {@code /give} avec NBT) partagent leurs données : c'est un
 * choix accepté, sans contournement du code. Le service vérifie le propriétaire côté serveur à chaque ouverture, et
 * cette vérification ne doit jamais être relâchée.
 *
 * <p>Lot T2 : la carte SIM logée vit dans le NBT {@value #TAG_SIM} du téléphone ; son jeton n'est jamais envoyé au
 * client ({@link #getShareTag}). Deux copies d'un téléphone avec sa carte partagent sa ligne, comme le reste.
 */
public class PhoneItem extends Item {

    public static final String TAG_ID = "phone_id";
    /** La carte SIM logée : la pile entière, telle que {@link ItemStack#save} l'écrit (lot T2). */
    public static final String TAG_SIM = "sim";
    private static final int BAR_COLOR = 0x6FB1E8;

    public PhoneItem(Properties properties) {
        super(properties);
    }

    /**
     * La carte SIM logée dans ce téléphone (une copie, une seule), ou une pile vide. Seule une carte SIM est rendue : un
     * NBT {@value #TAG_SIM} forgé qui porte autre chose (64 diamants, un autre téléphone) ne rend rien.
     */
    public static ItemStack sim(ItemStack phone) {
        if (phone.hasTag() && phone.getTag().contains(TAG_SIM, Tag.TAG_COMPOUND)) {
            ItemStack lodged = ItemStack.of(phone.getTag().getCompound(TAG_SIM));
            if (lodged.getItem() instanceof SimCardItem) {
                return lodged.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Loge cette carte (une copie, une seule) dans le téléphone, ou l'en retire si la pile est vide. Une pile qui n'est
     * pas une carte SIM n'est jamais logée (rien ne change). Serveur seulement : l'authenticité de la carte, c'est
     * {@link PhoneSims} qui en décide.
     */
    public static void setSim(ItemStack phone, ItemStack sim) {
        if (!sim.isEmpty() && !(sim.getItem() instanceof SimCardItem)) {
            return;
        }
        if (sim.isEmpty()) {
            if (phone.hasTag()) {
                phone.getTag().remove(TAG_SIM);
            }
            return;
        }
        phone.getOrCreateTag().put(TAG_SIM, sim.copyWithCount(1).save(new CompoundTag()));
    }

    /** Ce que le client reçoit : tout, sauf le jeton de la carte logée. */
    @Override
    @Nullable
    public CompoundTag getShareTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SIM, Tag.TAG_COMPOUND)) {
            return tag;
        }
        CompoundTag copy = tag.copy();
        CompoundTag simTag = copy.getCompound(TAG_SIM).getCompound("tag");
        simTag.remove(SimCardItem.TAG_TOKEN);
        return copy;
    }

    private static int capacity() {
        return PhoneConfig.capacity();
    }

    /** L'identifiant de ce téléphone, s'il en a déjà reçu un. */
    public static Optional<UUID> phoneId(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().hasUUID(TAG_ID)) {
            return Optional.of(stack.getTag().getUUID(TAG_ID));
        }
        return Optional.empty();
    }

    /** L'identifiant de ce téléphone ; lui en donne un au premier appel. Serveur seulement. */
    public static UUID ensurePhoneId(ItemStack stack) {
        Optional<UUID> known = phoneId(stack);
        if (known.isPresent()) {
            return known.get();
        }
        UUID id = UUID.randomUUID();
        stack.getOrCreateTag().putUUID(TAG_ID, id);
        return id;
    }

    public static int stored(ItemStack stack) {
        return ItemBattery.stored(stack, capacity());
    }

    public static void store(ItemStack stack, int fe) {
        ItemBattery.store(stack, fe, capacity());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PhoneService.open(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ItemBattery.Provider(stack, PhoneItem::capacity);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return ItemBattery.reequip(oldStack, newStack, slotChanged);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return ItemBattery.barWidth(stack, capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.phone.energy", stored(stack), capacity()));
        ItemStack sim = sim(stack);
        if (sim.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.oneforallsurvival.phone.no_sim"));
        } else {
            CompoundTag simTag = sim.getTag();
            String number = simTag == null ? "" : simTag.getString(SimCardItem.TAG_NUMBER);
            tooltip.add(Component.translatable("tooltip.oneforallsurvival.phone.sim", SimNumbers.format(number)));
        }
    }
}
