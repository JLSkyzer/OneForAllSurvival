package dev.oneforallsurvival.economy.invoice;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Le carnet de factures : clic droit sur un joueur proche, il ouvre chez l'émetteur le formulaire de la facture
 * ({@link InvoiceFormPacket}). Rien n'est émis avant le formulaire, et le serveur revérifie tout à sa réception
 * ({@link InvoiceService#issue}). Le carnet ne s'use pas.
 */
public final class InvoiceBookItem extends Item {

    public InvoiceBookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer issuer && target instanceof ServerPlayer debtor) {
            InvoiceService.IssueResult check = InvoiceService.check(issuer, debtor);
            if (check == InvoiceService.IssueResult.DONE) {
                InvoiceFormPacket.open(issuer, debtor);
            } else {
                issuer.displayClientMessage(Component.translatable(InvoiceService.MESSAGE + "issue."
                        + check.name().toLowerCase(Locale.ROOT), debtor.getGameProfile().getName()), true);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.invoice_book").withStyle(ChatFormatting.GRAY));
    }
}
