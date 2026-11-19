package dev.oneforallsurvival.phone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("phone").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reset").executes(PhoneCommands::reset)));
    }

    /** Efface le code, le propriétaire et les réglages du téléphone tenu en main principale. */
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
        source.sendSuccess(() -> Component.translatable("message.oneforallsurvival.phone.reset"), true);
        return 1;
    }
}
