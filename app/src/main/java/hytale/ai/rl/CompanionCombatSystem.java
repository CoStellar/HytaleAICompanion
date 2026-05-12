package hytale.ai.rl;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import hytale.ai.config.PlayerSettings;
import hytale.ai.config.TalkLines;
import hytale.ai.npc.CompanionController;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Globalny system taktyczny uruchamiany co ~2 sekundy.
 * Gdy kompan wykryje wrogow, przejmuje kontrole od LLM i uzywa Q-Learningu
 * do podejmowania decyzji bojowych w czasie rzeczywistym.
 * Gdy rlEnabled=false, stosuje logike skryptowana opartą na nastawieniu gracza.
 */
public class CompanionCombatSystem extends TickingSystem<EntityStore> {

    private static final float TICK_INTERVAL = 2.0f;
    private static final float RADAR_RADIUS_SQ = 900.0f; // 30 blokow

    private final HytaleAIMod mod;
    private final Map<UUID, CombatObservation> previousObservations = new ConcurrentHashMap<>();

    private float elapsed = 0.0f;

    public CompanionCombatSystem(HytaleAIMod mod) {
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
            Ref<EntityStore> companionRef = mod.getActiveCompanionEntities().get(playerUuid);
            if (companionRef == null || !companionRef.isValid()) continue;

            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) continue;

            processCompanion(playerUuid, playerRef, playerEntityRef, companionRef, store);
        }
    }

    private void processCompanion(UUID playerUuid, PlayerRef playerRef,
                                   Ref<EntityStore> playerEntityRef, Ref<EntityStore> companionRef,
                                   Store<EntityStore> store) {
        QLearningAgent agent = mod.getAgentForPlayer(playerUuid);
        if (agent == null) return;

        float[] hpData = getPlayerHp(playerEntityRef, store);
        float playerHp = hpData[0];
        float playerMaxHp = hpData[1];

        RLState currentState = computeState(playerHp, playerMaxHp, playerEntityRef, companionRef, store);

        CompanionController controller = mod.getActiveControllers().get(playerUuid);
        if (controller != null) {
            controller.setLastKnownState(currentState);
            controller.refreshHudHp();
        }

        // Skip entirely when companion is in STOP mode
        if (controller != null && controller.isStopped()) {
            previousObservations.remove(playerUuid);
            return;
        }

        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        boolean learningMode = settings == null || settings.isRlEnabled();

        // If the LLM recently issued a direct command or feedback, yield to it:
        // skip the Bellman update AND action selection for this tick.
        if (controller != null && controller.hasLlmControlLock()) {
            controller.decrementLlmControlLock();
            previousObservations.remove(playerUuid);
            return;
        }

        if (learningMode) {
            // --- Learning mode: Bellman update + epsilon-greedy selection ---
            CombatObservation prev = previousObservations.get(playerUuid);
            if (prev != null) {
                float reward = computeReward(prev, currentState, playerHp);
                agent.update(prev.state, prev.action, reward, currentState);

                if (reward <= -10.0f) {
                    CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
                    if (profile != null) {
                        profile.setPlayerDeathsWitnessed(profile.getPlayerDeathsWitnessed() + 1);
                        PlayerProfileManager.saveProfile(playerUuid, profile);
                    }
                }
            }
        }

        if (!currentState.hasCombatThreat()) {
            previousObservations.remove(playerUuid);
            return;
        }

        // Respect combatStance set by the player or LLM
        CompanionProfile stanceProfile = mod.getPlayerProfiles().get(playerUuid);
        String stance = (stanceProfile != null && stanceProfile.getCombatStance() != null)
                ? stanceProfile.getCombatStance() : "AGRESYWNY";

        if ("PASYWNY".equals(stance)) {
            // Passive: only flee when HP is critical, otherwise do nothing
            if (currentState.playerHpBucket() == 0 && controller != null) {
                controller.applyRLAction("Flee");
            }
            previousObservations.remove(playerUuid);
            return;
        }

        if ("DEFENSYWNY".equals(stance) && currentState.distanceBucket() > 0) {
            // Defensive: only engage enemies that are very close (dist bucket 0)
            previousObservations.remove(playerUuid);
            return;
        }

        // In GUARD mode only engage threats within 6 blocks
        if (controller != null && controller.isGuarding() && currentState.distanceBucket() > 0) {
            previousObservations.remove(playerUuid);
            return;
        }

        RLAction chosenAction = learningMode
                ? agent.selectAction(currentState)      // epsilon-greedy, updates epsilon
                : agent.inferBestAction(currentState);  // pure greedy, no side-effects

        applyAction(chosenAction, playerUuid, playerRef, playerEntityRef, store, currentState);

        if (learningMode) {
            previousObservations.put(playerUuid, new CombatObservation(currentState, chosenAction, playerHp, playerMaxHp));
        } else {
            previousObservations.remove(playerUuid);
        }

        if (settings != null && settings.isDebugMode()) {
            HytaleAIMod.LOGGER.info((learningMode ? "[LEARN] " : "[INFER] ")
                    + agent.debugInfo(currentState) + " -> " + chosenAction);
        }
    }

    // ---- RL action dispatcher ----

    private void applyAction(RLAction action, UUID playerUuid, PlayerRef playerRef,
                              Ref<EntityStore> playerEntityRef, Store<EntityStore> store, RLState state) {
        Map<UUID, CompanionController> controllers = mod.getActiveControllers();
        CompanionController controller = controllers.get(playerUuid);
        if (controller == null) return;

        switch (action) {
            case FOLLOW -> controller.applyRLAction("Follow");
            case ATTACK -> controller.applyRLAction("Attack");
            case FLEE   -> controller.applyRLAction("Flee");
            case TALK -> {
                controller.applyRLAction("Talk");
                sendTalkMessage(state, playerRef, playerUuid);
            }
            case HEAL -> {
                controller.applyRLAction("Heal");
                performHealing(playerEntityRef, store, playerRef, playerUuid);
            }
            default -> controller.applyRLAction("Follow");
        }
    }

    private void sendTalkMessage(RLState state, PlayerRef playerRef, UUID playerUuid) {
        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
        String name = (profile != null) ? profile.getNpcName() : "Kompan";

        String line;
        if (state.playerHpBucket() == 0 && !state.hasCombatThreat()) {
            line = TalkLines.get().getRandomOrFallback("critical_hp_no_enemies", "Zatrzymaj sie!");
        } else if (state.playerHpBucket() == 0) {
            line = TalkLines.get().getRandomOrFallback("critical_hp_combat", "Trzymaj sie!");
        } else if (state.enemyCountBucket() > 0) {
            line = TalkLines.get().getRandomOrFallback("enemy_spotted", "Uwaga!");
        } else {
            line = TalkLines.get().getRandomOrFallback("follow_idle", "...");
        }

        playerRef.sendMessage(Message.raw("[" + name + "] " + line));
    }

    private void performHealing(Ref<EntityStore> playerEntityRef, Store<EntityStore> store,
                                 PlayerRef playerRef, UUID playerUuid) {
        EntityStatMap stats = store.getComponent(playerEntityRef, EntityStatMap.getComponentType());
        if (stats == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = stats.get(healthIndex);
        if (healthStat == null) return;

        float current = healthStat.get();
        float max = healthStat.getMax();
        if (current < max) {
            stats.setStatValue(healthIndex, Math.min(max, current + 20.0f));
            CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
            String name = (profile != null) ? profile.getNpcName() : "Kompan";
            String line = TalkLines.get().getRandomOrFallback("healing_companion", "Mam cie.");
            playerRef.sendMessage(Message.raw("[" + name + "] " + line));
        }
    }

    // ---- State computation ----

    private RLState computeState(float playerHp, float playerMaxHp,
                                  Ref<EntityStore> playerEntityRef, Ref<EntityStore> companionRef,
                                  Store<EntityStore> store) {
        int playerHpBucket = 3;
        if (playerMaxHp > 0) {
            float ratio = playerHp / playerMaxHp;
            if (ratio < 0.25f) playerHpBucket = 0;
            else if (ratio < 0.50f) playerHpBucket = 1;
            else if (ratio < 0.75f) playerHpBucket = 2;
        }

        TransformComponent origin = store.getComponent(companionRef, TransformComponent.getComponentType());
        if (origin == null) return new RLState(playerHpBucket, 0, 2);

        int[] enemyCount = {0};
        double[] nearestDistSq = {Double.MAX_VALUE};
        final double ox = origin.getPosition().getX();
        final double oy = origin.getPosition().getY();
        final double oz = origin.getPosition().getZ();

        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> scanner = (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref.equals(playerEntityRef) || ref.equals(companionRef)) continue;
                TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
                if (t != null) {
                    double dx = t.getPosition().getX() - ox;
                    double dy = t.getPosition().getY() - oy;
                    double dz = t.getPosition().getZ() - oz;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq <= RADAR_RADIUS_SQ) {
                        enemyCount[0]++;
                        if (distSq < nearestDistSq[0]) nearestDistSq[0] = distSq;
                    }
                }
            }
        };

        store.forEachChunk(NPCEntity.getComponentType(), scanner);

        int enemyBucket = Math.min(enemyCount[0], 3);
        int distBucket = 2;
        if (nearestDistSq[0] <= 36.0) distBucket = 0;
        else if (nearestDistSq[0] <= 225.0) distBucket = 1;

        return new RLState(playerHpBucket, enemyBucket, distBucket);
    }

    private float[] getPlayerHp(Ref<EntityStore> playerEntityRef, Store<EntityStore> store) {
        EntityStatMap stats = store.getComponent(playerEntityRef, EntityStatMap.getComponentType());
        if (stats == null) return new float[]{100f, 100f};
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null) return new float[]{100f, 100f};
        return new float[]{health.get(), health.getMax()};
    }

    // ---- Reward function ----

    private float computeReward(CombatObservation prev, RLState currentState, float currentPlayerHp) {
        float reward = 0.05f;

        float hpLost = prev.playerHp - currentPlayerHp;
        if (hpLost > 0) {
            reward -= hpLost / 10.0f;
        }

        if (currentState.playerHpBucket() == 0 && prev.state.playerHpBucket() > 0) {
            reward -= 15.0f;
        }

        switch (prev.action) {
            case HEAL -> {
                if (prev.hpRatio() < 0.5f) reward += 3.0f;
                else if (prev.hpRatio() > 0.8f) reward -= 2.0f;
            }
            case ATTACK -> {
                if (prev.state.enemyCountBucket() == 0) reward -= 1.0f;
                // Penalty for sprint-aggro: attacking enemies that are far away
                if (prev.state.distanceBucket() == 2) reward -= 1.5f;
            }
            case FOLLOW -> {
                // Reward for staying close to the player during combat
                if (prev.state.distanceBucket() == 0) reward += 0.3f;
            }
            case FLEE -> {
                if (prev.state.playerHpBucket() == 0 && prev.state.enemyCountBucket() >= 2) reward += 2.0f;
                else if (prev.state.enemyCountBucket() == 0) reward -= 1.0f;
            }
            case TALK -> {
                if (prev.state.playerHpBucket() == 0 && !prev.state.hasCombatThreat()) reward += 2.0f;
                else if (prev.state.playerHpBucket() >= 3) reward -= 1.0f;
            }
            default -> { /* unreachable */ }
        }

        return reward;
    }
}
