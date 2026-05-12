package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerSettings;

import java.util.UUID;
/**
 * Funkcja diagnostyczna typu "Read-Only". Odpytuje systemowe struktury danych
 * (Mapy RAM oraz rejestry Assetów silnika Hytale) w celu wyświetlenia ich stanu graczowi.
 */
public class InfoCommand implements AICommand {
    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);

        boolean hasKey = settings != null && settings.hasKey();
        boolean hasCompanion = profile != null && profile.getNpcName() != null;

        sender.sendMessage(Message.raw("[Ustawienia Konta] Klucz: " + (hasKey ? "ZAPISANY" : "BRAK") + " | Silnik: " + (settings != null ? settings.getAiModel() : "BRAK")));

        if (hasCompanion) {
            String status = profile.isSummoned() ? "Przyzwany" : "Odeslany";
            sender.sendMessage(Message.raw("[Karta Kompana] Imie: " + profile.getNpcName() + " | Stan: " + status + " | Wyglad: " + profile.getInGameModel()));
        } else {
            sender.sendMessage(Message.raw("[Karta Kompana] Brak stworzonego kompana."));
        }

        boolean rlEnabled = settings != null && settings.isRlEnabled();
        hytale.ai.rl.QLearningAgent playerAgent = mod.getAgentForPlayer(playerUuid);
        if (playerAgent != null) {
            sender.sendMessage(Message.translation("ai_companion.info.rl")
                    .param("status", rlEnabled ? "WL" : "WYL")
                    .param("decisions", playerAgent.getTotalDecisions())
                    .param("epsilon", String.format("%.3f", playerAgent.getEpsilon())));
        }
    }

    @Override
    public String getDescription() {
        return "Wyswietla informacje o Twoim koncie i statystykach kompana.";
    }
}