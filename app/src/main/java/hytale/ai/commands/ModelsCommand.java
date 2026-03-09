package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Funkcja diagnostyczna typu "Read-Only". Odpytuje systemowe struktury danych
 * (rejestry Assetów silnika Hytale) w celu wyświetlenia ich stanu graczowi.
 * <p>
 * Posiada wbudowany system paginacji (stronicowania), aby uniknąć przepełnienia czatu.
 * </p>
 */
public class ModelsCommand implements AICommand {

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        try {
            Set<String> allModelKeys = com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAssetMap().keySet();
            List<String> models = new ArrayList<>(allModelKeys);
            models.sort(String::compareTo);

            int itemsPerPage = 15;
            int page = 1;

            if (args.length > 1) {
                try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
            }

            int totalPages = (int) Math.ceil((double) models.size() / itemsPerPage);
            if (page < 1) page = 1;
            if (page > totalPages) page = totalPages;

            int startIndex = (page - 1) * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, models.size());
            List<String> pageModels = models.subList(startIndex, endIndex);

            String modelsString = String.join(", ", pageModels);
            sender.sendMessage(Message.raw("[System] Modele (Strona " + page + "/" + totalPages + "):"));
            sender.sendMessage(Message.raw("[System] " + modelsString));

            if (page < totalPages) {
                sender.sendMessage(Message.raw("[System] Wpisz '!ai -models " + (page + 1) + "' dla kolejnej strony."));
            }
        } catch (Exception e) {
            sender.sendMessage(Message.raw("[System] Blad podczas pobierania listy modeli."));
        }
    }

    @Override
    public String getDescription() {
        return "Wyswietla liste wszystkich modeli dostepnych do przywolania.";
    }
}