package hytale.ai.npc;

/**
 * Struktura danych mapująca odpowiedź JSON od modelu LLM
 * podczas inicjalizacji nowego kompana.
 */
public record InitializationResponse(
        String personality_prompt,
        String combat_stance
) {}