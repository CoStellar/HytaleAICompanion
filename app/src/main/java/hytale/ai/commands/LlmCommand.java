package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.PlayerSettings;
import hytale.ai.config.PlayerSettingsManager;
import java.util.UUID;

/**
 * Moduł konfiguracyjny (Client-Side). Zapisuje wrażliwe dane wprowadzane przez użytkownika
 * w izolowanym portfelu pamięci (PlayerSettingsManager), chroniąc je przed cyklem życia NPC.
 * <p>
 * Pozwala na dynamiczną zmianę modelu językowego (LLM) "w locie", resetując cache instancji Mózgu.
 * </p>
 */
public class LlmCommand implements AICommand {

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        if (settings == null || !settings.hasKey()) {
            sender.sendMessage(Message.raw("[System] Uzyj najpierw !ai -setup <klucz>"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Message.raw("[System] Uzycie: !ai -llm <NazwaModelu>"));
            return;
        }

        String newModel = args[1].trim();
        settings.setAiModel(newModel);
        PlayerSettingsManager.saveSettings(playerUuid, settings);

        // Resetuje mózg, by przy kolejnym zapytaniu wczytał nowy model z ustawień
        mod.getActiveCompanions().remove(playerUuid);
        sender.sendMessage(Message.raw("[System] Zmieniono silnik AI na: " + newModel));
    }

    @Override
    public String getDescription() {
        return "Zmienia model LLM (np. gemini-2.5-flash).";
    }
}