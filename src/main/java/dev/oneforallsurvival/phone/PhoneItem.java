package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.machine.ItemBattery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
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
 */
public class PhoneItem extends Item {

    public static final String TAG_ID = "phone_id";
    private static final int BAR_COLOR = 0x6FB1E8;

    public PhoneItem(Properties properties) {
        super(properties);
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
    }
}
