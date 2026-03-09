package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import java.util.UUID;

/**
 * Funkcja mutująca (Setter) wpływająca na sposób generowania kontekstu (Promptu) przez system NpcBrain.
 * <p>
 * Zmienia nastawienie behawioralne oraz stosunek bytu do bodźców zewnętrznych
 * (np. wykrycia wrogów na radarze). Wymusza odświeżenie Mózgu NPC z pamięci podręcznej.
 * </p>
 */
public class StanceCommand implements AICommand {

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
        if (profile == null || profile.getNpcName() == null) {
            sender.sendMessage(Message.raw("[System] Najpierw musisz stworzyc kompana (!ai -create)."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Message.raw("[System] Uzycie: !ai -stance <PASYWNY|DEFENSYWNY|AGRESYWNY>"));
            return;
        }

        String newStance = args[1].trim().toUpperCase();
        if (newStance.equals("PASYWNY") || newStance.equals("DEFENSYWNY") || newStance.equals("AGRESYWNY")) {
            profile.setCombatStance(newStance);
            PlayerProfileManager.saveProfile(playerUuid, profile);

            // Wymusza przeliczenie promptu przy kolejnej wiadomości
            mod.getActiveCompanions().remove(playerUuid);

            sender.sendMessage(Message.raw("[System] Zmieniono nastawienie do walki na: " + newStance));
        } else {
            sender.sendMessage(Message.raw("[System] Dostepne nastawienia: PASYWNY, DEFENSYWNY, AGRESYWNY."));
        }
    }

    @Override
    public String getDescription() {
        return "Zmienia zachowanie AI na radarze (PASYWNY, DEFENSYWNY, AGRESYWNY).";
    }
}