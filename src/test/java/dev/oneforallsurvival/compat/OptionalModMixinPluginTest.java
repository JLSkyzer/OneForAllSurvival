package dev.oneforallsurvival.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** La plage de versions dont le bytecode a été lu : la version lue y est, les voisines non. */
class OptionalModMixinPluginTest {

    @Test
    void laVersionLueDeFarmersDelightEstDansSaPlage() {
        String plage = new OptionalModMixinPlugin.FarmersDelight().verifiedRange();
        assertTrue(OptionalModMixinPlugin.inRange("1.20.1-1.3.4", plage), plage);
        assertFalse(OptionalModMixinPlugin.inRange("1.20.1-1.3.5", plage), plage);
        assertFalse(OptionalModMixinPlugin.inRange("1.20.1-1.3.3", plage), plage);
        assertFalse(OptionalModMixinPlugin.inRange("1.20.1-1.4.0", plage), plage);
    }

    @Test
    void laVersionLueDeCreateEstDansSaPlage() {
        String plage = new OptionalModMixinPlugin.Create().verifiedRange();
        assertTrue(OptionalModMixinPlugin.inRange("6.0.8", plage), plage);
        assertFalse(OptionalModMixinPlugin.inRange("6.0.9", plage), plage);
        assertFalse(OptionalModMixinPlugin.inRange("6.0.7", plage), plage);
    }
}
