package hytale.ai.commands;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import hytale.ai.HytaleAIMod;
import hytale.ai.api.GeminiProvider;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import hytale.ai.config.PlayerSettings;
import hytale.ai.npc.InitializationResponse;
import hytale.ai.npc.QuirkGenerator;

import java.util.List;
import java.util.UUID;

/**
 * Inicjalizuje proces kreacji nowego wirtualnego bytu (NPC).
 * Odpowiada za alokację pamięci dla profilu, przydzielenie modelu,
 * wywołanie proceduralnego generatora cech oraz wygenerowanie historii przez AI.
 */
public class CreateCommand implements AICommand {

    private static final Gson GSON = new Gson();

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
            sender.sendMessage(Message.raw("[System] Rozpoczynam kreacje kompana o imieniu " + newName + "... Prosze czekac."));

            // 1. Lokalne losowanie dziwactw (Twoja mechanika)
            List<String> generatedQuirks = QuirkGenerator.generateQuirks(finalRandLevel);
            String quirksListString = String.join(" | ", generatedQuirks);

            // 2. Budowa System Promptu wymuszającego JSON
            String systemPrompt = """
                Jesteś asystentem Game Mastera. Gracz tworzy NPC o imieniu: %s.
                Wylosowano dla niego następujące cechy charakteru (dziwactwa): [%s].
                
                TWOJE ZADANIE:
                Zwróć odpowiedź WYŁĄCZNIE jako czysty, poprawny JSON. Żadnych znaczników markdown.
                
                STRUKTURA JSON:
                {
                  "personality_prompt": "Napisz tutaj krótką (3-4 zdania) historię tej postaci, która logicznie uzasadnia wylosowane dziwactwa. Zwracaj się w drugiej osobie ('Jesteś...').",
                  "combat_stance": "Wybierz JEDNĄ wartość pasującą do historii: PASYWNY, DEFENSYWNY lub AGRESYWNY."
                }
                """.formatted(newName, quirksListString);

            // 3. Wysłanie asynchronicznego zapytania do Gemini
            GeminiProvider provider = new GeminiProvider(settings.getApiKey(), settings.getAiModel());
            provider.generateResponse(systemPrompt).thenAccept(jsonResponse -> {
                try {
                    // Oczyszczanie odpowiedzi z potencjalnych formatowań Markdown
                    String cleanJson = jsonResponse.replace("```json", "").replace("```", "").trim();
                    InitializationResponse aiData = GSON.fromJson(cleanJson, InitializationResponse.class);

                    // 4. Przypisywanie danych do profilu (Z uwzględnieniem modelu i czyszczenia)
                    profile.setNpcName(newName);
                    profile.setInGameModel(newModel); // Przywrócony Model!
                    profile.setSummoned(false);
                    profile.setRandomnessLevel(finalRandLevel);
                    profile.setQuirks(generatedQuirks);
                    profile.setPersonality(aiData.personality_prompt());
                    profile.setCombatStance(aiData.combat_stance());
                    profile.getChatHistory().clear(); // Resetujemy pamięć starego bytu

                    // 5. Zapis i czyszczenie stanu serwera
                    PlayerProfileManager.saveProfile(playerUuid, profile);
                    mod.getPlayerProfiles().put(playerUuid, profile);
                    mod.getActiveCompanions().remove(playerUuid); // Wyrzuca starego NpcBrain

                    if (sender.getReference() != null && sender.getReference().getStore() != null) {
                        mod.despawnCompanion(playerUuid, sender.getReference().getStore());
                    }

                    sender.sendMessage(Message.raw("[System] Stworzono kompana: " + newName + " (Model: " + newModel + ")."));
                    sender.sendMessage(Message.raw("[Tło Fabularne]: " + profile.getPersonality()));
                    sender.sendMessage(Message.raw("Uzyj '!ai -summon', aby go przywolac."));

                } catch (JsonSyntaxException e) {
                    sender.sendMessage(Message.raw("[System] Blad AI: Model zwrocil niepoprawny format danych. Sprobuj ponownie."));
                    HytaleAIMod.LOGGER.severe("Blad parsowania JSON: " + jsonResponse);
                }
            }).exceptionally(ex -> {
                sender.sendMessage(Message.raw("[System] Blad polaczenia z API Gemini."));
                return null;
            });
        };

        // Twoja logika potwierdzeń pozostaje nienaruszona
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