package hytale.ai.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class ArchetypeRegistry {

    private static final Logger LOGGER = Logger.getLogger("AI-NPC");
    private static final ArchetypeRegistry INSTANCE = new ArchetypeRegistry();
    private static final Gson GSON = new Gson();

    private List<Archetype> archetypes = Collections.emptyList();

    private ArchetypeRegistry() {}

    public static ArchetypeRegistry get() { return INSTANCE; }

    public void load() {
        try (InputStream is = ArchetypeRegistry.class.getClassLoader().getResourceAsStream("archetypes.json")) {
            if (is == null) {
                LOGGER.severe("[AI NPC] Nie mozna znalezc archetypes.json w zasobach!");
                return;
            }
            Type listType = new TypeToken<List<Archetype>>() {}.getType();
            archetypes = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), listType);
            LOGGER.info("[AI NPC] Zaladowano " + archetypes.size() + " archetypow kompanow.");
        } catch (Exception e) {
            LOGGER.severe("[AI NPC] Blad ladowania archetypes.json: " + e.getMessage());
        }
    }

    public List<Archetype> getAll() { return Collections.unmodifiableList(archetypes); }

    public Archetype getById(int id) {
        for (Archetype a : archetypes) {
            if (a.getId() == id) return a;
        }
        return null;
    }

    public String formatList() {
        StringBuilder sb = new StringBuilder("[System] Wybierz archetyp kompana (wpisz !ai -pick <numer>):\n");
        for (Archetype a : archetypes) {
            String tag = a.isAnimal() ? "[Zwierze]" : "[Humanoid]";
            sb.append(String.format("  [%d] %-20s %s - %s\n", a.getId(), a.getPolishName(), tag, a.getDescription()));
        }
        return sb.toString();
    }
}
