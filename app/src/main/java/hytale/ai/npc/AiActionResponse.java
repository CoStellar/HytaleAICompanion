package hytale.ai.npc;

/**
 * Zaktualizowana, rygorystyczna struktura danych wymuszająca analizę środowiska.
 */
public record AiActionResponse(
        ThoughtProcess thought_process,
        String dialogue,
        String action,
        String action_target
) {
    // Zagnieżdżony rekord wymuszający na modelu konkretne etapy myślenia
    public record ThoughtProcess(
            String observation, // Obserwacja HP gracza i otoczenia z radaru
            String reasoning    // Wyciągnięcie wniosków i uzasadnienie wybranej akcji
    ) {}
}