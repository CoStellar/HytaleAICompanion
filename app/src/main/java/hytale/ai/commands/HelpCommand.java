package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;

import java.util.Map;
import java.util.UUID;

/**
 * Komenda systemowa wyświetlająca graczowi listę wszystkich dostępnych instrukcji.
 * <p>
 * Działa dynamicznie, iterując po rejestrze CommandManagera, dzięki czemu
 * dodanie nowej komendy do gry nie wymaga ręcznej edycji tekstów pomocy.
 * </p>
 */
public class HelpCommand implements AICommand {

    private final CommandManager manager;

    public HelpCommand(CommandManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        sender.sendMessage(Message.raw("[System] === Dostepne Komendy AI ==="));

        for (Map.Entry<String, AICommand> entry : manager.getRegisteredCommands().entrySet()) {
            sender.sendMessage(Message.raw("!ai " + entry.getKey() + " - " + entry.getValue().getDescription()));
        }

        sender.sendMessage(Message.raw("[System] Aby po prostu porozmawiac, wpisz '!ai <twoja wiadomosc>'."));
    }

    @Override
    public String getDescription() {
        return "Pokazuje liste wszystkich dostepnych komend i ich opisy.";
    }
}