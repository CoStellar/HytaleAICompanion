package hytale.ai.api;

import java.util.concurrent.CompletableFuture;

/**
 * Interfejs (Kontrakt) definiujący warstwę abstrakcji dla usług sztucznej inteligencji.
 * <p>
 * Wykorzystanie interfejsu pozwala na oddzielenie logiki decyzyjnej Mózgu NPC (NpcBrain)
 * od konkretnej implementacji API dostawcy (np. Google Gemini, OpenAI, Claude).
 * Umożliwia to łatwą podmianę modelu językowego (LLM) w przyszłości bez modyfikacji
 * rdzenia modyfikacji.
 * </p>
 */
public interface AIService {

    /**
     * Wysyła asynchroniczne zapytanie do wybranego modelu językowego i zwraca odpowiedź.
     * @param prompt Pełny tekst zapytania (zawierający kontekst i zasady).
     * @return CompletableFuture reprezentujący tekstową odpowiedź od AI, rozwiązywany w tle.
     */
    CompletableFuture<String> generateResponse(String prompt);
}