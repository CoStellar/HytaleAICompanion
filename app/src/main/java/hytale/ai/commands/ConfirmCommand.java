package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import java.util.UUID;
/**
 * Komenda realizująca wzorzec projektowy "Safeguard" (Zabezpieczenie przed błędem użytkownika).
 * Wywołuje akcje destrukcyjne (np. usunięcie z pamięci) dodane wcześniej do kolejki oczekujących.
 */
public class ConfirmCommand implements AICommand {
    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        if (mod.getPendingConfirmations().containsKey(playerUuid)) {
            mod.getPendingConfirmations().get(playerUuid).run();
            mod.getPendingConfirmations().remove(playerUuid);
        } else {
            sender.sendMessage(Message.raw("[System] Nie masz zadnych akcji do potwierdzenia."));
        }
    }

    @Override
    public String getDescription() {
        return "Potwierdza wykonanie waznej akcji (np. usuniecie lub nadpisanie kompana).";
    }
}