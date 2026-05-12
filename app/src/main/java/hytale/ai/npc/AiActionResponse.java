package hytale.ai.npc;

/**
 * Struktura danych wymuszająca analizę środowiska i decyzję o akcji.
 * Opcjonalne pola feedback_* są wypełniane przez LLM gdy gracz wyraźnie
 * chwali lub krytykuje ostatnie zachowanie kompana.
 */
public record AiActionResponse(
        ThoughtProcess thought_process,
        String dialogue,
        String action,
        String action_target,
        String feedback_type,    // "POSITIVE", "NEGATIVE", or null
        String feedback_action,  // RLAction name this feedback targets, or null
        float  feedback_strength, // 1.0 = weak, 2.0 = medium, 3.0 = strong (0 = none)
        String set_stance        // "PASYWNY", "DEFENSYWNY", "AGRESYWNY", or null
) {
    public record ThoughtProcess(
            String observation,
            String reasoning
    ) {}
}