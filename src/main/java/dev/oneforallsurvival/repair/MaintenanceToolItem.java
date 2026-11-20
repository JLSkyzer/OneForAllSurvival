package dev.oneforallsurvival.repair;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * L'outil de maintenance de l'ingénieur (lot T2) : sans usure ni action propre, il ne fait qu'ouvrir des droits, et
 * seulement à l'ingénieur qui le tient dans une de ses deux mains (antennes, trésor et tableau de la mairie). Un autre
 * joueur qui le tient ne gagne rien. Fabriqué à l'établi de l'ingénieur (recette hors de {@code table_only}).
 */
public class MaintenanceToolItem extends Item {

    /** Les objets qui comptent comme outil de maintenance ({@code oneforallsurvival:maintenance_tools}). */
    public static final TagKey<Item> TAG =
            TagKey.create(Registries.ITEM, new ResourceLocation("oneforallsurvival", "maintenance_tools"));

    public MaintenanceToolItem(Properties properties) {
        super(properties);
    }

    /** Une des deux mains de ce joueur porte-t-elle un objet du tag ? Ne dit rien du rôle. */
    public static boolean isHeldBy(Player player) {
        return player.getMainHandItem().is(TAG) || player.getOffhandItem().is(TAG);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oneforallsurvival.maintenance_tool"));
    }
}
