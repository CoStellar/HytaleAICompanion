package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;

import java.util.UUID;

/**
 * Komenda obsługująca fizyczne spawnowanie i odspawnowanie modelu AI w świecie gry.
 * Zawiera logikę kar czasowych (Cooldown) po śmierci kompana.
 */
public class SummonCommand implements AICommand {

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        // Zamiast trzymać zmienne w jednym pliku, pobieramy je dynamicznie
        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);

        if (profile == null || profile.getNpcName() == null) {
            sender.sendMessage(Message.raw("[System] Najpierw musisz stworzyc kompana (!ai -create)."));
            return;
        }

        long cooldownMs = 3 * 60 * 1000;
        long timeSinceDeath = System.currentTimeMillis() - profile.getDeathTimestamp();

        // Ochrona przed spamowaniem spawnu po zgonie
        if (!profile.isSummoned() && timeSinceDeath < cooldownMs) {
            long secondsLeft = (cooldownMs - timeSinceDeath) / 1000;
            sender.sendMessage(Message.raw("[System] " + profile.getNpcName() + " wciaz odpoczywa. Musisz poczekac jeszcze " + secondsLeft + " sekund."));
            return;
        }

        // Przełącznik (Toggle): Odsyłanie vs Przywoływanie
        if (profile.isSummoned()) {
            profile.setSummoned(false);
            PlayerProfileManager.saveProfile(playerUuid, profile);
            if (sender.getReference() != null && sender.getReference().getStore() != null) {
                mod.despawnCompanion(playerUuid, sender.getReference().getStore());
            }
            sender.sendMessage(Message.raw("[System] " + profile.getNpcName() + " zostal odeslany."));
        } else {
            profile.setSummoned(true);
            PlayerProfileManager.saveProfile(playerUuid, profile);
            mod.spawnCompanion(playerUuid, profile, sender.getReference());
            sender.sendMessage(Message.raw("[System] Przywolano kompana: " + profile.getNpcName() + "!"));
        }
    }

    @Override
    public String getDescription() {
        return "Przywoluje lub odsyla twojego kompana. (Wymaga aktywnego profilu)";
    }
}