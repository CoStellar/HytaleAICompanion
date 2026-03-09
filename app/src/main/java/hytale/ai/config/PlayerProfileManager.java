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
 * Klasa zarządzająca trwałością danych (Persistence) dla profili sztucznej inteligencji.
 * <p>
 * Odpowiada za operacje wejścia/wyjścia (I/O) na plikach `.json` zapisujących
 * aktualny stan, osobowość oraz pamięć krótkotrwałą kompana przypisanego do danego gracza.
 * Zastosowanie kodowania UTF-8 zapobiega błędom parsowania polskich znaków diakrytycznych.
 * </p>
 */
public class PlayerProfileManager {

    /** Główna ścieżka do katalogu przechowującego stany sztucznej inteligencji. */
    private static final String DIR_PATH = "mods/AICompanion/players/";

    /** Instancja biblioteki GSON z włączonym formatowaniem (Pretty Printing). */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Inicjalizuje strukturę katalogów na dysku serwera.
     * Wywoływane jednorazowo podczas cyklu życia pluginu (w metodzie setup).
     */
    public static void init() {
        File dir = new File(DIR_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Odczytuje i deserializuje profil kompana z pliku JSON.
     * @param playerUuid Identyfikator gracza, do którego należy AI.
     * @return Zbudowany obiekt CompanionProfile lub null, jeśli gracz nie utworzył jeszcze kompana.
     */
    public static CompanionProfile loadProfile(UUID playerUuid) {
        File file = new File(DIR_PATH + playerUuid.toString() + ".json");
        if (file.exists()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, CompanionProfile.class);
            } catch (IOException e) {
                System.err.println("[AI NPC] Błąd odczytu pliku gracza: " + playerUuid);
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Zapisuje bieżący stan sztucznej inteligencji (wraz z historią pamięci) do pliku JSON.
     * @param playerUuid Identyfikator gracza.
     * @param profile Obiekt reprezentujący aktualny stan AI.
     */
    public static void saveProfile(UUID playerUuid, CompanionProfile profile) {
        try (Writer writer = Files.newBufferedWriter(Paths.get(DIR_PATH + playerUuid.toString() + ".json"), StandardCharsets.UTF_8)) {
            GSON.toJson(profile, writer);
        } catch (IOException e) {
            System.err.println("[AI NPC] Błąd zapisu pliku gracza: " + playerUuid);
            e.printStackTrace();
        }
    }

    /**
     * Wykonuje twarde usunięcie pliku profilu AI z serwera.
     * Stosowane przy resecie lub permanentnym odesłaniu kompana przez gracza.
     * @param playerUuid Identyfikator UUID gracza zwalniającego AI.
     */
    public static void deleteProfile(UUID playerUuid) {
        File file = new File(DIR_PATH + playerUuid.toString() + ".json");
        if (file.exists()) {
            file.delete();
        }
    }
}