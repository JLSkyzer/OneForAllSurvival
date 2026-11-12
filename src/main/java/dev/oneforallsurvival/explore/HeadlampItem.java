package dev.oneforallsurvival.explore;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.machine.ItemBattery;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

/**
 * La lampe frontale : un casque à batterie. Portée et chargée, elle éclaire autour de la tête ({@link HeadlampLights}).
 * Elle ne se recharge pas sur la tête : tenue en main, accroupi, un clic droit sur une source la remplit. Sa barre montre la
 * charge. Pas de durabilité : elle ne s'use pas, elle se vide.
 */
public class HeadlampItem extends ArmorItem {

    private static final int BAR_COLOR = 0xF0C040;

    /** Protection 1 à la tête, aucune durabilité, aucun enchantement. Texture portée : {@code headlamp_layer_1}. */
    public enum Material implements ArmorMaterial {
        HEADLAMP;

        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return type == ArmorItem.Type.HELMET ? 1 : 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return OneForAllSurvival.MODID + ":headlamp";
        }

        @Override
        public float getToughness() {
            return 0.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    }

    public HeadlampItem(Properties properties) {
        super(Material.HEADLAMP, ArmorItem.Type.HELMET, properties);
    }

    private static int capacity() {
        return ExploreConfig.headlampCapacity();
    }

    /** FE contenus dans la batterie de cette pile. */
    public static int stored(ItemStack stack) {
        return ItemBattery.stored(stack, capacity());
    }

    /** Fixe la charge, bornée à la capacité. */
    public static void store(ItemStack stack, int fe) {
        ItemBattery.store(stack, fe, capacity());
    }

    /** Dépense {@code fe} si la batterie les a ; sinon ne touche à rien. */
    public static boolean spend(ItemStack stack, int fe) {
        return ItemBattery.spend(stack, fe, capacity());
    }

    /** La lampe frontale portée à la tête de {@code player}, ou une pile vide. */
    public static ItemStack worn(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        return head.getItem() instanceof HeadlampItem ? head : ItemStack.EMPTY;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return ItemBattery.useOnSource(stack, context, capacity(), "message.oneforallsurvival.headlamp");
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ItemBattery.Provider(stack, HeadlampItem::capacity);
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
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.headlamp.energy", stored(stack), capacity()));
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.headlamp.usage"));
    }
}
