package dev.oneforallsurvival.explore;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.machine.ItemBattery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Le détecteur de métaux : une batterie, et un bip dont la cadence et la hauteur disent la distance à la cache
 * enfouie la plus proche. Il n'agit que côté serveur, tenu dans une main, chargé, dans une dimension qui a des
 * caches ; seul le porteur entend le bip. Accroupi, un clic droit sur une source le recharge. Sa barre montre la
 * charge.
 *
 * <p>La pile ne change pas à chaque tick : l'énergie se paie par blocs de {@link ItemBattery#PERIOD} ticks et le
 * compteur de bips vit dans une table serveur transitoire, par joueur, vidée à sa déconnexion.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID)
public class DetectorItem extends Item {

    private static final int BAR_COLOR = 0x6FB7D8;
    /** Ce que le détecteur d'un joueur garde d'un tick à l'autre : les ticks payés d'avance et ceux depuis le dernier bip. */
    private static final class State {
        private final ItemBattery.Prepaid prepaid = new ItemBattery.Prepaid();
        private int beepTicks;
    }

    /** Par joueur, d'une main à l'autre ; jamais dans la pile, dont le NBT est envoyé aux clients à chaque changement. */
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

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
        CacheIndex index = CacheIndex.get(level);
        if (index.size() == 0) {
            return Tick.IDLE;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (!state.prepaid.pay(stack, ExploreConfig.detectorEnergy(), capacity())) {
            return Tick.IDLE;
        }
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
            state.beepTicks = 0;
            return Tick.SEARCHING;
        }
        double distance = player.position().distanceTo(Vec3.atCenterOf(found.get()));
        int interval = DetectorRules.beepInterval(distance, range);
        if (interval < 0) {
            state.beepTicks = 0;
            return Tick.SEARCHING;
        }
        state.beepTicks++;
        if (state.beepTicks < interval) {
            return Tick.SEARCHING;
        }
        state.beepTicks = 0;
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0F,
                DetectorRules.pitch(distance, range));
        return Tick.BEEPED;
    }

    /** Le compteur d'un joueur qui s'en va ne sert plus. */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return ItemBattery.useOnSource(stack, context, capacity(), "message.oneforallsurvival.detector");
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ItemBattery.Provider(stack, DetectorItem::capacity);
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
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.detector.energy", stored(stack), capacity()));
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.detector.usage"));
    }
}
