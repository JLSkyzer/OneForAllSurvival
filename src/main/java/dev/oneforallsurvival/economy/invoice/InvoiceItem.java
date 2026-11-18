package dev.oneforallsurvival.economy.invoice;

import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * La facture : un papier, empilable à 1, qui n'est qu'une vue du registre ({@link InvoiceRegistry} fait foi). Son
 * NBT porte {@code Id}, {@code Issuer}, {@code IssuerName}, {@code Debtor}, {@code DebtorName}, {@code Amount},
 * {@code Reason}, {@code IssuedAt} et le fuseau du serveur ({@code Zone}) pour la date affichée.
 *
 * <p>L'utiliser ouvre l'écran qui la montre ({@code economy.client.InvoiceScreen}) ; rien n'est payé au premier clic. Une fois par
 * seconde, dans l'inventaire d'un joueur, une facture que le registre dit réglée devient un reçu. Ce n'est pas de
 * l'argent : elle se jette et se range partout.
 */
public final class InvoiceItem extends Item {

    public static final String TOOLTIP = "tooltip.oneforallsurvival.invoice.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public InvoiceItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** La vue d'une facture enregistrée. */
    public static ItemStack of(Invoice invoice) {
        ItemStack stack = new ItemStack(EconomyRegistry.INVOICE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID("Id", invoice.id());
        tag.putUUID("Issuer", invoice.issuer());
        tag.putString("IssuerName", invoice.issuerName());
        tag.putUUID("Debtor", invoice.debtor());
        tag.putString("DebtorName", invoice.debtorName());
        tag.putLong("Amount", invoice.amount());
        putReason(tag, invoice);
        tag.putLong("IssuedAt", invoice.issuedAt());
        tag.putString("Zone", ZoneId.systemDefault().getId());
        return stack;
    }

    /** Clé du motif traduit d'une facture publique (composant JSON), à côté du motif brut {@code Reason}. */
    public static final String REASON_TEXT = "ReasonText";

    /**
     * Écrit le motif : brut ({@code Reason}) et, pour une facture publique, traduit chez le client
     * ({@code ReasonText}, le motif du créancier : {@code DebitPayee.describe}).
     */
    public static void putReason(CompoundTag tag, Invoice invoice) {
        tag.putString("Reason", invoice.reason());
        if (invoice.isPublic()) {
            tag.putString(REASON_TEXT,
                    Component.Serializer.toJson(dev.oneforallsurvival.economy.debit.DirectDebit.describe(invoice)));
        }
    }

    /** Le motif à afficher : le traduit s'il y en a un (facture publique), sinon le motif brut. */
    public static Component reasonText(CompoundTag tag) {
        if (tag.contains(REASON_TEXT)) {
            try {
                Component text = Component.Serializer.fromJson(tag.getString(REASON_TEXT));
                if (text != null) {
                    return text;
                }
            } catch (RuntimeException e) {
                // motif traduit illisible : le brut
            }
        }
        return Component.literal(tag.getString("Reason"));
    }

    /** L'identifiant que porte cette pile, si c'est une facture qui en porte un. */
    public static Optional<UUID> idOf(ItemStack stack) {
        if (!stack.is(EconomyRegistry.INVOICE.get())) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID("Id") ? Optional.of(tag.getUUID("Id")) : Optional.empty();
    }

    // ---------------------------------------------------------------------------------------------
    // Lecture du NBT (pour l'écran et l'infobulle : rien de tout cela ne fait foi)
    // ---------------------------------------------------------------------------------------------

    private static CompoundTag tag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag;
    }

    @Nullable
    public static UUID debtorOf(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.hasUUID("Debtor") ? tag.getUUID("Debtor") : null;
    }

    public static String issuerName(ItemStack stack) {
        return tag(stack).getString("IssuerName");
    }

    public static String debtorName(ItemStack stack) {
        return tag(stack).getString("DebtorName");
    }

    public static long amount(ItemStack stack) {
        return tag(stack).getLong("Amount");
    }

    public static String reason(ItemStack stack) {
        return tag(stack).getString("Reason");
    }

    /** Le motif à afficher (traduit pour une facture publique). */
    public static Component reasonText(ItemStack stack) {
        return reasonText(tag(stack));
    }

    /** La date d'émission, {@code dd/MM/yyyy HH:mm}, au fuseau du serveur qui l'a émise. */
    public static String date(ItemStack stack) {
        return formatDate(tag(stack).getLong("IssuedAt"), tag(stack).getString("Zone"));
    }

    /** {@code dd/MM/yyyy HH:mm} au fuseau {@code zone} (le fuseau local s'il est illisible). */
    public static String formatDate(long epochMillis, String zone) {
        ZoneId id;
        try {
            id = zone == null || zone.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(zone);
        } catch (DateTimeException e) {
            id = ZoneId.systemDefault();
        }
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(id));
    }

    public static String shortId(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.hasUUID("Id") ? Invoice.shortId(tag.getUUID("Id")) : "?";
    }

    // ---------------------------------------------------------------------------------------------
    // Comportement
    // ---------------------------------------------------------------------------------------------

    /** Ouvre l'écran de la facture (côté client) ; le paiement attend le bouton « Payer ». */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
            ItemStack shown = stack.copy();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.oneforallsurvival.economy.client.InvoiceScreen.open(shown, slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Une fois par seconde, côté serveur : une facture réglée au registre devient un reçu dans la même case. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            InvoiceService.refresh(player, stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (idOf(stack).isEmpty()) {
            tooltip.add(Component.translatable(TOOLTIP + "blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable(TOOLTIP + "issuer", issuerName(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(TOOLTIP + "debtor", debtorName(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(TOOLTIP + "reason", reasonText(stack)).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable(TOOLTIP + "amount", CoinMath.format(amount(stack))).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(TOOLTIP + "date", date(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(TOOLTIP + "id", shortId(stack)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(TOOLTIP + "use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
