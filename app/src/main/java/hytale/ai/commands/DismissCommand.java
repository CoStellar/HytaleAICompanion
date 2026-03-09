package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import java.util.UUID;

/**
 * Komenda realizująca usunięcie bytu (NPC) z pamięci serwera.
 * <p>
 * Odpowiada za permanentne wyczyszczenie profilu kompana (w tym historii rozmów),
 * zachowując jednocześnie bezpieczny portfel z ustawieniami i kluczem API gracza.
 * </p>
 */
public class DismissCommand implements AICommand {

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
        if (profile == null || profile.getNpcName() == null) {
            sender.sendMessage(Message.raw("[System] Nie posiadasz zadnego kompana."));
            return;
        }

        sender.sendMessage(Message.raw("[System] Czy usunac kompana? (Twoj klucz API pozostanie bezpieczny!). Wpisz !ai -confirm"));
        mod.getPendingConfirmations().put(playerUuid, () -> {
            profile.setNpcName(null);
            profile.setInGameModel(null);
            profile.setPersonality(null);
            profile.setSummoned(false);

            PlayerProfileManager.saveProfile(playerUuid, profile);
            mod.getActiveCompanions().remove(playerUuid);
            mod.despawnCompanion(playerUuid, sender.getReference().getStore());
            sender.sendMessage(Message.raw("[System] Zwierzak usuniety. Klucz API i ustawienia zachowane."));
        });
    }

    @Override
    public String getDescription() {
        return "Trwale usuwa obecnego kompana z pamieci.";
    }
}