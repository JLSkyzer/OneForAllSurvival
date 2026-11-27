package dev.oneforallsurvival.client.title;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.minecraft.client.gui.components.Button;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

/**
 * Action d'un bouton de Minecraft ({@code Button.onPress}, champ protégé {@code f_93717_}) : sa classe dit quel mod a
 * créé le bouton. Lecture seule, par réflexion ; {@code null} si le champ est illisible (une ligne au journal, une
 * fois).
 */
final class ButtonPress {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Field field;
    private static boolean broken;

    private ButtonPress() {}

    static Object of(Button button) {
        if (broken) {
            return null;
        }
        try {
            if (field == null) {
                field = ObfuscationReflectionHelper.findField(Button.class, "f_93717_");
            }
            return field.get(button);
        } catch (RuntimeException | IllegalAccessException e) {
            broken = true;
            LOGGER.warn("Écran titre : action des boutons illisible, panneaux d'autres mods rangés comme les autres", e);
            return null;
        }
    }
}
