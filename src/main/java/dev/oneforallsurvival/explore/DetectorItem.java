package dev.oneforallsurvival.explore;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.ItemBattery;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Le détecteur de métaux : une batterie, et un bip dont la cadence et la hauteur disent la distance à la cache
 * enfouie la plus proche. Il n'agit que côté serveur, tenu dans une main, chargé, dans une dimension qui a des
 * caches ; seul le porteur entend le bip. Un clic droit sur un bloc à FE le recharge. Sa barre montre la charge.
 */
public class DetectorItem extends Item {

    private static final int BAR_COLOR = 0x6FB7D8;
    /** Le compteur de ticks depuis le dernier bip, dans le NBT de la pile : il suit l'objet d'une main à l'autre. */
    private static final String TAG_COUNTER = "beepTicks";

    /** Ce qu'un tick de détecteur a fait. */
    public enum Tick {
        /** Rien : pas chargé, ou dimension sans cache. Aucune charge dépensée. */
        IDLE,
        /** La charge a été dépensée, sans bip (aucune cache à portée, ou bip pas encore dû). */
        SEARCHING,
        /** La charge a été dépensée et le bip joué au porteur. */
        BEEPED
    }

    public DetectorItem(Properties properties) {
        super(properties);
    }

    private static int capacity() {
        return ExploreConfig.detectorCapacity();
    }

    /** FE contenus dans la batterie de cette pile. */
    public static int stored(ItemStack stack) {
        return ItemBattery.stored(stack, capacity());
    }

    /** Fixe la charge, bornée à la capacité. */
    public static void store(ItemStack stack, int fe) {
        ItemBattery.store(stack, fe, capacity());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player
                && (player.getMainHandItem() == stack || player.getOffhandItem() == stack)) {
            tick(serverLevel, player, stack);
        }
    }

    /**
     * Un tick de détecteur tenu en main. Sans charge ou sans cache dans la dimension, il se tait et ne dépense rien.
     * Une entrée d'index sans bloc derrière (cache retirée sans passer par le bloc) est purgée, puis la recherche
     * reprend dans le même tick.
     */
    public static Tick tick(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int cost = ExploreConfig.detectorEnergy();
        if (stored(stack) < cost) {
            return Tick.IDLE;
        }
        CacheIndex index = CacheIndex.get(level);
        if (index.size() == 0) {
            return Tick.IDLE;
        }
        ItemBattery.spend(stack, cost, capacity());
        double range = ExploreConfig.detectorRange();
        Optional<BlockPos> found;
        while ((found = index.nearest(player.blockPosition(), range)).isPresent()) {
            BlockPos pos = found.get();
            // Une zone déchargée n'est pas inspectée (jamais de chargement forcé) : on la croit encore bonne.
            if (!level.hasChunkAt(pos) || level.getBlockState(pos).is(ExploreRegistry.CACHE.get())) {
                break;
            }
            index.remove(pos);
        }
        if (found.isEmpty()) {
            setCounter(stack, 0);
            return Tick.SEARCHING;
        }
        double distance = player.position().distanceTo(Vec3.atCenterOf(found.get()));
        int interval = DetectorRules.beepInterval(distance, range);
        if (interval < 0) {
            setCounter(stack, 0);
            return Tick.SEARCHING;
        }
        int counter = counter(stack) + 1;
        if (counter < interval) {
            setCounter(stack, counter);
            return Tick.SEARCHING;
        }
        setCounter(stack, 0);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0F,
                DetectorRules.pitch(distance, range));
        return Tick.BEEPED;
    }

    private static int counter(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(TAG_COUNTER) : 0;
    }

    private static void setCounter(ItemStack stack, int value) {
        if (value <= 0) {
            if (stack.hasTag()) {
                stack.getTag().remove(TAG_COUNTER);
            }
        } else {
            stack.getOrCreateTag().putInt(TAG_COUNTER, value);
        }
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockEntity target = level.getBlockEntity(context.getClickedPos());
        if (target == null || context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof ElectricMachineBlockEntity)
                && !target.getCapability(ForgeCapabilities.ENERGY, context.getClickedFace()).isPresent()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            int received = ItemBattery.chargeFrom(stack, target, context.getClickedFace(), capacity());
            player.displayClientMessage(Component.translatable(received > 0
                    ? "message.oneforallsurvival.detector.charged" : "message.oneforallsurvival.detector.not_charged",
                    received), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ItemBattery.Provider(stack, DetectorItem::capacity);
    }

    /** La charge et le compteur changent à chaque tick : ce n'est pas un nouvel objet, la main ne doit pas rebondir. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * stored(stack) / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.detector.energy", stored(stack), capacity()));
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.detector.usage"));
    }
}
