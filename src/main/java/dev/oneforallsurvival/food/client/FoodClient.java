package dev.oneforallsurvival.food.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.food.DecayState;
import dev.oneforallsurvival.food.FoodDecay;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Branchements côté client seulement de la péremption : la ligne d'état dans l'infobulle et les écrans des machines. */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FoodClient {

    private FoodClient() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!FoodStamp.isPerishable(event.getItemStack())) {
            return;
        }
        // Sans monde (menu principal, livre de recettes hors partie), on n'a aucune horloge : rien à dire.
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        DecayState state = FoodStamp.state(event.getItemStack(), FoodStamp.now(level));
        String key;
        ChatFormatting color;
        switch (state) {
            case STALE -> {
                key = "stale";
                color = ChatFormatting.YELLOW;
            }
            case SPOILED -> {
                key = "spoiled";
                color = ChatFormatting.RED;
            }
            default -> {
                key = "fresh";
                color = ChatFormatting.GREEN;
            }
        }
        event.getToolTip().add(Component.translatable("food." + OneForAllSurvival.MODID + "." + key).withStyle(color));
        double fraction = FoodStamp.remainingFraction(event.getItemStack(), FoodStamp.now(level));
        event.getToolTip().add(bar(fraction, state == DecayState.STALE ? ChatFormatting.GOLD : color));
    }

    /** Traits de la barre de péremption. */
    public static final int BAR_CELLS = 20;

    /** La barre : des traits pleins dans la couleur de l'état, le reste en gris foncé. Du texte seulement. */
    static MutableComponent bar(double fraction, ChatFormatting color) {
        int full = FoodDecay.filledCells(fraction, BAR_CELLS);
        MutableComponent bar = Component.literal("|".repeat(full)).withStyle(color);
        if (full < BAR_CELLS) {
            bar.append(Component.literal("|".repeat(BAR_CELLS - full)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return bar;
    }

    /** Les écrans de l'armoire, du dessalinisateur et du séchoir s'enregistrent sur le bus du mod, au démarrage du client. */
    @Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {

        private ModBus() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MenuScreens.register(FoodRegistry.FRIDGE_MENU.get(), FridgeScreen::new);
                MenuScreens.register(FoodRegistry.DESALINATOR_MENU.get(), DesalinatorScreen::new);
                MenuScreens.register(FoodRegistry.DRYER_MENU.get(), DryerScreen::new);
            });
        }
    }
}
