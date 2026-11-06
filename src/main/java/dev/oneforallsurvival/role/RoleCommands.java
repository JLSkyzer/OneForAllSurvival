package dev.oneforallsurvival.role;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Commande {@code /role} : consultation pour tous, gestion pour les opérateurs. */
public final class RoleCommands {

    private static final SuggestionProvider<CommandSourceStack> ROLE_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    RoleSavedData.get(ctx.getSource().getServer()).book().roles().stream().map(Role::id), builder);

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("role");
        root.then(Commands.literal("list").executes(RoleCommands::list));
        root.then(Commands.literal("get")
                .executes(RoleCommands::getSelf)
                .then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                        .requires(s -> s.hasPermission(2))
                        .executes(RoleCommands::getOther)));
        root.then(Commands.literal("set").requires(s -> s.hasPermission(2))
                .then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests(ROLE_IDS)
                                .executes(RoleCommands::set))));
        root.then(Commands.literal("create").requires(s -> s.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("couleur", ColorArgument.color())
                                .then(Commands.argument("libelle", StringArgumentType.greedyString())
                                        .executes(RoleCommands::create)))));
        root.then(Commands.literal("edit").requires(s -> s.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ROLE_IDS)
                        .then(Commands.argument("couleur", ColorArgument.color())
                                .then(Commands.argument("libelle", StringArgumentType.greedyString())
                                        .executes(RoleCommands::edit)))));
        root.then(Commands.literal("delete").requires(s -> s.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ROLE_IDS)
                        .executes(RoleCommands::delete)));
        dispatcher.register(root);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendFailure(message);
        return 0;
    }

    private static Component roleName(Role role) {
        return Component.literal(role.label()).withStyle(role.color());
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        RoleBook book = RoleSavedData.get(ctx.getSource().getServer()).book();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.role.list.header"), false);
        for (Role role : book.roles()) {
            ctx.getSource().sendSuccess(() -> Component.literal("- " + role.id() + " : ").append(roleName(role)), false);
        }
        return book.roles().size();
    }

    private static int getSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        Role role = RoleSavedData.get(ctx.getSource().getServer()).book().roleOf(player.getUUID());
        ctx.getSource().sendSuccess(
                () -> Component.translatable("command.oneforallsurvival.role.get.self", roleName(role)), false);
        return 1;
    }

    /** Un seul profil est accepté, pour qu'un sélecteur large ne change pas le rôle de plusieurs joueurs. */
    private static GameProfile singleProfile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "joueur");
        if (profiles.size() != 1) {
            ctx.getSource().sendFailure(Component.translatable("command.oneforallsurvival.role.one_player_only"));
            return null;
        }
        return profiles.iterator().next();
    }

    private static int getOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile profile = singleProfile(ctx);
        if (profile == null) {
            return 0;
        }
        Role role = RoleSavedData.get(ctx.getSource().getServer()).book().roleOf(profile.getId());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.oneforallsurvival.role.get.other", profile.getName(), roleName(role)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile profile = singleProfile(ctx);
        if (profile == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        RoleSavedData data = RoleSavedData.get(server);
        String id = StringArgumentType.getString(ctx, "role");
        try {
            data.book().assign(profile.getId(), id);
        } catch (IllegalArgumentException e) {
            return fail(ctx, Component.translatable("command.oneforallsurvival.role.unknown", id));
        }
        data.setDirty();
        RoleEvents.refreshAll(server);
        Role role = data.book().roleOf(profile.getId());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.oneforallsurvival.role.set", profile.getName(), roleName(role)), true);
        return 1;
    }

    /** Couleur de texte uniquement : {@code reset} passe {@link ColorArgument} mais n'est pas une couleur. */
    private static ChatFormatting color(CommandContext<CommandSourceStack> ctx) {
        ChatFormatting color = ColorArgument.getColor(ctx, "couleur");
        return color.isColor() ? color : null;
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        ChatFormatting color = color(ctx);
        if (color == null) {
            return fail(ctx, Component.translatable("command.oneforallsurvival.role.bad_color"));
        }
        MinecraftServer server = ctx.getSource().getServer();
        RoleSavedData data = RoleSavedData.get(server);
        String id = StringArgumentType.getString(ctx, "id");
        String label = StringArgumentType.getString(ctx, "libelle").trim();
        if (label.isEmpty()) {
            return fail(ctx, Component.translatable("command.oneforallsurvival.role.empty_label"));
        }
        try {
            data.book().createRole(id, label, color);
        } catch (IllegalArgumentException e) {
            return fail(ctx, Component.translatable("command.oneforallsurvival.role.create_failed", id));
        }
        data.setDirty();
        RoleEvents.refreshAll(server);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.role.created", id), true);
        return 1;
    }

    private static int edit(CommandContext<CommandSourceStack> ctx) {
        ChatFormatting color = color(ctx);
        if (color == null) {
            return fail(ctx, Component.translatable("command.oneforallsurvival.role.bad_color"));
        }
        MinecraftServer server = ctx.getSource().getServer();
        RoleSavedData data = RoleSavedData.get(server);
        String id = StringArgumentType.getString(ctx, "id");
        String label = StringArgumentType.getString(ctx, "libelle").trim();
        if (label.isEmpty()) {
            return fail(ctx, Component.translatable("command.oneforallsurvival.role.empty_label"));
        }
        try {
            data.book().editRole(id, label, color);
        } catch (IllegalArgumentException e) {
            return fail(ctx, Component.translatable("command.oneforallsurvival.role.unknown", id));
        }
        data.setDirty();
        RoleEvents.refreshAll(server);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.role.edited", id), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        RoleSavedData data = RoleSavedData.get(server);
        String id = StringArgumentType.getString(ctx, "id");
        try {
            data.book().deleteRole(id);
        } catch (IllegalArgumentException e) {
            return fail(ctx, Component.translatable("command.oneforallsurvival.role.unknown", id));
        } catch (IllegalStateException e) {
            return fail(ctx, Component.translatable("command.oneforallsurvival.role.builtin", id));
        }
        data.setDirty();
        RoleEvents.refreshAll(server);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.role.deleted", id), true);
        return 1;
    }
}
