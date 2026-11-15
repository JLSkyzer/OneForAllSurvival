package dev.oneforallsurvival.economy.quest;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/** Une quête tirée : but, récompense en cuivres, progression et état de réclamation. Immuable. */
public record Quest(String templateId, QuestCategory category, @Nullable QuestTarget target, int goal, long reward,
        int progress, boolean claimed, @Nullable String titleKey) {

    public boolean complete() {
        return progress >= goal;
    }

    public Quest withProgress(int value) {
        return new Quest(templateId, category, target, goal, reward, Math.max(0, Math.min(goal, value)), claimed,
                titleKey);
    }

    public Quest claimedNow() {
        return new Quest(templateId, category, target, goal, reward, progress, true, titleKey);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("template", templateId);
        tag.putString("category", category.key());
        if (target != null) {
            tag.putString("target", target.text());
        }
        tag.putInt("goal", goal);
        tag.putLong("reward", reward);
        tag.putInt("progress", progress);
        tag.putBoolean("claimed", claimed);
        if (titleKey != null) {
            tag.putString("title", titleKey);
        }
        return tag;
    }

    public static Quest load(CompoundTag tag) {
        QuestCategory category = QuestCategory.byKey(tag.getString("category")).orElse(QuestCategory.BREAK);
        QuestTarget target =
                tag.contains("target", 8) ? QuestTarget.parse(tag.getString("target")).orElse(null) : null;
        int goal = Math.max(1, tag.getInt("goal"));
        return new Quest(tag.getString("template"), category, target, goal, Math.max(0L, tag.getLong("reward")),
                Math.max(0, Math.min(goal, tag.getInt("progress"))), tag.getBoolean("claimed"),
                tag.contains("title", 8) ? tag.getString("title") : null);
    }
}
