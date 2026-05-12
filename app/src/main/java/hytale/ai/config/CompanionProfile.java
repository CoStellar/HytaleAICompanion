package hytale.ai.config;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Model danych (POJO/DTO) reprezentujący unikalnego kompana AI przypisanego do gracza.
 * Serializowany do JSON przez GSON.
 */
public class CompanionProfile {

    private String npcName;
    private String inGameModel;
    /** ID roli NPC w silniku gry: "ai_companion" (humanoid) lub "ai_companion_animal". */
    private String roleId = "ai_companion";
    private String personality;
    private boolean isSummoned = false;
    private int randomnessLevel = 2; // default: medium quirk budget (4 pts)
    private String combatStance = "PASYWNY";
    private long deathTimestamp = 0;
    private List<String> quirks = new ArrayList<>();
    private LinkedList<String> chatHistory = new LinkedList<>();

    // --- Statystyki bojowe (zapisywane w historii do promptu LLM) ---
    private int battlesWon = 0;
    private int playerDeathsWitnessed = 0;
    private int enemiesSlain = 0;

    public String getNpcName() { return npcName; }
    public void setNpcName(String npcName) { this.npcName = npcName; }

    public String getInGameModel() { return inGameModel; }
    public void setInGameModel(String inGameModel) { this.inGameModel = inGameModel; }

    public String getRoleId() { return roleId != null ? roleId : "ai_companion"; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getPersonality() { return personality; }
    public void setPersonality(String personality) { this.personality = personality; }

    public boolean isSummoned() { return isSummoned; }
    public void setSummoned(boolean summoned) { this.isSummoned = summoned; }

    public int getRandomnessLevel() { return randomnessLevel; }
    public void setRandomnessLevel(int randomnessLevel) { this.randomnessLevel = randomnessLevel; }

    public String getCombatStance() { return combatStance; }
    public void setCombatStance(String combatStance) { this.combatStance = combatStance; }

    public long getDeathTimestamp() { return deathTimestamp; }
    public void setDeathTimestamp(long deathTimestamp) { this.deathTimestamp = deathTimestamp; }

    public List<String> getQuirks() { return quirks; }
    public void setQuirks(List<String> quirks) { this.quirks = quirks; }

    public LinkedList<String> getChatHistory() { return chatHistory; }

    public int getBattlesWon() { return battlesWon; }
    public void setBattlesWon(int battlesWon) { this.battlesWon = battlesWon; }
    public void incrementBattlesWon() { this.battlesWon++; }

    public int getPlayerDeathsWitnessed() { return playerDeathsWitnessed; }
    public void setPlayerDeathsWitnessed(int n) { this.playerDeathsWitnessed = n; }

    public int getEnemiesSlain() { return enemiesSlain; }
    public void setEnemiesSlain(int enemiesSlain) { this.enemiesSlain = enemiesSlain; }
    public void incrementEnemiesSlain() { this.enemiesSlain++; }

    public void resetCombatStats() {
        this.battlesWon = 0;
        this.playerDeathsWitnessed = 0;
        this.enemiesSlain = 0;
    }

    public boolean hasCombatHistory() {
        return battlesWon > 0 || playerDeathsWitnessed > 0 || enemiesSlain > 0;
    }

    public boolean isSetupComplete() {
        return npcName != null && inGameModel != null;
    }
}
