package hytale.ai.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

import java.util.logging.Logger;

public class CompanionController {
    private static final Logger LOGGER = Logger.getLogger("AI-NPC");

    private final Ref<EntityStore> npcRef;
    private final Ref<EntityStore> playerRef; // Dodana referencja do gracza, by móc go leczyć
    private final Store<EntityStore> store;

    // Aktualizacja konstruktora - teraz przyjmuje też playerRef
    public CompanionController(Ref<EntityStore> npcRef, Ref<EntityStore> playerRef, Store<EntityStore> store) {
        this.npcRef = npcRef;
        this.playerRef = playerRef;
        this.store = store;
    }

    public void handleAiDecision(AiActionResponse response) {
        String action = response.action();

        // Zastosowanie natywnego stanu
        applyState(action);

        // Dodatkowe, fizyczne efekty akcji z poziomu Javy
        if ("HEAL".equalsIgnoreCase(action)) {
            performHealing();
        }
    }

    private void applyState(String action) {
        NPCEntity npcComponent = store.getComponent(npcRef, NPCEntity.getComponentType());

        if (npcComponent != null && npcComponent.getRole() != null) {
            // Mapowanie wyjścia LLM na stany zdefiniowane w ai_companion.json
            String targetState = switch (action.toUpperCase()) {
                case "FOLLOW" -> "Follow";
                case "ATTACK" -> "Attack";
                case "FLEE" -> "Flee";
                case "HEAL" -> "Heal";
                case "STAY", "NONE" -> "Idle"; // Zatrzymuje bota w miejscu
                default -> "Idle"; // Bezpieczny fallback
            };

            Role role = npcComponent.getRole();
            if (role.getStateSupport() != null) {
                role.getStateSupport().setState(npcRef, targetState, null, store);
                LOGGER.info("[AI-NPC] Silnik Hytale przelaczyl bota w stan: " + targetState);
            }
        }
    }

    private void performHealing() {
        if (playerRef != null && playerRef.isValid()) {
            EntityStatMap stats = store.getComponent(playerRef, EntityStatMap.getComponentType());
            if (stats != null) {
                int healthIndex = DefaultEntityStatTypes.getHealth();
                EntityStatValue healthStat = stats.get(healthIndex);

                if (healthStat != null) {
                    float currentHealth = healthStat.get();
                    float maxHealth = healthStat.getMax();

                    if (currentHealth < maxHealth) {
                        // Leczmy gracza o 20 punktów (wymuszenie float)
                        float newHealth = Math.min(maxHealth, currentHealth + 20.0f);

                        // ROZWIĄZANIE: Używamy metody z EntityStatMap!
                        stats.setStatValue(healthIndex, newHealth);

                        LOGGER.info("[AI-NPC] Spidi uleczyl gracza! HP zmienione z " + currentHealth + " na " + newHealth);
                    } else {
                        LOGGER.info("[AI-NPC] Gracz ma już pełne zdrowie, leczenie pominięte.");
                    }
                }
            }
        }
    }

    public Ref<EntityStore> getNpcRef() {
        return npcRef;
    }
}