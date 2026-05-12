package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.PlayerSettings;
import hytale.ai.config.PlayerSettingsManager;

import java.util.UUID;

public class RlCommand implements AICommand {

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        if (settings == null) {
            sender.sendMessage(Message.translation("ai_companion.command.rl.noSettings"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Message.translation("ai_companion.command.rl.usage"));
            return;
        }

        String arg = args[1].trim().toLowerCase();
        if (arg.equals("on")) {
            settings.setRlEnabled(true);
            PlayerSettingsManager.saveSettings(playerUuid, settings);
            sender.sendMessage(Message.translation("ai_companion.command.rl.enabled"));
        } else if (arg.equals("off")) {
            settings.setRlEnabled(false);
            PlayerSettingsManager.saveSettings(playerUuid, settings);
            sender.sendMessage(Message.translation("ai_companion.command.rl.disabled"));
        } else {
            sender.sendMessage(Message.translation("ai_companion.command.rl.usage"));
        }
    }

    @Override
    public String getDescription() {
        return "Wlacza lub wylacza uczenie Q-Learning (!ai -rl on/off).";
    }
}
