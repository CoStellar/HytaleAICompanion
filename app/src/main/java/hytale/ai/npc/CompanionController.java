package hytale.ai.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import hytale.ai.config.PlayerSettings;
import hytale.ai.config.TalkLines;
import hytale.ai.rl.QLearningAgent;
import hytale.ai.rl.RLAction;
import hytale.ai.rl.RLState;
import hytale.ai.ui.CompanionStatusHud;

import java.util.UUID;
import java.util.logging.Logger;

public class CompanionController {

    private static final Logger LOGGER = Logger.getLogger("AI-NPC");

    private final Ref<EntityStore> npcRef;
    private final Ref<EntityStore> playerRef;
    private final Store<EntityStore> store;
    private final UUID playerUuid;
    private final QLearningAgent rlAgent;
    private final String companionName;
    private final HytaleAIMod mod;

    private String lastState = "Idle";

    // Behavioral mode flags set by LLM actions
    private boolean isGuarding  = false;
    private boolean isProtecting = false;
    private boolean isStopped   = false;

    // Last RL state observed by CompanionCombatSystem (updated each combat tick)
    private RLState lastKnownState = new RLState(3, 0, 2);

    // Remaining combat ticks where LLM has priority over the RL combat system.
    // Set when LLM issues an explicit action or feedback — prevents double-penalisation
    // and RL immediately overriding the LLM-chosen state.
    private int llmControlLock = 0;

    public CompanionController(Ref<EntityStore> npcRef, Ref<EntityStore> playerRef,
                                Store<EntityStore> store, UUID playerUuid,
                                QLearningAgent rlAgent, String companionName, HytaleAIMod mod) {
        this.npcRef = npcRef;
        this.playerRef = playerRef;
        this.store = store;
        this.playerUuid = playerUuid;
        this.rlAgent = rlAgent;
        this.mod = mod;
        this.companionName = companionName != null ? companionName : "Kompan";
    }

    /** Called by NpcBrain after receiving an LLM response. */
    public void handleAiDecision(AiActionResponse response) {
        String action = response.action();
        applyStateInternal(action);
        if ("HEAL".equalsIgnoreCase(action)) {
            performHealing();
        }

        // Any explicit non-idle LLM action locks out the RL combat system for ~10s (5 ticks)
        if (action != null && !action.equalsIgnoreCase("FOLLOW") && !action.equalsIgnoreCase("NONE")) {
            llmControlLock = 5;
        }

        if (response.feedback_type() != null && response.feedback_action() != null) {
            applyLlmFeedback(response);
        }

        if (response.set_stance() != null && !response.set_stance().isEmpty()
                && !"null".equalsIgnoreCase(response.set_stance())) {
            applyStanceChange(response.set_stance());
        }
    }

    /** Called by CompanionCombatSystem with a normalized state name (e.g. "Attack"). */
    public void applyRLAction(String stateName) {
        String previousState = lastState;
        applyStateInternal(stateName.toUpperCase());
        sendVocalReaction(stateName, previousState);
    }

    /** Updated each combat tick so feedback can reference a meaningful state. */
    public void setLastKnownState(RLState state) {
        this.lastKnownState = state;
    }

    public boolean isGuarding()   { return isGuarding; }
    public boolean isProtecting() { return isProtecting; }
    public boolean isStopped()    { return isStopped; }

    private void clearBehaviorFlags() {
        isGuarding   = false;
        isProtecting = false;
        isStopped    = false;
    }

    private void applyStateInternal(String action) {
        if (!npcRef.isValid()) return;
        NPCEntity npcComponent = store.getComponent(npcRef, NPCEntity.getComponentType());

        if (npcComponent != null && npcComponent.getRole() != null) {
            String targetState = switch (action.toUpperCase()) {
                case "FOLLOW"  -> { clearBehaviorFlags(); yield "Follow"; }
                case "ATTACK"  -> { clearBehaviorFlags(); yield "Attack"; }
                case "FLEE"    -> { clearBehaviorFlags(); yield "Flee"; }
                case "HEAL"    -> { clearBehaviorFlags(); yield "Heal"; }
                case "TALK"    -> { clearBehaviorFlags(); yield "Talk"; }
                case "STAY", "NONE" -> { clearBehaviorFlags(); yield "Idle"; }
                case "STOP"    -> { clearBehaviorFlags(); isStopped   = true;  yield "Idle"; }
                case "GUARD"   -> { clearBehaviorFlags(); isGuarding  = true;  yield "Follow"; }
                case "PROTECT" -> { clearBehaviorFlags(); isProtecting = true; yield "Follow"; }
                default        -> { clearBehaviorFlags(); yield "Idle"; }
            };

            Role role = npcComponent.getRole();
            if (role.getStateSupport() != null) {
                role.getStateSupport().setState(npcRef, targetState, null, store);
                lastState = targetState;
                LOGGER.info("[AI-NPC] Stan kompana: " + targetState
                        + (isGuarding ? " [GUARD]" : isProtecting ? " [PROTECT]" : isStopped ? " [STOP]" : ""));
                notifyHudStateChange();
            }
        }
    }

    private void applyLlmFeedback(AiActionResponse response) {
        try {
            RLAction feedbackAction = RLAction.valueOf(response.feedback_action().toUpperCase());
            float sign = "POSITIVE".equalsIgnoreCase(response.feedback_type()) ? 1.0f : -1.0f;
            float reward = sign * response.feedback_strength();
            rlAgent.applyManualReward(lastKnownState, feedbackAction, reward);

            // Freeze RL auto-updates for 2 ticks so feedback isn't immediately
            // overwritten by the Bellman update on the same action
            llmControlLock = Math.max(llmControlLock, 2);

            sendMessageToOwner(Message.translation("ai_companion.feedback.remembered")
                    .param("name", companionName));
            LOGGER.info("[AI-NPC] Feedback gracza: " + response.feedback_type()
                    + " na " + feedbackAction + " siła=" + reward);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("[AI-NPC] Nieznana akcja feedbacku: " + response.feedback_action());
        }
    }

    private void applyStanceChange(String newStance) {
        String normalized = newStance.trim().toUpperCase();
        if (!normalized.equals("PASYWNY") && !normalized.equals("DEFENSYWNY") && !normalized.equals("AGRESYWNY")) return;

        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
        if (profile == null) return;

        profile.setCombatStance(normalized);
        PlayerProfileManager.saveProfile(playerUuid, profile);
        mod.getActiveCompanions().remove(playerUuid); // force NpcBrain rebuild with new stance

        sendMessageToOwner(Message.raw("[System] " + companionName + " zmienil nastawienie: " + normalized));
        LOGGER.info("[AI-NPC] Zmieniono nastawienie kompana na: " + normalized);
        notifyHudStateChange();
    }

    /** Returns true if the LLM control lock is still active (RL combat system should yield). */
    public boolean hasLlmControlLock() { return llmControlLock > 0; }

    /** Decrements the LLM control lock counter by one combat tick. */
    public void decrementLlmControlLock() { if (llmControlLock > 0) llmControlLock--; }

    private void sendVocalReaction(String newState, String previousState) {
        if (newState.equals(previousState)) return;

        String line = switch (newState.toUpperCase()) {
            case "ATTACK" -> TalkLines.get().getRandom("battle_cry");
            case "FLEE"   -> TalkLines.get().getRandom("retreat");
            case "HEAL"   -> TalkLines.get().getRandom("healing_companion");
            default -> null;
        };
        if (line == null) return;
        sendMessageToOwner(Message.raw(line));
    }

    private void sendMessageToOwner(Message message) {
        World world = store.getExternalData().getWorld();
        for (PlayerRef pRef : world.getPlayerRefs()) {
            if (pRef.getUuid().equals(playerUuid)) {
                pRef.sendMessage(message);
                return;
            }
        }
    }

    private void performHealing() {
        if (playerRef == null || !playerRef.isValid()) return;
        EntityStatMap stats = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (stats == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = stats.get(healthIndex);
        if (healthStat == null) return;

        float current = healthStat.get();
        float max = healthStat.getMax();
        if (current < max) {
            stats.setStatValue(healthIndex, Math.min(max, current + 20.0f));
            LOGGER.info("[AI-NPC] Kompan uleczyl gracza. HP: " + current + " -> " + Math.min(max, current + 20.0f));
        }
    }

    public Ref<EntityStore> getNpcRef() { return npcRef; }
    public String getLastState() { return lastState; }

    public void refreshHudHp() {
        if (mod == null) return;
        CompanionStatusHud hud = mod.getPlayerHud(playerUuid);
        if (hud == null) return;
        hud.updateHp(getNpcHpPercent());
    }

    private void notifyHudStateChange() {
        if (mod == null) return;
        CompanionStatusHud hud = mod.getPlayerHud(playerUuid);
        if (hud == null) return;

        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
        PlayerSettings settings  = mod.getPlayerSettingsMap().get(playerUuid);
        String stance    = (profile != null) ? profile.getCombatStance() : "?";
        boolean rlEnabled = (settings != null) && settings.isRlEnabled();
        float hpPercent  = getNpcHpPercent();

        hud.updateValues(lastState, stance, rlEnabled, hpPercent);
    }

    private float getNpcHpPercent() {
        if (!npcRef.isValid()) return -1f;
        EntityStatMap stats = store.getComponent(npcRef, EntityStatMap.getComponentType());
        if (stats == null) return -1f;
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.getMax() <= 0) return -1f;
        return Math.max(0f, Math.min(1f, health.get() / health.getMax()));
    }
}
