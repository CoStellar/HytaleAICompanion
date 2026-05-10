package hytale.ai.npc;

/**
 * Struktura danych wymuszająca analizę środowiska i decyzję o akcji.
 */
public record AiActionResponse(
        ThoughtProcess thought_process,
        String dialogue,
        String action,
        String action_target
) {
    public record ThoughtProcess(
            String observation,
            String reasoning
    ) {}
}