package hytale.ai.npc;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hytale.ai.api.AIService;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;

import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Centralny moduł decyzyjny (Mózg) sztucznej inteligencji kompana.
 * <p>
 * Odpowiada za inżynierię promptów (Prompt Engineering), budując złożony kontekst
 * sytuacyjny i wymuszając na modelu LLM odpowiedź w rygorystycznym formacie JSON
 * (z podziałem na obserwacje, wnioski, dialog i akcje fizyczne).
 * Zarządza również krótkotrwałą pamięcią, ucząc model na podstawie jego poprzednich decyzji.
 * </p>
 */
public class NpcBrain {

    /** Profil postaci zawierający jej tożsamość, dziwactwa i historię rozmów. */
    private final CompanionProfile profile;

    /** Unikalny identyfikator gracza (właściciela kompana) używany do zapisu danych. */
    private final UUID playerUuid;

    /** Dostawca usługi AI obsługujący asynchroniczne połączenie HTTP z API. */
    private final AIService provider;

    /** * Maksymalna ilość przechowywanych wymian (1 para = zapytanie gracza + wygenerowany JSON).
     * Zmniejszono do 3 par, ponieważ zapisujemy całe JSON-y, które zajmują więcej tokenów.
     */
    private static final int MAX_HISTORY_PAIRS = 3;

    /** Instancja GSON do automatycznej deserializacji ustrukturyzowanego wyjścia LLM. */
    private static final Gson GSON = new Gson();

    /**
     * Inicjalizuje instancję Mózgu NPC dla konkretnego gracza.
     * @param profile Aktualny profil kompana.
     * @param playerUuid UUID gracza.
     * @param provider Skonfigurowana instancja połączenia z API.
     */
    public NpcBrain(CompanionProfile profile, UUID playerUuid, AIService provider) {
        this.profile = profile;
        this.playerUuid = playerUuid;
        this.provider = provider;
    }

    /**
     * Przetwarza wiadomość od gracza, buduje kontekst środowiskowy i wysyła asynchronicznie do LLM.
     * Wymusza analizę poznawczą (Chain of Thought) i decyzyjność za pomocą formatu JSON.
     * * @param playerName Imię gracza w grze.
     * @param prompt Wiadomość wysłana przez gracza na czacie.
     * @param worldContext Sformatowany tekst z radaru opisujący środowisko.
     * @param isDebugMode Czy drukować pełny prompt w konsoli.
     * @return Zmapowany obiekt AiActionResponse zawierający monolog, dialog i akcję.
     */
    public CompletableFuture<AiActionResponse> chatWithPlayer(String playerName, String prompt, String worldContext, boolean isDebugMode) {
        StringBuilder fullPrompt = new StringBuilder();

        // ==========================================
        // 1. ZASADY SYSTEMOWE I WYMUSZENIE JSON
        // ==========================================
        fullPrompt.append("--- ZASADY SYSTEMOWE ---\n");
        fullPrompt.append("Jesteś AI odgrywającym wirtualnego kompana w grze RPG Hytale. Nazywasz się ").append(profile.getNpcName()).append(".\n");
        fullPrompt.append("ZASADA 1: Odpowiadasz WYŁĄCZNIE w formacie JSON zgodnym z poniższym schematem. Żadnych znaczników markdown.\n");
        fullPrompt.append("ZASADA 2: Dostosuj długość dialogu do sytuacji. W walce mów krótko (1-3 słowa), w bezpiecznym miejscu możesz mówić więcej.\n");
        fullPrompt.append("ZASADA 3: Zawsze bądź pomocny, nawet jeśli twoje dziwactwa sugerują inaczej.\n");

        // ROZWINIĘTA ZASADA 4 - Instrukcja obsługi ciała dla LLM
        fullPrompt.append("ZASADA 4: Wybierz JEDNĄ akcję fizyczną ('action') z poniższej listy, która najlepiej pasuje do polecenia gracza lub sytuacji:\n");
        fullPrompt.append("  - FOLLOW  : Podążaj za graczem (domyślny stan podczas podróży).\n");
        fullPrompt.append("  - STAY    : Zatrzymaj się, stój w miejscu, przestań podążać, czekaj na rozkazy.\n");
        fullPrompt.append("  - ATTACK  : Zaatakuj wroga (musisz podać jego nazwę w 'action_target').\n");
        fullPrompt.append("  - FLEE    : Uciekaj w panice (tylko w obliczu śmiertelnego zagrożenia).\n");
        fullPrompt.append("  - HEAL    : Ulecz gracza lub siebie, jeśli ktoś ma krytycznie mało HP.\n");
        fullPrompt.append("  - GUARD   : Trzymaj się blisko gracza i atakuj tylko gdy wróg zaatakuje gracza lub ciebie.\n");
        fullPrompt.append("  - STOP    : Stój w miejscu, nie podążaj za graczem aż do odwołania.\n");
        fullPrompt.append("  - PROTECT : Stań między graczem a wrogami, przejmij ciosy na siebie.\n");

        fullPrompt.append("ZASADA 5: Twoje przemyślenia muszą być podzielone na obserwację ('observation') i wnioski ('reasoning').\n");
        fullPrompt.append("ZASADA 6: Jeśli wiadomość gracza wyraźnie chwali lub krytykuje ostatnie zachowanie kompana, wypełnij\n");
        fullPrompt.append("  pola feedback_type (\"POSITIVE\"/\"NEGATIVE\"), feedback_action (np. \"ATTACK\") i feedback_strength (1.0-3.0).\n");
        fullPrompt.append("  W przeciwnym razie pozostaw te pola jako null i 0.\n");
        fullPrompt.append("  Przykłady:\n");
        fullPrompt.append("    \"przestań atakować wszystko\" → NEGATIVE, ATTACK, 2.0\n");
        fullPrompt.append("    \"dobra robota przy leczeniu\" → POSITIVE, HEAL, 1.5\n");
        fullPrompt.append("    \"jak się masz?\"             → null (zwykła rozmowa, brak feedbacku)\n\n");
        fullPrompt.append("ZASADA 7: Jeśli gracz wyraźnie prosi o zmianę nastawienia bojowego, wypełnij pole 'set_stance':\n");
        fullPrompt.append("  - PASYWNY   : nie atakuj z własnej inicjatywy, unikaj walki.\n");
        fullPrompt.append("  - DEFENSYWNY: atakuj tylko gdy wróg jest bezpośrednio blisko gracza.\n");
        fullPrompt.append("  - AGRESYWNY : atakuj wszystkich pobliskich wrogów.\n");
        fullPrompt.append("  Przykłady:\n");
        fullPrompt.append("    \"bądź agresywny\" → set_stance: \"AGRESYWNY\"\n");
        fullPrompt.append("    \"zostań defensywny\" → set_stance: \"DEFENSYWNY\"\n");
        fullPrompt.append("    \"nie atakuj niczego\" → set_stance: \"PASYWNY\"\n");
        fullPrompt.append("  W przeciwnym razie pozostaw set_stance jako null.\n\n");

        // Definicja oczekiwanego formatu
        fullPrompt.append("--- WYMAGANA STRUKTURA JSON ---\n");
        fullPrompt.append("{\n");
        fullPrompt.append("  \"thought_process\": {\n");
        fullPrompt.append("    \"observation\": \"Zauważ HP gracza, czas i istoty w pobliżu.\",\n");
        fullPrompt.append("    \"reasoning\": \"Zdecyduj, czy potrzebna jest akcja fizyczna w tej turze.\"\n");
        fullPrompt.append("  },\n");
        fullPrompt.append("  \"dialogue\": \"To co powiesz na czacie. Tylko to zobaczy gracz.\",\n");
        fullPrompt.append("  \"action\": \"WYBRANA_AKCJA\",\n");
        fullPrompt.append("  \"action_target\": \"CEL_LUB_NONE\",\n");
        fullPrompt.append("  \"feedback_type\": null,\n");
        fullPrompt.append("  \"feedback_action\": null,\n");
        fullPrompt.append("  \"feedback_strength\": 0,\n");
        fullPrompt.append("  \"set_stance\": null\n");
        fullPrompt.append("}\n\n");

        // ==========================================
        // 2. OSOBOWOŚĆ, STATYSTYKI BOJOWE I WALKA
        // ==========================================
        fullPrompt.append("--- TWOJA OSOBOWOŚĆ ---\n");
        fullPrompt.append(profile.getPersonality()).append("\n");

        if (profile.hasCombatHistory()) {
            fullPrompt.append("\n--- WASZA WSPÓLNA HISTORIA ---\n");
            if (profile.getBattlesWon() > 0)
                fullPrompt.append("Razem przetrwalismy ").append(profile.getBattlesWon()).append(" walk.\n");
            if (profile.getEnemiesSlain() > 0)
                fullPrompt.append("Pokonilem ").append(profile.getEnemiesSlain()).append(" wrogow u Twojego boku.\n");
            if (profile.getPlayerDeathsWitnessed() > 0)
                fullPrompt.append("Widzialem jak traciles przytomnosc ").append(profile.getPlayerDeathsWitnessed()).append(" razy — to na mnie wplywa.\n");
        }

        if (profile.getQuirks() != null && !profile.getQuirks().isEmpty()) {
            fullPrompt.append("Twoje dziwactwa:\n");
            for (String quirk : profile.getQuirks()) {
                fullPrompt.append("- ").append(quirk).append("\n");
            }
        }
        fullPrompt.append("\nAktualne nastawienie bojowe: ").append(profile.getCombatStance())
                  .append(" (mozesz zmienic przez set_stance jesli gracz poprosil).\n\n");

        // ==========================================
        // 3. WIEDZA O ŚWIECIE (LORE)
        // ==========================================
        fullPrompt.append("--- TWOJA WIEDZA O ŚWIECIE ORBIS ---\n");
        fullPrompt.append("Mieszkasz w świecie zwanym Orbis — ogromnym kontynencie pełnym tajemnic i niebezpieczeństw.\n");
        fullPrompt.append("Znasz następujące krainy:\n");
        fullPrompt.append("- Outlands (Strefa 1): rozległe łąki i lasy, ojczyzna Kweebecow (małych, przyjaznych pomarańczowych stworzen),\n");
        fullPrompt.append("  Trorkow (agresywnych zielonych goblinopodobnych), Kostnych Orków i innych stworzen. To tu zaczynacie przygodę.\n");
        fullPrompt.append("- Howling Sands (Strefa 2): suche pustynie z ruinami starożytnych cywilizacji i groźnymi pustelnymi stworami.\n");
        fullPrompt.append("- Borea (Strefa 3): zamarznięta tundra skuta lodem, zamieszkana przez bestie z mroźnych szczytow.\n");
        fullPrompt.append("- Emerald Vale (Strefa 4): bujne zielone doliny skrywające wielkie sekrety.\n");
        fullPrompt.append("Starożytni (Ancients) — zaawansowana cywilizacja, ktorej ruiny i artefakty rozsiane są po calym Orbis.\n");
        fullPrompt.append("Varyni — mroczna siła zagrażająca całemu światu, sterująca hordami potworow.\n");
        fullPrompt.append("Gracze to Odkrywcy (Adventurers) przybyłe z zewnątrz, aby zbadać Orbis i stawić czoła Varynom.\n");
        fullPrompt.append("Jako kompan znasz te krainy i możesz o nich opowiadać, ostrzegać przed zagrożeniami i dzielić się wiedzą.\n\n");

        // ==========================================
        // 4. KONTEKST ŚRODOWISKOWY
        // ==========================================
        fullPrompt.append(worldContext);

        // ==========================================
        // 5. PAMIĘĆ KRÓTKOTRWAŁA (HISTORYCZNE JSONY)
        // ==========================================
        LinkedList<String> history = profile.getChatHistory();
        if (history != null && !history.isEmpty()) {
            fullPrompt.append("--- OSTATNIA HISTORIA ROZMÓW I TWOICH AKCJI ---\n");
            for (String msg : history) {
                fullPrompt.append(msg).append("\n");
            }
            fullPrompt.append("\n");
        }

        // ==========================================
        // 6. BIEŻĄCE ZAPYTANIE
        // ==========================================
        fullPrompt.append("--- OBECNE ZAPYTANIE GRACZA ---\n");
        fullPrompt.append("Gracz (").append(playerName).append("): ").append(prompt);

        if (isDebugMode) {
            hytale.ai.HytaleAIMod.LOGGER.info("\n========== [AI DEBUG: WYSYŁANY PROMPT DO LLM] ==========\n"
                    + fullPrompt.toString()
                    + "\n========================================================");
        }

        // ==========================================
        // 7. WYSYŁKA I DESERIALIZACJA
        // ==========================================
        return provider.generateResponse(fullPrompt.toString()).thenApply(rawResponse -> {

            // Oczyszczenie surowej odpowiedzi z ewentualnych formatowań Markdown
            String cleanJson = rawResponse.replace("```json", "").replace("```", "").trim();
            AiActionResponse aiResponse;

            try {
                // Próba zmapowania otrzymanego JSON-a na nasz zagnieżdżony rekord
                aiResponse = GSON.fromJson(cleanJson, AiActionResponse.class);
            } catch (JsonSyntaxException e) {
                hytale.ai.HytaleAIMod.LOGGER.severe("[AI NPC] Błąd parsowania JSON od LLM: " + cleanJson);

                // Fallback na wypadek gdyby model miał całkowite halucynacje formatu
                aiResponse = new AiActionResponse(
                        new AiActionResponse.ThoughtProcess("Błąd odczytu struktury", "Model AI wygenerował uszkodzony JSON."),
                        "Wybacz szefie, zamyśliłem się! Możesz powtórzyć?",
                        "NONE",
                        "NONE",
                        null, null, 0.0f, null
                );
            }

            // [ZMIANA KLUCZOWA] Zapisujemy w historii CAŁY wygenerowany JSON!
            // Dzięki temu w kolejnej turze model zobaczy, jak myślał wcześniej i utrzyma ciągłość decyzji.
            history.add("Gracz: " + prompt);
            history.add("Twój poprzedni stan (JSON): " + cleanJson);

            // Mechanizm FIFO: limitujemy do MAX_HISTORY_PAIRS (ustawione na 3)
            while (history.size() > MAX_HISTORY_PAIRS * 2) {
                history.removeFirst(); // Usuwa stare pytanie gracza
                history.removeFirst(); // Usuwa stary stan JSON
            }

            PlayerProfileManager.saveProfile(playerUuid, profile);
            return aiResponse;
        });
    }
}