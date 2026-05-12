package hytale.ai;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import hytale.ai.config.PlayerSettings;
import hytale.ai.config.PlayerSettingsManager;

import java.util.UUID;

/**
 * Shared action facade used by both chat commands and the GUI panel.
 * All state changes that can be triggered from the UI go through here.
 */
public final class CompanionActions {

    private CompanionActions() {}

    public static void summonOrDismiss(UUID playerUuid, Ref<EntityStore> playerEntityRef,
                                        Store<EntityStore> store, HytaleAIMod mod) {
        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
        if (profile == null || profile.getNpcName() == null) return;

        if (profile.isSummoned()) {
            profile.setSummoned(false);
            PlayerProfileManager.saveProfile(playerUuid, profile);
            mod.despawnCompanion(playerUuid, store);
        } else {
            long deathTs = profile.getDeathTimestamp();
            long cooldownMs = 3L * 60 * 1000;
            if (deathTs > 0 && System.currentTimeMillis() - deathTs < cooldownMs) return;

            profile.setSummoned(true);
            PlayerProfileManager.saveProfile(playerUuid, profile);
            mod.spawnCompanion(playerUuid, profile, playerEntityRef);
        }
    }

    public static void setStance(UUID playerUuid, String stance, HytaleAIMod mod) {
        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
        if (profile == null) return;
        profile.setCombatStance(stance);
        PlayerProfileManager.saveProfile(playerUuid, profile);
        mod.getActiveCompanions().remove(playerUuid); // force prompt refresh
    }

    public static void toggleRl(UUID playerUuid, HytaleAIMod mod) {
        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        if (settings == null) return;
        settings.setRlEnabled(!settings.isRlEnabled());
        PlayerSettingsManager.saveSettings(playerUuid, settings);
    }

    public static void toggleDebug(UUID playerUuid, HytaleAIMod mod) {
        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        if (settings == null) return;
        settings.setDebugMode(!settings.isDebugMode());
        PlayerSettingsManager.saveSettings(playerUuid, settings);
    }

    public static void setLlmModel(UUID playerUuid, String model, HytaleAIMod mod) {
        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        if (settings == null || model == null || model.isBlank()) return;
        settings.setAiModel(model.trim());
        PlayerSettingsManager.saveSettings(playerUuid, settings);
        mod.getActiveCompanions().remove(playerUuid); // force provider refresh
    }
}
