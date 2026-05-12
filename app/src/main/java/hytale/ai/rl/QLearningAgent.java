package hytale.ai.rl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tabelaryczny agent Q-Learning — per-player instancja.
 *
 * Tabela Q: [stan (48)] x [akcja (5)] = 240 wartosci float
 * Rownaie Bellmana: Q(s,a) <- Q(s,a) + α * (R + γ * max_Q(s') - Q(s,a))
 *
 * Strategia eksploracji: ε-zachlanna z liniowym zanikaniem epsilona.
 *
 * Tryb inferencji (rlEnabled=false): zawsze najlepsza akcja, bez aktualizacji.
 * Tryb uczenia (rlEnabled=true): epsilon-zachlanny + aktualizacja Bellmana.
 */
public class QLearningAgent {

    private static final Logger LOGGER = Logger.getLogger("AI-NPC");
    private static final String PLAYERS_DIR = "mods/AICompanion/rl/players/";
    private static final String BASE_RESOURCE = "/base_qtable.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final float ALPHA = 0.10f;
    private static final float GAMMA = 0.90f;
    private static final float EPSILON_START = 0.30f;
    private static final float EPSILON_MIN = 0.05f;
    private static final float EPSILON_DECAY = 0.0001f;

    private final String savePath;
    private final float[][] qTable = new float[RLState.totalStates()][RLAction.RL_VALUES.length];
    private float epsilon = EPSILON_START;
    private long totalDecisions = 0;
    private final Random random = new Random();

    public QLearningAgent(UUID playerUuid) {
        this.savePath = PLAYERS_DIR + playerUuid.toString() + ".json";
    }

    /**
     * Priority order:
     *   1. Player's personal Q-table (mods/AICompanion/rl/players/<uuid>.json)
     *   2. Bundled base model from JAR classpath (base_qtable.json, produced by train_qtable.py)
     *   3. Hard-coded domain-knowledge defaults (fallback if JAR has no base model yet)
     */
    public void loadOrInitFromBase() {
        File file = new File(savePath);
        if (file.exists()) {
            load();
            return;
        }
        if (loadFromClasspath()) return;
        initializeDefaultValues();
    }

    private boolean loadFromClasspath() {
        try (InputStream is = QLearningAgent.class.getResourceAsStream(BASE_RESOURCE)) {
            if (is == null) return false;
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                SaveData data = GSON.fromJson(reader, SaveData.class);
                if (data != null && data.qTable != null
                        && data.qTable.length == qTable.length
                        && data.qTable[0].length == qTable[0].length) {
                    for (int s = 0; s < qTable.length; s++) {
                        System.arraycopy(data.qTable[s], 0, qTable[s], 0, qTable[s].length);
                    }
                    // Start new players at EPSILON_MIN so they immediately exploit the trained model
                    epsilon = EPSILON_MIN;
                    totalDecisions = data.totalDecisions;
                    LOGGER.info("[RL] Zaladowano bazowy model Q z zasobow JAR.");
                    return true;
                }
            }
        } catch (IOException e) {
            LOGGER.warning("[RL] Blad ladowania bazowego modelu z JAR: " + e.getMessage());
        }
        return false;
    }

    private void load() {
        try (Reader reader = Files.newBufferedReader(new File(savePath).toPath(), StandardCharsets.UTF_8)) {
            SaveData data = GSON.fromJson(reader, SaveData.class);
            if (data != null && data.qTable != null && data.qTable.length == qTable.length
                    && data.qTable[0].length == qTable[0].length) {
                for (int s = 0; s < qTable.length; s++) {
                    System.arraycopy(data.qTable[s], 0, qTable[s], 0, qTable[s].length);
                }
                epsilon = Math.max(EPSILON_MIN, data.epsilon);
                totalDecisions = data.totalDecisions;
                LOGGER.info("[RL] Zaladowano tablice Q (" + totalDecisions + " decyzji, epsilon="
                        + String.format("%.3f", epsilon) + ") z " + savePath);
            } else {
                LOGGER.warning("[RL] Niezgodny format Q-table w " + savePath + " — ladujemy wartosci domyslne.");
                initializeDefaultValues();
            }
        } catch (IOException e) {
            LOGGER.warning("[RL] Blad ladowania tablicy Q: " + e.getMessage());
            initializeDefaultValues();
        }
    }

    public void save() {
        try {
            Files.createDirectories(Paths.get(PLAYERS_DIR));
            SaveData data = new SaveData();
            data.qTable = qTable;
            data.epsilon = epsilon;
            data.totalDecisions = totalDecisions;
            try (Writer writer = Files.newBufferedWriter(Paths.get(savePath), StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.warning("[RL] Blad zapisu tablicy Q: " + e.getMessage());
        }
    }

    /**
     * Populates Q-table with domain-knowledge-based starter values so new
     * players get sensible companion behavior from the first session.
     *
     * State: (playerHpBucket 0-3, enemyCountBucket 0-3, distanceBucket 0-2)
     */
    private void initializeDefaultValues() {
        for (int hp = 0; hp < 4; hp++) {
            for (int enemies = 0; enemies < 4; enemies++) {
                for (int dist = 0; dist < 3; dist++) {
                    int s = new RLState(hp, enemies, dist).toIndex();

                    if (enemies == 0) {
                        // No threat — stay close to player
                        qTable[s][RLAction.FOLLOW.ordinal()] = 0.5f;
                    } else if (hp == 0) {
                        // Critical HP — always flee
                        qTable[s][RLAction.FLEE.ordinal()] = 1.2f;
                    } else if (hp == 1 && enemies >= 2) {
                        // Low HP, multiple enemies — flee
                        qTable[s][RLAction.FLEE.ordinal()] = 0.9f;
                    } else if (hp <= 1) {
                        // Low HP — heal first
                        qTable[s][RLAction.HEAL.ordinal()] = 0.8f;
                    } else if (dist == 2) {
                        // Enemies far — don't sprint-aggro, keep escorting player
                        qTable[s][RLAction.FOLLOW.ordinal()] = 0.5f;
                    } else {
                        // Good HP, enemies nearby — attack
                        qTable[s][RLAction.ATTACK.ordinal()] = 0.6f;
                    }
                }
            }
        }
        LOGGER.info("[RL] Zaladowano domyslne wartosci Q (nowy gracz).");
    }

    // ---- Learning mode (rlEnabled=true) ----

    /**
     * Epsilon-greedy action selection; also increments decision counter
     * and decays epsilon. Call only in learning mode.
     */
    public RLAction selectAction(RLState state) {
        totalDecisions++;
        epsilon = Math.max(EPSILON_MIN, EPSILON_START - totalDecisions * EPSILON_DECAY);

        if (random.nextFloat() < epsilon) {
            return RLAction.RL_VALUES[random.nextInt(RLAction.RL_VALUES.length)];
        }
        return bestAction(state);
    }

    /** Bellman update. Call only in learning mode. */
    public void update(RLState prevState, RLAction action, float reward, RLState nextState) {
        int s = prevState.toIndex();
        int a = action.ordinal();
        float maxNextQ = maxQ(nextState);
        qTable[s][a] += ALPHA * (reward + GAMMA * maxNextQ - qTable[s][a]);
    }

    // ---- Inference mode (rlEnabled=false) ----

    /** Pure greedy selection — no exploration, no side-effects. */
    public RLAction inferBestAction(RLState state) {
        return bestAction(state);
    }

    // ---- Manual feedback ----

    /** Applies a direct reward adjustment without a full Bellman update. */
    public void applyManualReward(RLState state, RLAction action, float reward) {
        if (action.ordinal() >= RLAction.RL_VALUES.length) return;
        qTable[state.toIndex()][action.ordinal()] += reward;
    }

    // ---- Internals ----

    private RLAction bestAction(RLState state) {
        int s = state.toIndex();
        int best = 0;
        for (int a = 1; a < RLAction.RL_VALUES.length; a++) {
            if (qTable[s][a] > qTable[s][best]) best = a;
        }
        return RLAction.RL_VALUES[best];
    }

    private float maxQ(RLState state) {
        int s = state.toIndex();
        float max = qTable[s][0];
        for (int a = 1; a < RLAction.RL_VALUES.length; a++) {
            if (qTable[s][a] > max) max = qTable[s][a];
        }
        return max;
    }

    public float getEpsilon() { return epsilon; }
    public long getTotalDecisions() { return totalDecisions; }

    public String debugInfo(RLState state) {
        int s = state.toIndex();
        StringBuilder sb = new StringBuilder("[RL] Stan: " + state + " | Q: ");
        for (RLAction a : RLAction.RL_VALUES) {
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
