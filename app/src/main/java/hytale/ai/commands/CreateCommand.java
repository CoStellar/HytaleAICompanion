package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import hytale.ai.config.PlayerSettings;
import hytale.ai.npc.QuirkGenerator;
import java.util.UUID;
/**
 * Inicjalizuje proces kreacji nowego wirtualnego bytu (NPC).
 * Odpowiada za alokację pamięci dla profilu, przydzielenie modelu oraz
 * wywołanie proceduralnego generatora cech (QuirkGenerator).
 */
public class CreateCommand implements AICommand {
    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        if (settings == null || !settings.hasKey()) {
            sender.sendMessage(Message.raw("[System] Najpierw musisz dodac klucz API: !ai -setup <klucz>"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Message.raw("[System] Uzycie: !ai -create <Imie> <Model> [Losowosc: 0-3]"));
            return;
        }

        String newName = args[1];
        String newModel = args[2];
        int randLevel = 0;
        if (args.length >= 4) {
            try { randLevel = Integer.parseInt(args[3]); } catch (Exception ignored) {}
            if (randLevel > 3) randLevel = 3;
            if (randLevel < 0) randLevel = 0;
        }

        CompanionProfile profile = mod.getPlayerProfiles().getOrDefault(playerUuid, new CompanionProfile());
        int finalRandLevel = randLevel;

        Runnable createAction = () -> {
            profile.setNpcName(newName);
            profile.setInGameModel(newModel);
            profile.setSummoned(false);
            profile.setRandomnessLevel(finalRandLevel);
            profile.setQuirks(QuirkGenerator.generateQuirks(finalRandLevel));
            profile.getChatHistory().clear();

            PlayerProfileManager.saveProfile(playerUuid, profile);
            mod.getPlayerProfiles().put(playerUuid, profile);
            mod.getActiveCompanions().remove(playerUuid);
            mod.despawnCompanion(playerUuid, sender.getReference().getStore());

            sender.sendMessage(Message.raw("[System] Stworzono kompana: " + newName + " (Losowosc: " + finalRandLevel + "). Uzyj '!ai -summon'"));
        };

        if (profile.getNpcName() != null && !profile.getNpcName().isEmpty()) {
            sender.sendMessage(Message.raw("[System] Masz juz kompana. Zostanie nadpisany! Wpisz !ai -confirm"));
            mod.getPendingConfirmations().put(playerUuid, createAction);
        } else {
            createAction.run();
        }
    }

    @Override
    public String getDescription() {
        return "Tworzy nowego kompana (Imie, Model, Losowosc 0-3).";
    }
}