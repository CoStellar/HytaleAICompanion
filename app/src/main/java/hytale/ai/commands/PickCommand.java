package hytale.ai.commands;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.api.GeminiProvider;
import hytale.ai.config.Archetype;
import hytale.ai.config.ArchetypeRegistry;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import hytale.ai.config.PlayerSettings;
import hytale.ai.config.WizardState;
import hytale.ai.npc.InitializationResponse;
import hytale.ai.npc.QuirkGenerator;

import java.util.List;
import java.util.UUID;

/**
 * Krok 2 kreatora postaci: gracz wybiera archetyp numerem.
 * Wyzwala generacje osobowosci przez AI i tworzy profil kompana.
 */
public class PickCommand implements AICommand {

    private static final Gson GSON = new Gson();

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        WizardState wizard = mod.getActiveWizards().get(playerUuid);
        if (wizard == null || wizard.isExpired()) {
            sender.sendMessage(Message.raw("[System] Brak aktywnego kreatora. Uzyj najpierw: !ai -create <imie>"));
            mod.getActiveWizards().remove(playerUuid);
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Message.raw("[System] Uzycie: !ai -pick <numer>"));
            return;
        }

        int archetypeId;
        try {
            archetypeId = Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            sender.sendMessage(Message.raw("[System] Podaj liczbe, np. !ai -pick 3"));
            return;
        }

        List<Archetype> all = ArchetypeRegistry.get().getAll();
        Archetype chosen = ArchetypeRegistry.get().getById(archetypeId);
        if (chosen == null) {
            sender.sendMessage(Message.raw("[System] Nieprawidlowy numer. Wybierz od 1 do " + all.size() + "."));
            return;
        }

        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        if (settings == null || !settings.hasKey()) {
            sender.sendMessage(Message.raw("[System] Najpierw dodaj klucz API: !ai -setup <klucz>"));
            return;
        }

        String pendingName = wizard.getPendingName();
        mod.getActiveWizards().remove(playerUuid);

        CompanionProfile profile = mod.getPlayerProfiles().getOrDefault(playerUuid, new CompanionProfile());
        List<String> quirks = QuirkGenerator.generateQuirks(profile.getRandomnessLevel());

        sender.sendMessage(Message.raw("[System] Tworze kompana '" + pendingName + "' (" + chosen.getPolishName() + ")... Chwile!"));

        String quirksText = quirks.isEmpty() ? "brak dziwactw" : String.join(" | ", quirks);
        String systemPrompt = """
                Jestes asystentem Game Mastera. Gracz tworzy NPC o imieniu: %s.
                Wyglad/rasa kompana: %s (%s).
                Wskazowka osobowosci: %s
                Wylosowane cechy (dziwactwa): [%s].

                TWOJE ZADANIE:
                Zwroc odpowiedz WYLACZNIE jako czysty, poprawny JSON. Zadnych znacznikow markdown.

                STRUKTURA JSON:
                {
                  "personality_prompt": "Napisz krotka (3-4 zdania) historie tej postaci w drugiej osobie ('Jestes...'). Uwzgledniaj wyglad i wskazowke osobowosci.",
                  "combat_stance": "Wybierz JEDNA wartosc: PASYWNY, DEFENSYWNY lub AGRESYWNY."
                }
                """.formatted(pendingName, chosen.getPolishName(), chosen.getAppearance(),
                chosen.getPersonalityHint(), quirksText);

        GeminiProvider provider = new GeminiProvider(settings.getApiKey(), settings.getAiModel());
        provider.generateResponse(systemPrompt).thenAccept(jsonResponse -> {
            try {
                String clean = jsonResponse.replace("```json", "").replace("```", "").trim();
                InitializationResponse aiData = GSON.fromJson(clean, InitializationResponse.class);

                profile.setNpcName(pendingName);
                profile.setInGameModel(chosen.getAppearance());
                profile.setRoleId(chosen.getRoleId());
                profile.setSummoned(false);
                profile.setQuirks(quirks);
                profile.setPersonality(aiData.personality_prompt());
                profile.setCombatStance(aiData.combat_stance());
                profile.getChatHistory().clear();
                profile.resetCombatStats();

                PlayerProfileManager.saveProfile(playerUuid, profile);
                mod.getPlayerProfiles().put(playerUuid, profile);
                mod.getActiveCompanions().remove(playerUuid);

                if (sender.getReference() != null && sender.getReference().getStore() != null) {
                    mod.despawnCompanion(playerUuid, sender.getReference().getStore());
                }

                sender.sendMessage(Message.raw("[System] Stworzono: " + pendingName + " (" + chosen.getPolishName() + " / " + chosen.getAppearance() + ")"));
                sender.sendMessage(Message.raw("[Tlo Fabularne] " + profile.getPersonality()));
                sender.sendMessage(Message.raw("Uzyj '!ai -summon', aby go przywolac."));

            } catch (JsonSyntaxException e) {
                sender.sendMessage(Message.raw("[System] Blad AI: Sprobuj ponownie."));
                HytaleAIMod.LOGGER.severe("[AI] Blad parsowania JSON: " + jsonResponse);
            }
        }).exceptionally(ex -> {
            sender.sendMessage(Message.raw("[System] Blad polaczenia z API."));
            return null;
        });
    }

    @Override
    public String getDescription() {
        return "Wybiera archetyp kompana z listy (krok 2 kreatora).";
    }
}
