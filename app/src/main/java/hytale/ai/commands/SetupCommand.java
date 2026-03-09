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
 */
public class SetupCommand implements AICommand {
    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        if (args.length < 2) {
            sender.sendMessage(Message.raw("[System] Uzycie: !ai -setup <TwójKluczAPI>"));
            return;
        }
        String newKey = args[1].trim();
        PlayerSettings settings = mod.getPlayerSettingsMap().computeIfAbsent(playerUuid, k -> new PlayerSettings());

        settings.setApiKey(newKey);
        PlayerSettingsManager.saveSettings(playerUuid, settings);
        sender.sendMessage(Message.raw("[System] Zapisano klucz API w twoim prywatnym portfelu."));
    }

    @Override
    public String getDescription() {
        return "Zapisuje Twoj prywatny klucz API Google Gemini.";
    }
}