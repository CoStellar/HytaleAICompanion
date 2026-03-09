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
     * * @param apiKey Prywatny klucz API Google wygenerowany przez gracza.
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
     * wysyła ją przez HTTP POST, a po otrzymaniu odpowiedzi przeszukuje drzewo JSON
     * w celu wyciągnięcia wygenerowanego tekstu. Posiada wbudowaną obsługę błędów HTTP.
     * </p>
     * * @param prompt Pełny, sformatowany tekst zapytania (zawierający kontekst, zasady i pytanie).
     * @return CompletableFuture reprezentujący tekstową odpowiedź od sztucznej inteligencji.
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
                    // Obsługa błędów sieciowych (np. kod 403, 503)
                    if (response.statusCode() != 200) {
                        return "Błąd API: " + response.statusCode() + " (Sprawdź poprawność klucza API w konfiguracji)";
                    }

                    // Bezpieczne parsowanie (Deserializacja) odpowiedzi JSON
                    try {
                        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                        return jsonResponse.getAsJsonArray("candidates")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("content")
                                .getAsJsonArray("parts")
                                .get(0).getAsJsonObject()
                                .get("text").getAsString();
                    } catch (Exception e) {
                        return "Błąd podczas parsowania odpowiedzi: " + e.getMessage();
                    }
                });
    }
}