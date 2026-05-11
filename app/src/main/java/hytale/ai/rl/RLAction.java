package hytale.ai.rl;

public enum RLAction {
    FOLLOW,
    ATTACK,
    HEAL,
    FLEE,
    TALK;

    public static final RLAction[] VALUES = values();
}
