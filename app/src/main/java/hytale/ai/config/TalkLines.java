package hytale.ai.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

public class TalkLines {

    private static final Logger LOGGER = Logger.getLogger("AI-NPC");
    private static final TalkLines INSTANCE = new TalkLines();
    private static final Gson GSON = new Gson();
    private static final Random RANDOM = new Random();

    private Map<String, List<String>> lines = Collections.emptyMap();

    private TalkLines() {}

    public static TalkLines get() { return INSTANCE; }

    public void load() {
        try (InputStream is = TalkLines.class.getClassLoader().getResourceAsStream("talk_lines.json")) {
            if (is == null) {
                LOGGER.severe("[AI NPC] Nie mozna znalezc talk_lines.json w zasobach!");
                return;
            }
            Type mapType = new TypeToken<Map<String, List<String>>>() {}.getType();
            lines = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), mapType);
            LOGGER.info("[AI NPC] Zaladowano linie dialogowe kompana.");
        } catch (Exception e) {
            LOGGER.severe("[AI NPC] Blad ladowania talk_lines.json: " + e.getMessage());
        }
    }

    public String getRandom(String category) {
        List<String> categoryLines = lines.get(category);
        if (categoryLines == null || categoryLines.isEmpty()) return null;
        return categoryLines.get(RANDOM.nextInt(categoryLines.size()));
    }

    public String getRandomOrFallback(String category, String fallback) {
        String line = getRandom(category);
        return line != null ? line : fallback;
    }
}
