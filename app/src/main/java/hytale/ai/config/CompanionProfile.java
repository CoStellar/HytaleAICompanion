package hytale.ai.config;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Model danych (POJO/DTO) reprezentujący unikalnego kompana AI przypisanego do gracza.
 * <p>
 * Klasa ta przechowuje wszystkie informacje o tożsamości, stanie fizycznym w grze
 * oraz pamięci postaci. Jest automatycznie serializowana i deserializowana
 * do formatu JSON przy użyciu biblioteki GSON, co zapewnia trwałość danych (Persistence)
 * pomiędzy restartami serwera.
 * </p>
 */
public class CompanionProfile {

    /** Wybrane przez gracza imię kompana, używane na czacie i w prompcie. */
    private String npcName;

    /** Identyfikator zasobu (Asset ID) używany do renderowania fizycznego modelu w grze. */
    private String inGameModel;

    /** Bazowa osobowość w formie tekstowej ("Jesteś drwalem..."). */
    private String personality;

    /** Flaga określająca, czy model istnieje aktualnie fizycznie w świecie (zapobiega duplikacji). */
    private boolean isSummoned = false;

    /** Poziom modyfikacji losowych dziwactw (0=Brak, 1=Niski, 2=Średni, 3=Wysoki). */
    private int randomnessLevel = 0;

    /** Postawa kompana wobec przeciwników z radaru (PASYWNY, DEFENSYWNY, AGRESYWNY). */
    private String combatStance = "PASYWNY";

    /** Znacznik czasu (UNIX timestamp w milisekundach) określający dokładny moment śmierci kompana. */
    private long deathTimestamp = 0;

    /** Lista wylosowanych przez QuirkGenerator cech unikalnych (dziwactw). */
    private List<String> quirks = new ArrayList<>();

    /** Trwała pamięć czatu zaimplementowana jako kolejka (zapisywana do JSON). */
    private LinkedList<String> chatHistory = new LinkedList<>();

    // ==========================================
    // GETTERY I SETTERY
    // ==========================================

    public String getNpcName() { return npcName; }
    public void setNpcName(String npcName) { this.npcName = npcName; }

    public String getInGameModel() { return inGameModel; }
    public void setInGameModel(String inGameModel) { this.inGameModel = inGameModel; }

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

    /**
     * Weryfikuje, czy proces podstawowej konfiguracji kompana został poprawnie zakończony.
     * @return true jeśli kompan posiada przypisane imię i model, false w przeciwnym razie.
     */
    public boolean isSetupComplete() {
        return npcName != null && inGameModel != null;
    }
}