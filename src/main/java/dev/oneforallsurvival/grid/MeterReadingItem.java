package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.grid.MeterLedger.Anomaly;
import dev.oneforallsurvival.grid.MeterLedger.Reading;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Le relevé d'un compteur : un papier qui porte abonné, index, consommation depuis le relevé précédent, jour de jeu,
 * date réelle du serveur et anomalies. Tout est écrit par le serveur au relevé ; l'infobulle ne fait que lire.
 */
public final class MeterReadingItem extends Item {

    private static final String TAG_READING = "reading";
    private static final String TAG_SUBSCRIBER = "subscriber";
    private static final String TAG_INDEX = "index";
    private static final String TAG_CONSUMPTION = "consumption";
    private static final String TAG_DAY = "day";
    private static final String TAG_DATE = "real_date";
    private static final String TAG_ANOMALIES = "anomalies";
    private static final String TOOLTIP = "tooltip.oneforallsurvival.meter_reading.";

    public MeterReadingItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Reading reading) {
        ItemStack stack = new ItemStack(GridRegistry.METER_READING.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_SUBSCRIBER, reading.subscriber());
        tag.putLong(TAG_INDEX, reading.index());
        tag.putLong(TAG_CONSUMPTION, reading.consumption());
        tag.putLong(TAG_DAY, reading.day());
        tag.putString(TAG_DATE, reading.realDate());
        ListTag anomalies = new ListTag();
        for (Anomaly anomaly : reading.anomalies()) {
            anomalies.add(StringTag.valueOf(anomaly.name().toLowerCase(Locale.ROOT)));
        }
        tag.put(TAG_ANOMALIES, anomalies);
        stack.getOrCreateTag().put(TAG_READING, tag);
        return stack;
    }

    /** Le relevé que porte le papier, ou {@code null} pour un papier vierge. */
    @Nullable
    public static Reading reading(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG_READING, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = root.getCompound(TAG_READING);
        List<Anomaly> anomalies = new ArrayList<>();
        ListTag list = tag.getList(TAG_ANOMALIES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            for (Anomaly anomaly : Anomaly.values()) {
                if (anomaly.name().equalsIgnoreCase(list.getString(i))) {
                    anomalies.add(anomaly);
                }
            }
        }
        return new Reading(tag.getString(TAG_SUBSCRIBER), tag.getLong(TAG_INDEX), tag.getLong(TAG_CONSUMPTION),
                tag.getLong(TAG_DAY), tag.getString(TAG_DATE), anomalies);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        Reading reading = reading(stack);
        if (reading == null) {
            return;
        }
        lines.add(Component.translatable(TOOLTIP + "subscriber", reading.subscriber()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TOOLTIP + "index", reading.index()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TOOLTIP + "consumption", reading.consumption()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TOOLTIP + "day", reading.day()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(TOOLTIP + "date", reading.realDate()).withStyle(ChatFormatting.GRAY));
        if (reading.anomalies().isEmpty()) {
            lines.add(Component.translatable(TOOLTIP + "no_anomaly").withStyle(ChatFormatting.DARK_GREEN));
        }
        for (Anomaly anomaly : reading.anomalies()) {
            lines.add(Component.translatable(TOOLTIP + "anomaly." + anomaly.name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.RED));
        }
    }
}
