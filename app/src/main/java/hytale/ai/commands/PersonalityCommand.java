package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import java.util.UUID;
/**
 * Funkcja mutująca (Setter) wpływająca na sposób generowania kontekstu (Promptu) przez system NpcBrain.
 * Zmienia nastawienie behawioralne oraz stosunek bytu do bodźców zewnętrznych (np. wykrycia wrogów na radarze).
 */
public class PersonalityCommand implements AICommand {
    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
        if (profile == null || profile.getNpcName() == null) {
            sender.sendMessage(Message.raw("[System] Najpierw musisz stworzyc kompana (!ai -create)."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Message.raw("[System] Uzycie: !ai -personality <Opis Twojego Kompana>"));
            return;
        }

        // Zlepia resztę argumentów w jedno zdanie
        StringBuilder personality = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            personality.append(args[i]).append(" ");
        }

        String newPersonality = personality.toString().trim();
        profile.setPersonality(newPersonality);
        PlayerProfileManager.saveProfile(playerUuid, profile);
        mod.getActiveCompanions().remove(playerUuid);
        sender.sendMessage(Message.raw("[System] Zmieniono osobowosc na: " + newPersonality));
    }

    @Override
    public String getDescription() {
        return "Pozwala recznie wpisac nowa osobowosc i cechy dla Twojego kompana.";
    }
}