package hytale.ai.config;

/**
 * Model danych (POJO) przechowujący wrażliwe dane konfiguracyjne gracza.
 * <p>
 * Klasa jest odseparowana od logiki gry i profilu postaci, aby zapewnić
 * bezpieczeństwo prywatnego klucza API. Użytkownik nie traci tych danych
 * nawet po skasowaniu lub śmierci swojego wirtualnego kompana.
 * </p>
 */
public class PlayerSettings {

    /** Prywatny klucz API usługi Google Gemini. */
    private String apiKey;

    /** Wybrany model LLM. Domyślnie zoptymalizowany pod kątem szybkości i kosztów. */
    private String aiModel = "gemini-2.5-flash";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    private boolean debugMode = false;
    private boolean rlEnabled = false;

    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }

    public boolean isRlEnabled() { return rlEnabled; }
    public void setRlEnabled(boolean rlEnabled) { this.rlEnabled = rlEnabled; }
    /**
     * Weryfikuje, czy gracz poprawnie zdefiniował swój klucz API.
     * @return true, jeśli klucz istnieje i nie jest pusty.
     */
    public boolean hasKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}