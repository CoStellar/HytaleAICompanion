package hytale.ai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Klasa zarządzająca zapisem i odczytem (I/O) prywatnych ustawień gracza.
 * <p>
 * Odpowiada za bezpieczną serializację klasy {@link PlayerSettings} do plików JSON.
 * Zapewnia kodowanie UTF-8, chroniąc przed uszkodzeniem danych.
 * </p>
 */
public class PlayerSettingsManager {

    /** Ścieżka katalogu przechowującego portfele graczy. */
    private static final String DIR_PATH = "mods/AICompanion/players/settings/";

    /** Instancja biblioteki GSON z włączonym formatowaniem (Pretty Printing). */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Inicjalizuje strukturę katalogów na dysku serwera.
     * Wywoływane jednorazowo podczas startu modyfikacji.
     */
    public static void init() {
        try {
            Files.createDirectories(Paths.get(DIR_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Zapisuje ustawienia gracza na dysk serwera w formacie JSON (z zachowaniem UTF-8).
     * @param playerUuid Identyfikator UUID gracza.
     * @param settings Obiekt z danymi do zapisania.
     */
    public static void saveSettings(UUID playerUuid, PlayerSettings settings) {
        try (Writer writer = Files.newBufferedWriter(Paths.get(DIR_PATH + playerUuid.toString() + ".json"), StandardCharsets.UTF_8)) {
            GSON.toJson(settings, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Odczytuje zapisane ustawienia gracza z dysku.
     * @param playerUuid Identyfikator UUID gracza.
     * @return Zdeserializowany obiekt PlayerSettings lub null, jeśli plik nie istnieje.
     */
    public static PlayerSettings loadSettings(UUID playerUuid) {
        File file = new File(DIR_PATH + playerUuid.toString() + ".json");
        if (!file.exists()) return null;

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, PlayerSettings.class);
        } catch (IOException e) {
            return null;
        }
    }
}