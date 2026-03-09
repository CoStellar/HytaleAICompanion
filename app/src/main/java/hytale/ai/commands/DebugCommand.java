package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.PlayerSettings;
import hytale.ai.config.PlayerSettingsManager;
import java.util.UUID;

/**
 * Komenda developerska pozwalająca na przełączanie jawności promptów.
 * Jeśli włączona, każdy pełny kontekst wysyłany do AI pojawi się w logach serwera Hytale.
 */
public class DebugCommand implements AICommand {
    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        PlayerSettings settings = mod.getPlayerSettingsMap().computeIfAbsent(playerUuid, k -> new PlayerSettings());

        boolean currentMode = settings.isDebugMode();
        settings.setDebugMode(!currentMode); // Odwracamy przełącznik

        PlayerSettingsManager.saveSettings(playerUuid, settings);

        String state = !currentMode ? "WLACZONE" : "WYLACZONE";
        sender.sendMessage(Message.raw("[System] Logowanie pełnych promptow (Debug) zostalo: " + state));
        if (!currentMode) {
            sender.sendMessage(Message.raw("[System] Otwworz konsole serwera, aby analizowac kontekst wysylany do LLM."));
        }
    }

    @Override
    public String getDescription() {
        return "Wlacza/wylacza podglad pelnej tresci promptow wysylanych do AI w logach serwera.";
    }
}