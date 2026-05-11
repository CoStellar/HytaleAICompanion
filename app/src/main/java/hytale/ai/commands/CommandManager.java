package hytale.ai.commands;

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rejestr i parser odpowiedzialny za przekierowywanie ruchu z czatu
 * do odpowiednich klas implementujących interfejs AICommand.
 */
public class CommandManager {

    private final Map<String, AICommand> commands = new HashMap<>();
    private final HytaleAIMod modInstance;

    public CommandManager(HytaleAIMod modInstance) {
        this.modInstance = modInstance;
        registerCommands();
        listenToChat();
    }

    /**
     * Rejestruje wszystkie dostępne komendy w pamięci podręcznej.
     */
    private void registerCommands() {
        commands.put("-help", new HelpCommand(this));
        commands.put("-summon", new SummonCommand());
        commands.put("-confirm", new ConfirmCommand());
        commands.put("-setup", new SetupCommand());
        commands.put("-llm", new LlmCommand());
        commands.put("-create", new CreateCommand());
        commands.put("-pick", new PickCommand());
        commands.put("-dismiss", new DismissCommand());
        commands.put("-info", new InfoCommand());
        commands.put("-personality", new PersonalityCommand());
        commands.put("-stance", new StanceCommand());
        commands.put("-models", new ModelsCommand());
        commands.put("-clearmap", new ClearMapCommand());
        commands.put("-debug", new DebugCommand());
    }

    public Map<String, AICommand> getRegisteredCommands() {
        return commands;
    }

    /**
     * Podpina się pod event czatu. Jeśli wiadomość to komenda (!ai), blokuje jej wyświetlenie
     * i przekazuje logikę do odpowiedniej klasy.
     */
    private void listenToChat() {
        HytaleServer.get().getEventBus().registerGlobal(PlayerChatEvent.class, event -> {
            String message = event.getContent().trim();
            PlayerRef sender = event.getSender();
            UUID playerUuid = sender.getUuid();

            // Przejmujemy tylko wiadomości systemowe (zaczynające się od !ai)
            if (message.toLowerCase().startsWith("!ai ")) {
                event.setCancelled(true); // Blokuje pokazanie "!ai coś tam" innym graczom

                String rawArgs = message.substring(4).trim();
                String[] splitArgs = rawArgs.split("\\s+");
                String commandName = splitArgs[0].toLowerCase();

                // Sprawdzamy, czy gracz wpisał znaną nam komendę
                if (commands.containsKey(commandName)) {
                    AICommand command = commands.get(commandName);
                    command.execute(sender, playerUuid, splitArgs, modInstance);
                } else if (commandName.startsWith("-")) {
                    // Nieznana komenda techniczna (zaczyna się od "-")
                    sender.sendMessage(Message.raw("[System] Nieznana komenda. Wpisz !ai -help"));
                } else {
                    // Zwykła wiadomość do AI (nie zaczyna się od "-")
                    modInstance.processNaturalConversation(sender, playerUuid, rawArgs);
                }
            }
        });
    }
}