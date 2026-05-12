package hytale.ai.rl;

public enum RLAction {
    // Q-table actions — must stay first; Q-table column count = RL_VALUES.length
    FOLLOW,
    ATTACK,
    HEAL,
    FLEE,
    TALK,
    // LLM-only actions — not stored in Q-table
    GUARD,
    STOP,
    PROTECT;

    /** All actions (used for LLM response parsing). */
    public static final RLAction[] VALUES = values();
    /** Only the actions that participate in Q-learning. */
    public static final RLAction[] RL_VALUES = { FOLLOW, ATTACK, HEAL, FLEE, TALK };
}
