package hytale.ai.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Moduł sieciowy obsługujący bezpośrednią komunikację z API Google Gemini.
 * <p>
 * Implementuje interfejs AIService, dostarczając warstwę abstrakcji dla zapytań HTTP.
 * Klasa wykorzystuje natywnego klienta Java 11 (HttpClient) do wysyłania
 * asynchronicznych żądań typu POST, zapewniając, że główny wątek serwera Hytale
 * nie jest blokowany podczas oczekiwania na odpowiedź od serwerów Google.
 * Posiada wbudowaną obsługę Native JSON Mode oraz bezpieczne mechanizmy awaryjne (Fallback).
 * </p>
 */
public class GeminiProvider implements AIService {

    /** Pełny adres URL endpointu API zawierający wstrzyknięty model i klucz uwierzytelniający. */
    private final String apiUrl;

    /** Asynchroniczny klient HTTP wielokrotnego użytku. */
    private final HttpClient httpClient;

    /** Instancja biblioteki GSON do mapowania obiektów na format JSON i odwrotnie. */
    private final Gson gson;

    /**
     * Inicjalizuje połączenie dla konkretnego gracza, parsując jego prywatne ustawienia.
     * <p>
     * Metoda automatycznie sanitizuje wejście (usuwa białe znaki) i aplikuje model domyślny,
     * jeśli użytkownik nie podał żadnego w konfiguracji.
     * </p>
     *
     * @param apiKey Prywatny klucz API Google wygenerowany przez gracza.
     * @param aiModel Nazwa modelu językowego (np. "gemini-2.5-flash").
     */
    public GeminiProvider(String apiKey, String aiModel) {

        // Zabezpieczenie (Sanitization): usuwamy białe znaki z klucza
        if (apiKey != null) {
            apiKey = apiKey.trim();
        } else {
            apiKey = "";
            System.err.println("[AI NPC] UWAGA: Przekazano pusty klucz API dla gracza!");
        }

        // Zabezpieczenie (Fallback): jeśli gracz nie podał modelu, używamy domyślnego
        if (aiModel == null || aiModel.trim().isEmpty()) {
            aiModel = "gemini-2.5-flash";
        }

        // Budujemy URL wstrzykując model i klucz gracza prosto w endpoint REST API
        this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + aiModel + ":generateContent?key=" + apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    /**
     * Wysyła asynchroniczne zapytanie (Prompt) do modelu LLM i przetwarza odpowiedź.
     * <p>
     * Metoda buduje zagnieżdżoną strukturę JSON wymaganą przez specyfikację Google Gemini,
     * wymuszając jednocześnie natywny format odpowiedzi (responseMimeType: application/json).
     * Wysyła żądanie przez HTTP POST, a po otrzymaniu odpowiedzi przeszukuje drzewo JSON
     * w celu wyciągnięcia wygenerowanego tekstu.
     * W przypadku błędów sieciowych lub problemów z parsowaniem, zwraca bezpieczny,
     * zapasowy ciąg JSON, zapobiegając awarii logiki Mózgu NPC.
     * </p>
     *
     * @param prompt Pełny, sformatowany tekst zapytania (zawierający kontekst, zasady i pytanie gracza).
     * @return CompletableFuture reprezentujący odpowiedź od AI w formacie ustrukturyzowanego JSON (jako String).
     */
    @Override
    public CompletableFuture<String> generateResponse(String prompt) {
        // Budowanie zagnieżdżonego drzewa JSON zgodnie ze specyfikacją API Gemini
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);

        JsonArray partsArray = new JsonArray();
        partsArray.add(textPart);

        JsonObject contentObj = new JsonObject();
        contentObj.add("parts", partsArray);

        JsonArray contentsArray = new JsonArray();
        contentsArray.add(contentObj);

        JsonObject requestBody = new JsonObject();
        requestBody.add("contents", contentsArray);

        // ZABEZPIECZENIE HARDWARE'OWE: Wymuszenie na API natywnego trybu JSON
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        requestBody.add("generationConfig", generationConfig);

        // Serializacja obiektu Java do ciągu znaków JSON
        String jsonString = gson.toJson(requestBody);

        // Konstrukcja zapytania HTTP POST
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonString))
                .build();

        // Asynchroniczne wysłanie żądania (Non-blocking I/O)
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    // Obsługa błędów sieciowych (np. kod 403, 503, filtry bezpieczeństwa Google)
                    // Zwracamy awaryjny JSON, aby nie spowodować błędu deserializacji w NpcBrain
                    if (response.statusCode() != 200) {
                        System.err.println("[GeminiProvider] BŁĄD HTTP " + response.statusCode() + ": " + response.body());
                        return "{\"thought_process\":{\"observation\":\"Błąd sieciowy lub filtry API.\",\"reasoning\":\"Serwer Google odrzucił zapytanie gracza.\"},\"dialogue\":\"Wybacz szefie, jakaś magiczna bariera zagłusza moje myśli... (Błąd API)\",\"action\":\"NONE\",\"action_target\":\"NONE\"}";
                    }

                    // Bezpieczne parsowanie (Deserializacja) odpowiedzi JSON od Google
                    try {
                        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                        return jsonResponse.getAsJsonArray("candidates")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("content")
                                .getAsJsonArray("parts")
                                .get(0).getAsJsonObject()
                                .get("text").getAsString();
                    } catch (Exception e) {
                        System.err.println("[GeminiProvider] Błąd parsowania węzłów JSON od Google: " + e.getMessage());
                        // Zapasowy JSON w razie gdyby Google zmieniło strukturę odpowiedzi (np. brak 'candidates')
                        return "{\"thought_process\":{\"observation\":\"Błąd parsowania odpowiedzi.\",\"reasoning\":\"Odpowiedź API była pusta lub miała nieznaną strukturę.\"},\"dialogue\":\"Chyba straciłem wątek... Możesz powtórzyć?\",\"action\":\"NONE\",\"action_target\":\"NONE\"}";
                    }
                });
    }
}