package hytale.ai.config;

public class Archetype {

    private int id;
    private String polishName;
    private String description;
    private String roleId;
    private String appearance;
    private String type;
    private String personalityHint;

    public int getId() { return id; }
    public String getPolishName() { return polishName; }
    public String getDescription() { return description; }
    public String getRoleId() { return roleId; }
    public String getAppearance() { return appearance; }
    public String getType() { return type; }
    public String getPersonalityHint() { return personalityHint; }

    public boolean isAnimal() { return "animal".equals(type); }
}
