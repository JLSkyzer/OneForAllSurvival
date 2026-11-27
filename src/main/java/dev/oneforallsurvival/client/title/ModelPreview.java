package dev.oneforallsurvival.client.title;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.client.title.TitleLayout.Rect;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.components.AbstractWidget;
import org.slf4j.Logger;

/**
 * Place à l'écran d'un aperçu 3D du joueur d'un autre mod. Quick-Skin 4.1.0 ({@code PlayerWidget}) déclare un widget
 * de taille nulle et dessine son modèle autour d'un point ; il publie ce point et l'échelle par trois méthodes
 * publiques ({@code getLayoutCenterX}, {@code getLayoutFeetY}, {@code getEffectiveScale}), lues ici par réflexion (le
 * mod n'est pas une dépendance). Un widget qui ne les a pas : vide.
 */
final class ModelPreview {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Méthodes par classe de widget ; tableau vide pour une classe sans aperçu. */
    private static final Map<Class<?>, Method[]> METHODS = new ConcurrentHashMap<>();

    private ModelPreview() {}

    static Optional<Rect> footprint(AbstractWidget widget) {
        Method[] m = METHODS.computeIfAbsent(widget.getClass(), ModelPreview::lookup);
        if (m.length == 0) {
            return Optional.empty();
        }
        try {
            int centerX = (Integer) m[0].invoke(widget);
            int feetY = (Integer) m[1].invoke(widget);
            float scale = (Float) m[2].invoke(widget);
            return Optional.of(TitleLayout.modelFootprint(centerX, feetY, scale));
        } catch (ReflectiveOperationException | RuntimeException e) {
            METHODS.put(widget.getClass(), new Method[0]);
            LOGGER.warn("Écran titre : aperçu du joueur de {} illisible, son rectangle de widget compte à la place",
                    widget.getClass().getName(), e);
            return Optional.empty();
        }
    }

    private static Method[] lookup(Class<?> type) {
        try {
            Method cx = type.getMethod("getLayoutCenterX");
            Method feet = type.getMethod("getLayoutFeetY");
            Method scale = type.getMethod("getEffectiveScale");
            if (cx.getReturnType() == int.class && feet.getReturnType() == int.class && scale.getReturnType() == float.class) {
                return new Method[] {cx, feet, scale};
            }
        } catch (NoSuchMethodException | SecurityException e) {
            // pas un aperçu du joueur
        }
        return new Method[0];
    }
}
