package hytale.ai.commands;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;

/**
 * Kontrakt definiujący strukturę każdej komendy modyfikacji AI.
 * Pozwala na polimorficzne wywoływanie logiki przez Command Managera.
 */
public interface AICommand {

    /**
     * Główna metoda wykonawcza komendy.
     * @param sender Gracz wywołujący komendę.
     * @param playerUuid Identyfikator UUID gracza.
     * @param args Tablica argumentów podanych po nazwie komendy (np. ["-setup", "KLUCZ_API"]).
     * @param mod Główna instancja pluginu (potrzebna np. do spawnowania).
     */
    void execute(PlayerRef sender, UUID playerUuid, String[] args, hytale.ai.HytaleAIMod mod);

    /**
     * Zwraca krótki opis działania komendy (używane przez komendę Help).
     * @return Opis tekstowy.
     */
    String getDescription();
}