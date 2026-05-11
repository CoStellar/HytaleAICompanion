package hytale.ai.rl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Tabelaryczny agent Q-Learning.
 *
 * Tabela Q: [stan (48)] x [akcja (5)] = 240 wartosci float
 * Rownaie Bellmana: Q(s,a) <- Q(s,a) + α * (R + γ * max_Q(s') - Q(s,a))
 *
 * Strategia eksploracji: ε-zachlanna z liniowym zanikaniem epsilona.
 */
public class QLearningAgent {

    private static final Logger LOGGER = Logger.getLogger("AI-NPC");
    private static final String QTABLE_PATH = "mods/AICompanion/rl/qtable.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final float ALPHA = 0.10f;
    private static final float GAMMA = 0.90f;
    private static final float EPSILON_START = 0.30f;
    private static final float EPSILON_MIN = 0.05f;
    private static final float EPSILON_DECAY = 0.0001f;

    private final float[][] qTable = new float[RLState.totalStates()][RLAction.VALUES.length];
    private float epsilon = EPSILON_START;
    private long totalDecisions = 0;
    private final Random random = new Random();

    public void load() {
        File file = new File(QTABLE_PATH);
        if (!file.exists()) {
            LOGGER.info("[RL] Brak zapisanej tablicy Q — startujemy od zera.");
            return;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            SaveData data = GSON.fromJson(reader, SaveData.class);
            if (data != null && data.qTable != null && data.qTable.length == qTable.length) {
                for (int s = 0; s < qTable.length; s++) {
                    System.arraycopy(data.qTable[s], 0, qTable[s], 0, qTable[s].length);
                }
                epsilon = Math.max(EPSILON_MIN, data.epsilon);
                totalDecisions = data.totalDecisions;
                LOGGER.info("[RL] Zaladowano tablice Q (" + totalDecisions + " decyzji, epsilon=" + String.format("%.3f", epsilon) + ").");
            }
        } catch (IOException e) {
            LOGGER.warning("[RL] Blad ladowania tablicy Q: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(Paths.get("mods/AICompanion/rl"));
            SaveData data = new SaveData();
            data.qTable = qTable;
            data.epsilon = epsilon;
            data.totalDecisions = totalDecisions;
            try (Writer writer = Files.newBufferedWriter(Paths.get(QTABLE_PATH), StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.warning("[RL] Blad zapisu tablicy Q: " + e.getMessage());
        }
    }

    /**
     * Wybiera akcje wedlug strategii ε-zachlannej.
     * Jesli epsilon > rand → eksploracja (losowa akcja).
     * Inaczej → eksploatacja (najlepsza znana akcja).
     */
    public RLAction selectAction(RLState state) {
        totalDecisions++;
        epsilon = Math.max(EPSILON_MIN, EPSILON_START - totalDecisions * EPSILON_DECAY);

        if (random.nextFloat() < epsilon) {
            return RLAction.VALUES[random.nextInt(RLAction.VALUES.length)];
        }
        return bestAction(state);
    }

    /** Aktualizuje tablice Q zgodnie z rownaniem Bellmana. */
    public void update(RLState prevState, RLAction action, float reward, RLState nextState) {
        int s = prevState.toIndex();
        int a = action.ordinal();
        float maxNextQ = maxQ(nextState);
        qTable[s][a] += ALPHA * (reward + GAMMA * maxNextQ - qTable[s][a]);
    }

    private RLAction bestAction(RLState state) {
        int s = state.toIndex();
        int best = 0;
        for (int a = 1; a < RLAction.VALUES.length; a++) {
            if (qTable[s][a] > qTable[s][best]) best = a;
        }
        return RLAction.VALUES[best];
    }

    private float maxQ(RLState state) {
        int s = state.toIndex();
        float max = qTable[s][0];
        for (int a = 1; a < RLAction.VALUES.length; a++) {
            if (qTable[s][a] > max) max = qTable[s][a];
        }
        return max;
    }

    public float getEpsilon() { return epsilon; }
    public long getTotalDecisions() { return totalDecisions; }

    public String debugInfo(RLState state) {
        int s = state.toIndex();
        StringBuilder sb = new StringBuilder("[RL] Stan: " + state + " | Q: ");
        for (RLAction a : RLAction.VALUES) {
            sb.append(a.name()).append("=").append(String.format("%.2f", qTable[s][a.ordinal()])).append(" ");
        }
        sb.append("| ε=").append(String.format("%.3f", epsilon));
        return sb.toString();
    }

    private static class SaveData {
        float[][] qTable;
        float epsilon;
        long totalDecisions;
    }
}
