package dev.oneforallsurvival.economy.quest;

import dev.oneforallsurvival.role.RoleBook;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Les gestes de métier qu'une quête peut demander, chacun lié à un rôle : une quête de métier n'est tirée que pour ce
 * rôle. Les lots des autres métiers y inscrivent leurs gestes.
 */
public final class QuestJobs {

    public static final ResourceLocation REPAIR_MACHINE = new ResourceLocation("oneforallsurvival", "repair_machine");

    private static final Map<ResourceLocation, String> ROLES = new ConcurrentHashMap<>();

    static {
        register(REPAIR_MACHINE, RoleBook.ENGINEER_ROLE_ID);
    }

    private QuestJobs() {}

    public static void register(ResourceLocation action, String roleId) {
        ROLES.put(action, roleId);
    }

    public static Optional<String> roleOf(ResourceLocation action) {
        return Optional.ofNullable(ROLES.get(action));
    }
}
