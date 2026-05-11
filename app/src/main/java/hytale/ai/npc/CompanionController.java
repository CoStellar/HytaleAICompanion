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
import hytale.ai.config.TalkLines;

import java.util.UUID;
import java.util.logging.Logger;

public class CompanionController {

    private static final Logger LOGGER = Logger.getLogger("AI-NPC");

    private final Ref<EntityStore> npcRef;
    private final Ref<EntityStore> playerRef;
    private final Store<EntityStore> store;
    private final UUID playerUuid;

    private String lastState = "Idle";

    public CompanionController(Ref<EntityStore> npcRef, Ref<EntityStore> playerRef,
                                Store<EntityStore> store, UUID playerUuid) {
        this.npcRef = npcRef;
        this.playerRef = playerRef;
        this.store = store;
        this.playerUuid = playerUuid;
    }

    /** Zpasowanie z LLM-a (np. "ATTACK" -> "Attack") */
    public void handleAiDecision(AiActionResponse response) {
        String action = response.action();
        applyStateInternal(action);
        if ("HEAL".equalsIgnoreCase(action)) {
            performHealing();
        }
    }

    /** Wywoływane przez system RL z normalną nazwą stanu (np. "Attack") */
    public void applyRLAction(String stateName) {
        String previousState = lastState;
        applyStateInternal(stateName.toUpperCase());
        sendVocalReaction(stateName, previousState);
    }

    private void applyStateInternal(String action) {
        if (!npcRef.isValid()) return;
        NPCEntity npcComponent = store.getComponent(npcRef, NPCEntity.getComponentType());

        if (npcComponent != null && npcComponent.getRole() != null) {
            String targetState = switch (action.toUpperCase()) {
                case "FOLLOW" -> "Follow";
                case "ATTACK" -> "Attack";
                case "FLEE"   -> "Flee";
                case "HEAL"   -> "Heal";
                case "TALK"   -> "Talk";
                case "STAY", "NONE" -> "Idle";
                default -> "Idle";
            };

            Role role = npcComponent.getRole();
            if (role.getStateSupport() != null) {
                role.getStateSupport().setState(npcRef, targetState, null, store);
                lastState = targetState;
                LOGGER.info("[AI-NPC] Stan kompana: " + targetState);
            }
        }
    }

    /** Wysyła skryptowany okrzyk przy zmianie stanu bojowego. */
    private void sendVocalReaction(String newState, String previousState) {
        if (newState.equals(previousState)) return;

        String line = switch (newState.toUpperCase()) {
            case "ATTACK" -> TalkLines.get().getRandom("battle_cry");
            case "FLEE"   -> TalkLines.get().getRandom("retreat");
            case "HEAL"   -> TalkLines.get().getRandom("healing_companion");
            default -> null;
        };
        if (line == null) return;

        sendMessageToOwner(line);
    }

    private void sendMessageToOwner(String line) {
        if (line == null || line.isEmpty()) return;
        World world = store.getExternalData().getWorld();
        for (PlayerRef pRef : world.getPlayerRefs()) {
            if (pRef.getUuid().equals(playerUuid)) {
                pRef.sendMessage(Message.raw(line));
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
}
