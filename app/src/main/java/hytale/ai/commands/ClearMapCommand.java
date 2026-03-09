package hytale.ai.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import hytale.ai.HytaleAIMod;
import java.util.UUID;
/**
 * Narzędzie administracyjne ("Garbage Collector").
 * Skanuje świat gry i wymusza usunięcie (despawn) z systemu ECS wszelkich bytów
 * sztucznej inteligencji, które utraciły powiązanie (referencję UUID) ze swoim graczem.
 */
public class ClearMapCommand implements AICommand {
    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        Store<EntityStore> store = sender.getReference().getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();

        world.execute(() -> {
            int[] removedCount = {0};
            store.forEachChunk(NPCEntity.getComponentType(), (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    // Jeśli entity nie należy do aktywnej mapy kompanów - usuń je
                    if (!mod.getActiveCompanionEntities().containsValue(ref)) {
                        buffer.removeEntity(ref, RemoveReason.REMOVE);
                        removedCount[0]++;
                    }
                }
            });
            sender.sendMessage(Message.raw("[System] Wyczyszczono mape z " + removedCount[0] + " starych lub odczepionych zwierzakow."));
        });
    }

    @Override
    public String getDescription() {
        return "Debug: Czysci mape z NPC niezwiązanych z graczami.";
    }
}