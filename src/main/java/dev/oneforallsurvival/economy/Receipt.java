package dev.oneforallsurvival.economy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Le contenu d'un reçu ({@link ReceiptItem}) : son genre (échange ou facture réglée), la date, l'autre partie et
 * les lignes données et reçues, gardés dans le NBT de l'objet ({@value #TAG}). Un reçu n'est qu'un papier : il ne
 * vaut rien et se range partout.
 */
public final class Receipt {

    public static final String TAG = "Receipt";
    public static final String KIND_TRADE = "trade";
    /** Reçu d'une facture payée par l'objet (pièces passées par le mod). */
    public static final String KIND_INVOICE_PAID = "invoice_paid";
    /** Reçu d'une facture que l'émetteur a déclarée réglée de main à main (aucune pièce passée par le mod). */
    public static final String KIND_INVOICE_BY_HAND = "invoice_by_hand";
    /** Lignes affichées au plus dans l'infobulle ; au-delà, « … et N autres ». */
    public static final int MAX_LINES = 24;

    private static final String TOOLTIP = "tooltip.oneforallsurvival.receipt.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Une ligne : un objet, son nombre, et la valeur de son contenu en cuivres si c'est une bourse. */
    public record Line(Item item, int count, long purseValue) {}

    private Receipt() {}

    /** Le reçu de {@code self} pour un échange avec {@code other}. */
    public static ItemStack forTrade(ServerPlayer self, ServerPlayer other, List<ItemStack> given,
            List<ItemStack> received, long when) {
        ItemStack stack = new ItemStack(EconomyRegistry.RECEIPT.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", KIND_TRADE);
        tag.putLong("When", when);
        tag.putString("Other", other.getGameProfile().getName());
        tag.put("Given", lines(given));
        tag.put("Received", lines(received));
        stack.getOrCreateTag().put(TAG, tag);
        return stack;
    }

    /**
     * Le reçu d'une facture réglée : {@code status} {@code PAID} (payée par l'objet) ou {@code SETTLED_BY_HAND}.
     * Il reprend émetteur, débiteur, montant, motif, dates (émission, règlement) et l'identifiant court.
     */
    public static ItemStack forInvoice(dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice invoice,
            dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status status) {
        ItemStack stack = new ItemStack(EconomyRegistry.RECEIPT.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", status == dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status.SETTLED_BY_HAND
                ? KIND_INVOICE_BY_HAND : KIND_INVOICE_PAID);
        tag.putLong("When", invoice.settledAt() > 0L ? invoice.settledAt() : System.currentTimeMillis());
        tag.putString("Zone", ZoneId.systemDefault().getId());
        tag.putString("Issuer", invoice.issuerName());
        tag.putString("Debtor", invoice.debtorName());
        tag.putLong("Amount", invoice.amount());
        tag.putString("Reason", invoice.reason());
        tag.putLong("IssuedAt", invoice.issuedAt());
        tag.putString("Invoice", invoice.shortId());
        stack.getOrCreateTag().put(TAG, tag);
        return stack;
    }

    private static boolean isInvoice(CompoundTag data) {
        String kind = data.getString("Kind");
        return KIND_INVOICE_PAID.equals(kind) || KIND_INVOICE_BY_HAND.equals(kind);
    }

    private static ListTag lines(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag line = new CompoundTag();
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            line.putString("Id", id == null ? "minecraft:air" : id.toString());
            line.putInt("Count", stack.getCount());
            if (Purse.isPurse(stack)) {
                line.putLong("Purse", Purse.total(stack));
            }
            list.add(line);
        }
        return list;
    }

    private static CompoundTag data(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root == null ? new CompoundTag() : root.getCompound(TAG);
    }

    public static String kind(ItemStack stack) {
        return data(stack).getString("Kind");
    }

    public static long when(ItemStack stack) {
        return data(stack).getLong("When");
    }

    public static String other(ItemStack stack) {
        return data(stack).getString("Other");
    }

    public static List<Line> given(ItemStack stack) {
        return readLines(data(stack).getList("Given", Tag.TAG_COMPOUND));
    }

    public static List<Line> received(ItemStack stack) {
        return readLines(data(stack).getList("Received", Tag.TAG_COMPOUND));
    }

    private static List<Line> readLines(ListTag list) {
        List<Line> out = new ArrayList<>();
        for (Tag raw : list) {
            CompoundTag line = (CompoundTag) raw;
            ResourceLocation id = ResourceLocation.tryParse(line.getString("Id"));
            Item item = id == null ? Items.AIR : ForgeRegistries.ITEMS.getValue(id);
            out.add(new Line(item == null ? Items.AIR : item, line.getInt("Count"), line.getLong("Purse")));
        }
        return out;
    }

    /** L'infobulle : genre, date, autre partie, puis les lignes données et reçues (au plus {@value #MAX_LINES}). */
    public static List<Component> tooltip(ItemStack stack) {
        List<Component> out = new ArrayList<>();
        CompoundTag data = data(stack);
        if (data.isEmpty()) {
            out.add(Component.translatable(TOOLTIP + "blank").withStyle(ChatFormatting.GRAY));
            return out;
        }
        out.add(Component.translatable(TOOLTIP + "kind." + kind(stack)).withStyle(ChatFormatting.GOLD));
        if (isInvoice(data)) {
            String zone = data.getString("Zone");
            String settled = dev.oneforallsurvival.economy.invoice.InvoiceItem.formatDate(data.getLong("When"), zone);
            String issued = dev.oneforallsurvival.economy.invoice.InvoiceItem.formatDate(data.getLong("IssuedAt"), zone);
            out.add(Component.translatable(TOOLTIP + "invoice.issuer", data.getString("Issuer")).withStyle(ChatFormatting.GRAY));
            out.add(Component.translatable(TOOLTIP + "invoice.debtor", data.getString("Debtor")).withStyle(ChatFormatting.GRAY));
            out.add(Component.translatable(TOOLTIP + "invoice.reason", data.getString("Reason")).withStyle(ChatFormatting.WHITE));
            out.add(Component.translatable(TOOLTIP + "invoice.amount", CoinMath.format(data.getLong("Amount")))
                    .withStyle(ChatFormatting.GOLD));
            out.add(Component.translatable(TOOLTIP + "invoice.issued", issued).withStyle(ChatFormatting.GRAY));
            out.add(Component.translatable(TOOLTIP + "invoice.settled", settled).withStyle(ChatFormatting.GRAY));
            out.add(Component.translatable(TOOLTIP + "invoice.id", data.getString("Invoice")).withStyle(ChatFormatting.DARK_GRAY));
            return out;
        }
        String date = DATE.format(Instant.ofEpochMilli(when(stack)).atZone(ZoneId.systemDefault()));
        out.add(Component.translatable(TOOLTIP + "date", date).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable(TOOLTIP + "other", other(stack)).withStyle(ChatFormatting.GRAY));
        int shown = 0;
        int hidden = 0;
        for (String part : List.of("given", "received")) {
            List<Line> lines = part.equals("given") ? given(stack) : received(stack);
            out.add(Component.translatable(TOOLTIP + part).withStyle(ChatFormatting.WHITE));
            if (lines.isEmpty()) {
                out.add(Component.translatable(TOOLTIP + "nothing").withStyle(ChatFormatting.DARK_GRAY));
            }
            for (Line line : lines) {
                if (shown >= MAX_LINES) {
                    hidden++;
                    continue;
                }
                out.add(describe(line).withStyle(ChatFormatting.GRAY));
                shown++;
            }
        }
        if (hidden > 0) {
            out.add(Component.translatable(TOOLTIP + "more", hidden).withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    private static MutableComponent describe(Line line) {
        Component name = new ItemStack(line.item()).getHoverName();
        if (line.purseValue() > 0L) {
            return Component.translatable(TOOLTIP + "line.purse", line.count(), name, CoinMath.format(line.purseValue()));
        }
        return Component.translatable(TOOLTIP + "line", line.count(), name);
    }
}
