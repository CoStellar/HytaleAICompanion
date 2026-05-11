package hytale.ai.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;

import java.time.LocalDateTime;

/**
 * Klasa narzędziowa pełniąca rolę "zmysłów" sztucznej inteligencji.
 */
public class WorldContextBuilder {

    private WorldContextBuilder() {}

    /**
     * Buduje dynamiczny kontekst środowiskowy z uwzględnieniem fizycznej lokalizacji kompana.
     * @param playerRef Referencja do gracza.
     * @param companionRef Referencja do fizycznego ciała kompana (może być null, jeśli jest schowany).
     * @return Sformatowany tekst (String).
     */
    public static String buildContext(Ref<EntityStore> playerRef, Ref<EntityStore> companionRef) {
        StringBuilder context = new StringBuilder();
        context.append("--- KONTEKST ŚRODOWISKOWY W GRZE (Zmienia się dynamicznie) ---\n");

        if (playerRef == null || !playerRef.isValid()) {
            return context.append("Brak danych o graczu.\n\n").toString();
        }

        Store<EntityStore> store = playerRef.getStore();
        // POPRAWKA: Usunięto zbędne rzutowanie
        World world = store.getExternalData().getWorld();
        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());

        // 1. Zdrowie
        EntityStatMap stats = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (stats != null) {
            int healthIndex = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthStat = stats.get(healthIndex);
            if (healthStat != null) {
                int currentHp = (int) healthStat.get();
                int maxHp = (int) healthStat.getMax();
                context.append("Punkty Zdrowia Gracza (HP): ").append(currentHp).append(" / ").append(maxHp);
                if (currentHp < maxHp / 3) context.append(" [UWAGA: Gracz jest ciężko ranny!]\n");
                else context.append("\n");
            }
            int oxygenIndex = DefaultEntityStatTypes.getOxygen();
            EntityStatValue oxygenStat = stats.get(oxygenIndex);
            if (oxygenStat != null && oxygenStat.get() < oxygenStat.getMax()) {
                context.append(" [UWAGA: Gracz traci tlen, dusi się pod wodą!]\n");
            }
        }

        // 2. Ekwipunek
        Player playerComponent = store.getComponent(playerRef, Player.getComponentType());
        if (playerComponent != null) {
            Inventory inventory = playerComponent.getInventory();
            if (inventory != null) {
                ItemStack itemInHand = inventory.getItemInHand();
                if (itemInHand != null && !itemInHand.isEmpty()) {
                    context.append("Gracz trzyma w dłoni: ").append(itemInHand.getItemId()).append("\n");
                } else context.append("Gracz ma puste dłonie.\n");

                com.hypixel.hytale.server.core.inventory.container.ItemContainer armorContainer = inventory.getArmor();
                boolean isArmored = false;
                if (armorContainer != null) {
                    for (short i = 0; i < armorContainer.getCapacity(); i++) {
                        ItemStack armorPiece = armorContainer.getItemStack(i);
                        if (armorPiece != null && !armorPiece.isEmpty()) {
                            isArmored = true;
                            break;
                        }
                    }
                }
                context.append(isArmored ? "Gracz jest opancerzony.\n" : "Gracz nie ma pancerza (bezbronny!).\n");
            }
        }

        // 3. Czas
        try {
            WorldTimeResource timeResource = store.getResource(TimeModule.get().getWorldTimeResourceType());
            if (timeResource != null) {
                LocalDateTime dateTime = timeResource.getGameDateTime();
                String timeOfDay = "Noc";
                if (dateTime.getHour() >= 6 && dateTime.getHour() < 10) timeOfDay = "Poranek";
                else if (dateTime.getHour() >= 10 && dateTime.getHour() < 17) timeOfDay = "Środek Dnia";
                else if (dateTime.getHour() >= 17 && dateTime.getHour() < 20) timeOfDay = "Wieczór / Zachód Słońca";
                context.append("Czas na świecie: ").append(String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute())).append(" (").append(timeOfDay).append(")\n");
            }
        } catch (Exception ignored) {
        }

        // 4 & 5. ZAAWANSOWANY RADAR
        TransformComponent radarOriginTransform = playerTransform;
        double radarRadiusSq = 225.0;
        String originName = "gracza";

        if (companionRef != null && companionRef.isValid()) {
            TransformComponent compTransform = store.getComponent(companionRef, TransformComponent.getComponentType());
            if (compTransform != null) {
                radarOriginTransform = compTransform;
                radarRadiusSq = 900.0; // 30^2
                originName = "Ciebie (kompana)";
            }
        }

        context.append("Istoty w pobliżu ").append(originName).append(" (Zasięg radaru: ").append((int) Math.sqrt(radarRadiusSq)).append(" bloków):\n");
        java.util.Map<String, Integer> nearbyEntities = new java.util.HashMap<>();

        if (radarOriginTransform != null) {
            final TransformComponent finalOrigin = radarOriginTransform;
            final double finalRadiusSq = radarRadiusSq;

            store.forEachChunk(NPCEntity.getComponentType(), (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);

                    if (ref.equals(playerRef)) continue;
                    if (companionRef != null && ref.equals(companionRef)) continue;

                    TransformComponent npcTransform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (npcTransform != null) {
                        double dx = npcTransform.getPosition().getX() - finalOrigin.getPosition().getX();
                        double dy = npcTransform.getPosition().getY() - finalOrigin.getPosition().getY();
                        double dz = npcTransform.getPosition().getZ() - finalOrigin.getPosition().getZ();
                        double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);

                        if (distanceSq <= finalRadiusSq) {
                            String entityName = "Nieznana_istota";
                            try {
                                ModelComponent modelComp = store.getComponent(ref, ModelComponent.getComponentType());
                                if (modelComp != null && modelComp.getModel() != null) {
                                    String assetId = modelComp.getModel().getModelAssetId();
                                    if (assetId != null && !assetId.isEmpty()) entityName = assetId;
                                }
                            } catch (Exception ignored) {
                            }
                            nearbyEntities.put(entityName, nearbyEntities.getOrDefault(entityName, 0) + 1);
                        }
                    }
                }
            });
        }
        String engineState = "Nieznany";
        if (companionRef != null && companionRef.isValid()) {
            NPCEntity npcComponent = store.getComponent(companionRef, NPCEntity.getComponentType());
            if (npcComponent != null && npcComponent.getRole() != null && npcComponent.getRole().getStateSupport() != null) {
                try {
                    engineState = npcComponent.getRole().getStateSupport().getStateName();
                } catch (Exception e) {
                    engineState = "Blad_Odczytu";
                }
            }
        }

        context.append("Twoj obecny fizyczny stan w silniku gry to: ").append(engineState).append("\n");

        if (nearbyEntities.isEmpty()) {
            context.append("- Czysto. W pobliżu nie ma żadnych istot.\n");
        } else {
            for (java.util.Map.Entry<String, Integer> entry : nearbyEntities.entrySet()) {
                context.append("- ").append(entry.getValue()).append("x ").append(entry.getKey()).append("\n");
            }
        }

        context.append("\n");
        return context.toString();
    }
}