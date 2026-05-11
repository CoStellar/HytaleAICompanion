package hytale.ai.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.TalkLines;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Globalny system ambientowych reakcji — uruchamiany co ~45 sekund.
 * Sprawdza warunki srodowiskowe (pora nocy, woda, HP gracza) i wyzwala
 * spontaniczne, skryptowane linie dialogowe kompana.
 * Nie dziala podczas aktywnej walki (RL przejmuje kontrole gdy sa wrogie NPC).
 */
public class SpontaneousReactionSystem extends TickingSystem<EntityStore> {

    private static final float TICK_INTERVAL = 45.0f;
    private static final float LOW_HP_THRESHOLD = 0.35f;

    private final HytaleAIMod mod;
    private final Map<UUID, Float> previousHp = new ConcurrentHashMap<>();

    private float elapsed = 0.0f;

    public SpontaneousReactionSystem(HytaleAIMod mod) {
        this.mod = mod;
    }

    @Override
    public void tick(float dt, int index, @Nonnull Store<EntityStore> store) {
        elapsed += dt;
        if (elapsed < TICK_INTERVAL) return;
        elapsed = 0.0f;

        World world = store.getExternalData().getWorld();

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            UUID playerUuid = playerRef.getUuid();
            CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
            if (profile == null || !profile.isSummoned()) continue;

            Ref<EntityStore> companionRef = mod.getActiveCompanionEntities().get(playerUuid);
            if (companionRef == null || !companionRef.isValid()) continue;

            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) continue;

            checkAndReact(playerUuid, playerRef, playerEntityRef, profile, store);
        }
    }

    private void checkAndReact(UUID playerUuid, PlayerRef playerRef,
                                Ref<EntityStore> playerEntityRef, CompanionProfile profile,
                                Store<EntityStore> store) {
        String companionName = profile.getNpcName();
        String line = pickReactionLine(playerUuid, playerEntityRef, store);
        if (line == null) return;

        playerRef.sendMessage(Message.raw("[" + companionName + "] " + line));
    }

    private String pickReactionLine(UUID playerUuid, Ref<EntityStore> playerEntityRef, Store<EntityStore> store) {
        EntityStatMap stats = store.getComponent(playerEntityRef, EntityStatMap.getComponentType());
        if (stats == null) return null;

        // 1. Gracz pod woda (tlen spada)
        int oxygenIndex = DefaultEntityStatTypes.getOxygen();
        EntityStatValue oxygenStat = stats.get(oxygenIndex);
        if (oxygenStat != null && oxygenStat.get() < oxygenStat.getMax() * 0.6f) {
            return TalkLines.get().getRandom("water_warning");
        }

        // 2. HP gracza niskie (ale nie w walce — RL zajmuje sie walka)
        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = stats.get(healthIndex);
        if (healthStat != null) {
            float ratio = healthStat.get() / healthStat.getMax();
            Float prevHpRatio = previousHp.get(playerUuid);
            previousHp.put(playerUuid, ratio);

            if (ratio < LOW_HP_THRESHOLD) {
                return TalkLines.get().getRandom("player_hurt_ambient");
            }
            // HP gwaltownie spadlo od ostatniego sprawdzenia
            if (prevHpRatio != null && prevHpRatio - ratio > 0.25f) {
                return TalkLines.get().getRandom("player_hurt_ambient");
            }
        }

        // 3. Pora nocy
        try {
            WorldTimeResource timeResource = store.getResource(TimeModule.get().getWorldTimeResourceType());
            if (timeResource != null) {
                LocalDateTime dt = timeResource.getGameDateTime();
                int hour = dt.getHour();
                if (hour >= 20 || hour < 6) {
                    return TalkLines.get().getRandom("night_warning");
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}
