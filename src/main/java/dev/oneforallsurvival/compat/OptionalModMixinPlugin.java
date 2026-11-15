package dev.oneforallsurvival.compat;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Les mixins d'un mod tiers ne s'appliquent que si ce mod est chargé dans une plage de versions dont le bytecode a été
 * lu ; sinon rien n'est appliqué et un avertissement le dit : ce que fait ce mod ne fait plus avancer les quêtes (ou
 * n'est plus marqué « posé »), rien ne plante. Une configuration par mod ({@link Create}, {@link FarmersDelight}),
 * non requise.
 */
public abstract class OptionalModMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String modId;
    private final String verifiedRange;
    private boolean enabled;

    protected OptionalModMixinPlugin(String modId, String verifiedRange) {
        this.modId = modId;
        this.verifiedRange = verifiedRange;
    }

    /** Vrai si cette version est dans la plage (notation de Maven) ; faux pour une plage illisible. */
    public static boolean inRange(String version, String range) {
        try {
            return VersionRange.createFromVersionSpec(range).containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException e) {
            return false;
        }
    }

    /** La plage de versions dont le bytecode a été lu. */
    public String verifiedRange() {
        return verifiedRange;
    }

    @Override
    public void onLoad(String mixinPackage) {
        LoadingModList mods = LoadingModList.get();
        ModInfo info = mods == null ? null
                : mods.getMods().stream().filter(m -> m.getModId().equals(modId)).findFirst().orElse(null);
        if (info == null) {
            LOGGER.info("Quêtes : {} absent, ses mixins ne sont pas appliqués", modId);
            enabled = false;
            return;
        }
        String version = info.getVersion().toString();
        enabled = inRange(version, verifiedRange);
        if (enabled) {
            LOGGER.info("Quêtes : mixins de {} {} appliqués", modId, version);
        } else {
            LOGGER.warn("Quêtes : {} {} hors de la plage vérifiée {} ; ses mixins ne sont pas appliqués, ce que ce "
                    + "mod fait ne compte pas pour les quêtes", modId, version, verifiedRange);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return enabled;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    /** Create : bytecode lu sur 6.0.8 ({@code Contraption}, {@code BlockHelper}). */
    public static final class Create extends OptionalModMixinPlugin {
        public Create() {
            super("create", "[6.0.8]");
        }
    }

    /** Farmer's Delight : bytecode lu sur 1.20.1-1.3.4 ({@code CookingPotResultSlot}, {@code CookingPotMenu}). */
    public static final class FarmersDelight extends OptionalModMixinPlugin {
        public FarmersDelight() {
            super("farmersdelight", "[1.20.1-1.3.4]");
        }
    }
}
