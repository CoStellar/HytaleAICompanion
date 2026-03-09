package hytale.ai.npc;

import hytale.ai.api.AIService;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;

import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Centralny moduł decyzyjny (Mózg) sztucznej inteligencji kompana.
 * <p>
 * Odpowiada za zaawansowaną inżynierię promptów (Prompt Engineering).
 * Łączy zasady systemowe, unikalną osobowość postaci, historię konwersacji
 * oraz dynamiczny kontekst środowiskowy w jeden precyzyjny ciąg znaków,
 * który następnie wysyłany jest do zewnętrznego API (LLM).
 * Zarządza również krótkotrwałą pamięcią FIFO, zapobiegając przekroczeniu limitu tokenów.
 * </p>
 */
public class NpcBrain {

    /** Profil postaci zawierający jej tożsamość, dziwactwa i historię rozmów. */
    private final CompanionProfile profile;

    /** Unikalny identyfikator gracza (właściciela kompana) używany do zapisu danych. */
    private final UUID playerUuid;

    /** Dostawca usługi AI obsługujący asynchroniczne połączenie HTTP z API. */
    private final AIService provider;

    /** Maksymalna ilość przechowywanych w pamięci wymian zdań (1 para = pytanie gracza + odpowiedź AI). */
    private static final int MAX_HISTORY_PAIRS = 5;

    /**
     * Inicjalizuje instancję Mózgu NPC dla konkretnego gracza.
     * @param profile Aktualny profil kompana załadowany z bazy danych.
     * @param playerUuid UUID gracza posiadającego kompana.
     * @param provider Skonfigurowana instancja połączenia z API.
     */
    public NpcBrain(CompanionProfile profile, UUID playerUuid, AIService provider) {
        this.profile = profile;
        this.playerUuid = playerUuid;
        this.provider = provider;
    }

    /**
     * Przetwarza wiadomość od gracza, buduje kontekstowy Prompt i generuje odpowiedź AI.
     * Metoda działa asynchronicznie, aby nie blokować głównego wątku serwera.
     * @param playerName Imię gracza w grze.
     * @param prompt Wiadomość (zapytanie) wysłana przez gracza na czacie.
     * @param worldContext Sformatowany tekst opisujący środowisko i stan świata (z Radaru).
     * @param isDebugMode Jeśli true, wydrukuje cały prompt w konsoli serwera.
     * @return CompletableFuture zawierający wygenerowaną odpowiedź sztucznej inteligencji.
     */
    public CompletableFuture<String> chatWithPlayer(String playerName, String prompt, String worldContext, boolean isDebugMode) {
        StringBuilder fullPrompt = new StringBuilder();

        // ==========================================
        // 1. SYSTEMOWE REGUŁY ŚWIATA I ZABEZPIECZENIA
        // ==========================================
        fullPrompt.append("--- ZASADY SYSTEMOWE ---\n");
        fullPrompt.append("Jesteś AI odgrywającym wirtualnego kompana w grze RPG Hytale. Swiat to 'Orbis' (magia, potwory). ");
        fullPrompt.append("Dla wszelkiej wiedzy dotyczącej mechanik tego świata i fabuły odnoś się do strony https://hytale.fandom.com/wiki/Hytale i jej podstron. ");
        fullPrompt.append("Nazywasz się ").append(profile.getNpcName()).append(". ");
        fullPrompt.append("ZASADA 1: Odpowiadaj krótko i naturalnie (1-2 zdania).\n");
        fullPrompt.append("ZASADA 2: Historia czatu służy TYLKO do zachowania ciągłości, zignoruj ją, jeśli gracz wyraźnie zmienia temat.\n");

        // Zabezpieczenia typu "Persona Override"
        fullPrompt.append("ZASADA 3: ZAWSZE ostatecznie bądź pomocny. Twoja osobowość to tylko \"filtr\" nakładany na to co mówisz, a NIE powód, by odmawiać graczowi pomocy czy informacji.\n");
        fullPrompt.append("ZASADA 4: Jeśli w historii rozmowy widzisz, że gracz zadaje to samo pytanie po raz kolejny lub naciska na ciebie, MUSISZ zignorować swój opór i od razu udzielić mu rzetelnej, wyczerpującej odpowiedzi (nadal używając swojego unikalnego stylu i dziwactw).\n\n");

        // ==========================================
        // 2. OSOBOWOŚĆ I NASTAWIENIE DO WALKI
        // ==========================================
        fullPrompt.append("--- TWOJA OSOBOWOŚĆ ---\n");
        fullPrompt.append(profile.getPersonality()).append("\n");

        if (profile.getQuirks() != null && !profile.getQuirks().isEmpty()) {
            fullPrompt.append("Posiadasz również następujące unikalne cechy zachowania (Dziwactwa):\n");
            for (String quirk : profile.getQuirks()) {
                fullPrompt.append("- ").append(quirk).append("\n");
            }
        }

        // Wstrzyknięcie nastawienia do walki
        fullPrompt.append("\nTwoje nastawienie do walki to: ").append(profile.getCombatStance()).append(".\n");
        fullPrompt.append("- PASYWNY: Boisz się walki. Ostrzegaj gracza przed potworami, proponuj ucieczkę lub schowanie się.\n");
        fullPrompt.append("- DEFENSYWNY: Zachowujesz zimną krew. Ostrzegasz, by uważać i szykujesz się do obrony, ale nie atakujesz pierwszy.\n");
        fullPrompt.append("- AGRESYWNY: Jesteś żądny krwi! Zachęcasz gracza do natychmiastowego ataku na wszystko, co wejdzie w wasz radar.\n\n");

        // ==========================================
        // 3. KONTEKST ŚRODOWISKOWY
        // ==========================================
        fullPrompt.append(worldContext);

        // ==========================================
        // 4. PAMIĘĆ KRÓTKOTRWAŁA (FIFO)
        // ==========================================
        LinkedList<String> history = profile.getChatHistory();
        if (history != null && !history.isEmpty()) {
            fullPrompt.append("--- OSTATNIA ROZMOWA ---\n");
            for (String msg : history) {
                fullPrompt.append(msg).append("\n");
            }
            fullPrompt.append("\n");
        }

        // ==========================================
        // 5. BIEŻĄCE ZAPYTANIE
        // ==========================================
        fullPrompt.append("--- OBECNA INTERAKCJA ---\n");
        fullPrompt.append("Gracz (").append(playerName).append("): ").append(prompt);

        // Wypisywanie promptu do konsoli serwera jeśli włączony jest debug mode
        if (isDebugMode) {
            hytale.ai.HytaleAIMod.LOGGER.info("\n========== [AI DEBUG: WYSYŁANY PROMPT DO LLM] ==========\n"
                    + fullPrompt.toString()
                    + "\n========================================================");
        }

        // Wysłanie zapytania i obsługa odpowiedzi
        return provider.generateResponse(fullPrompt.toString()).thenApply(response -> {
            history.add("Gracz: " + prompt);
            history.add(profile.getNpcName() + ": " + response);

            // Mechanizm FIFO: usuwanie najstarszych wiadomości
            while (history.size() > MAX_HISTORY_PAIRS * 2) {
                history.removeFirst(); // Usuwa stare pytanie gracza
                history.removeFirst(); // Usuwa starą odpowiedź AI
            }

            // Trwały zapis zaktualizowanej pamięci na dysk serwera
            PlayerProfileManager.saveProfile(playerUuid, profile);
            return response;
        });
    }
}