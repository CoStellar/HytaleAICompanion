package hytale.ai.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Klasa narzędziowa pełniąca rolę zmysłów sztucznej inteligencji.
 */
public class WorldContextBuilder {

    private WorldContextBuilder() {}

    public static String buildContext(Ref<EntityStore> playerRef, Ref<EntityStore> companionRef) {
        StringBuilder context = new StringBuilder();
        context.append("--- KONTEKST ŚRODOWISKOWY W GRZE ---\n");

        if (playerRef == null || !playerRef.isValid()) {
            return context.append("Brak danych o graczu.\n\n").toString();
        }

        Store<EntityStore> store = playerRef.getStore();
        World world = store.getExternalData().getWorld();
        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());

        // ==========================================
        // 1. ZDROWIE GRACZA I KOMPANA
        // ==========================================
        EntityStatMap playerStats = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (playerStats != null) {
            EntityStatValue hpStat = playerStats.get(DefaultEntityStatTypes.getHealth());
            if (hpStat != null) {
                int hp = (int) hpStat.get();
                int maxHp = (int) hpStat.getMax();
                context.append("Zdrowie Gracza: ").append(hp).append("/").append(maxHp);
                if (hp < maxHp / 3) context.append(" [UWAGA: GRACZ CIĘŻKO RANNY!]\n");
                else context.append("\n");
            }
        }

        if (companionRef != null && companionRef.isValid()) {
            EntityStatMap compStats = store.getComponent(companionRef, EntityStatMap.getComponentType());
            if (compStats != null) {
                EntityStatValue compHpStat = compStats.get(DefaultEntityStatTypes.getHealth());
                if (compHpStat != null) {
                    int compHp = (int) compHpStat.get();
                    int compMaxHp = (int) compHpStat.getMax();
                    context.append("Twoje Zdrowie (AI): ").append(compHp).append("/").append(compMaxHp);
                    if (compHp < compMaxHp / 3) context.append(" [UWAGA: JESTEŚ BLISKI ŚMIERCI!]\n");
                    else context.append("\n");
                }
            }
        }

        // ==========================================
        // 2. EKWIPUNEK I PANCERZ GRACZA
        // ==========================================
        Player playerComponent = store.getComponent(playerRef, Player.getComponentType());
        if (playerComponent != null && playerComponent.getInventory() != null) {
            Inventory inv = playerComponent.getInventory();

            // Trzymany przedmiot
            ItemStack handItem = inv.getItemInHand();
            context.append("Gracz trzyma w dłoni: ").append(handItem != null && !handItem.isEmpty() ? handItem.getItemId() : "Nic").append("\n");

            // Szczegółowy pancerz
            context.append("Ubiór Gracza: ");
            ItemContainer armor = inv.getArmor();
            boolean hasAnyArmor = false;
            if (armor != null) {
                for (short i = 0; i < armor.getCapacity(); i++) {
                    ItemStack piece = armor.getItemStack(i);
                    if (piece != null && !piece.isEmpty()) {
                        context.append("[").append(piece.getItemId()).append("] ");
                        hasAnyArmor = true;
                    }
                }
            }
            if (!hasAnyArmor) context.append("Brak (Bezbronny/Nagi)");
            context.append("\n");

            // Agregacja całego plecaka (Storage + Hotbar)
            context.append("Zawartość plecaka: ");
            ItemContainer mainInv = inv.getCombinedHotbarFirst(); // Używamy natywnej metody Hytale!
            if (mainInv != null) {
                Map<String, Integer> aggregatedItems = new HashMap<>();
                for (short i = 0; i < mainInv.getCapacity(); i++) {
                    ItemStack item = mainInv.getItemStack(i);
                    if (item != null && !item.isEmpty() && item.getItemId() != null) {
                        int amount = 1;
                        try { amount = item.getQuantity(); } catch (Exception ignored) {} // Zmieniono na getQuantity!
                        aggregatedItems.put(item.getItemId(), aggregatedItems.getOrDefault(item.getItemId(), 0) + amount);
                    }
                }

                if (aggregatedItems.isEmpty()) {
                    context.append("Pusty\n");
                } else {
                    StringBuilder invString = new StringBuilder();
                    for (Map.Entry<String, Integer> entry : aggregatedItems.entrySet()) {
                        invString.append(entry.getValue()).append("x ").append(entry.getKey()).append(", ");
                    }
                    context.append(invString.substring(0, invString.length() - 2)).append("\n");
                }
            }
        }

        // ==========================================
        // 3. CZAS I ŚRODOWISKO
        // ==========================================
        try {
            WorldTimeResource timeResource = store.getResource(TimeModule.get().getWorldTimeResourceType());
            if (timeResource != null) {
                LocalDateTime dateTime = timeResource.getGameDateTime();
                context.append("Czas w grze: ").append(String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute())).append("\n");
            }
        } catch (Exception ignored) {}

        // ==========================================
        // 4. MICRO-RADAR BLOKÓW INTERAKTYWNYCH (Promień: 3 bloki)
        // ==========================================
        if (playerTransform != null) {
            int px = (int) playerTransform.getPosition().getX();
            int py = (int) playerTransform.getPosition().getY();
            int pz = (int) playerTransform.getPosition().getZ();

            Map<Integer, Integer> interestingBlocks = new HashMap<>();

            for (int x = px - 3; x <= px + 3; x++) {
                for (int z = pz - 3; z <= pz + 3; z++) {
                    long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z);
                    com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);

                    if (chunk != null) {
                        int localX = x & 31;
                        int localZ = z & 31;
                        for (int y = py - 3; y <= py + 3; y++) {
                            if (y < 0 || y > 255) continue;

                            int blockId = chunk.getBlock(localX, y, localZ);
                            if (blockId > 10) {
                                interestingBlocks.put(blockId, interestingBlocks.getOrDefault(blockId, 0) + 1);
                            }
                        }
                    }
                }
            }

            if (!interestingBlocks.isEmpty()) {
                context.append("Ciekawe bloki wokół ciebie (ID): ");
                for (Map.Entry<Integer, Integer> entry : interestingBlocks.entrySet()) {
                    context.append(entry.getValue()).append("x [ID:").append(entry.getKey()).append("] ");
                }
                context.append("\n");
            }
        }

        // ==========================================
        // 5. RADAR ISTOT (Z wykorzystaniem pozycji Kompana)
        // ==========================================
        TransformComponent radarOrigin = playerTransform;
        double radarRadiusSq = 225.0;
        String originName = "gracza";

        if (companionRef != null && companionRef.isValid()) {
            TransformComponent compTransform = store.getComponent(companionRef, TransformComponent.getComponentType());
            if (compTransform != null) {
                radarOrigin = compTransform;
                radarRadiusSq = 900.0;
                originName = "ciebie (kompana)";
            }
        }

        context.append("Istoty w pobliżu ").append(originName).append(" (Promień: ").append((int)Math.sqrt(radarRadiusSq)).append(" blk):\n");
        Map<String, Integer> nearbyEntities = new HashMap<>();

        if (radarOrigin != null) {
            final TransformComponent finalOrigin = radarOrigin;
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
                        double distSq = (dx * dx) + (dy * dy) + (dz * dz);

                        if (distSq <= finalRadiusSq) {
                            String entityName = "Nieznana_istota";
                            try {
                                ModelComponent modelComp = store.getComponent(ref, ModelComponent.getComponentType());
                                if (modelComp != null && modelComp.getModel() != null) {
                                    String assetId = modelComp.getModel().getModelAssetId();
                                    if (assetId != null && !assetId.isEmpty()) entityName = assetId;
                                }
                            } catch (Exception ignored) {}
                            nearbyEntities.put(entityName, nearbyEntities.getOrDefault(entityName, 0) + 1);
                        }
                    }
                }
            });
        }

        if (nearbyEntities.isEmpty()) {
            context.append("- Czysto. Brak istot.\n");
        } else {
            for (Map.Entry<String, Integer> entry : nearbyEntities.entrySet()) {
                context.append("- ").append(entry.getValue()).append("x ").append(entry.getKey()).append("\n");
            }
        }

        context.append("\n");
        return context.toString();
    }
}