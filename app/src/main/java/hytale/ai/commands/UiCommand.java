package hytale.ai.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import hytale.ai.HytaleAIMod;
import hytale.ai.ui.CompanionControlPage;

import java.util.UUID;

public class UiCommand implements AICommand {

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        Ref<EntityStore> playerEntityRef = sender.getReference();
        if (playerEntityRef == null) {
            sender.sendMessage(Message.translation("ai_companion.command.ui.noRef"));
            return;
        }
        Store<EntityStore> store = playerEntityRef.getStore();
        if (store == null) return;

        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (!playerEntityRef.isValid()) return;
            com.hypixel.hytale.server.core.entity.entities.Player player =
                    store.getComponent(playerEntityRef, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(playerEntityRef, store,
                    new CompanionControlPage(sender, playerUuid, mod));
        });
    }

    @Override
    public String getDescription() {
        return "Otwiera graficzny panel sterowania kompanem.";
    }
}
