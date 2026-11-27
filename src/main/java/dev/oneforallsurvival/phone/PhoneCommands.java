package dev.oneforallsurvival.phone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.phone.call.EmergencyLog;
import dev.oneforallsurvival.phone.call.EmergencyService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Les commandes de l'équipe pour le téléphone, au niveau de permission 2. */
public final class PhoneCommands {

    private static final String URGENCES = "command.oneforallsurvival.phone.urgences.";

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("phone").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reset").executes(PhoneCommands::reset))
                .then(Commands.literal("urgences").executes(PhoneCommands::emergencies)));
    }

    /** Efface le code, le propriétaire et les réglages du téléphone tenu en main principale, et ferme ses sessions. */
    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        Optional<UUID> id = held.getItem() instanceof PhoneItem ? PhoneItem.phoneId(held) : Optional.empty();
        if (id.isEmpty()) {
            source.sendFailure(Component.translatable("message.oneforallsurvival.phone.reset.none"));
            return 0;
        }
        PhoneData.get(source.getServer()).erase(id.get());
        // Aucun écran ne reste ouvert sur l'ancien code (copies comprises).
        PhoneService.closeAll(source.getServer(), id.get(), PhoneStatePacket.CloseReason.ERASED);
        source.sendSuccess(() -> Component.translatable("message.oneforallsurvival.phone.reset"), true);
        return 1;
    }

    /**
     * Les {@value EmergencyLog#SHOWN} derniers appels d'urgence du journal (abus) : heure, joueur, service, position,
     * qui a répondu. Pour l'équipe seulement (niveau 2), jamais diffusé aux autres opérateurs.
     */
    private static int emergencies(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<EmergencyLog.Entry> entries = EmergencyService.latest(source.getServer());
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(URGENCES + "empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(URGENCES + "header", entries.size()), false);
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                .withZone(ZoneId.systemDefault());
        for (EmergencyLog.Entry e : entries) {
            Component answered = e.answeredBy().isEmpty() ? Component.translatable(URGENCES + "nobody")
                    : Component.literal(e.answeredBy());
            Component line = Component.translatable(URGENCES + "line",
                    clock.format(Instant.ofEpochMilli(e.atMillis())), e.playerName(), e.service(), e.x(), e.y(), e.z(),
                    e.dimension(), answered);
            source.sendSuccess(() -> line, false);
        }
        return entries.size();
    }
}
