package hytale.ai.rl;

/**
 * Zapis stanu i akcji z poprzedniego ticku — potrzebny do obliczenia nagrody
 * w nastepnej iteracji algorytmu Q-Learning.
 */
public class CombatObservation {

    public final RLState state;
    public final RLAction action;
    public final float playerHp;
    public final float playerMaxHp;

    public CombatObservation(RLState state, RLAction action, float playerHp, float playerMaxHp) {
        this.state = state;
        this.action = action;
        this.playerHp = playerHp;
        this.playerMaxHp = playerMaxHp;
    }

    public float hpRatio() {
        if (playerMaxHp <= 0) return 1.0f;
        return playerHp / playerMaxHp;
    }
}
