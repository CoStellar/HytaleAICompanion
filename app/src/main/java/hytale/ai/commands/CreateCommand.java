package hytale.ai.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.ArchetypeRegistry;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerSettings;
import hytale.ai.config.WizardState;

import java.util.UUID;

/**
 * Krok 1 kreatora postaci: gracz podaje imie kompana, a system wyswietla
 * pelna liste archetypow do wyboru. Nastepnym krokiem jest !ai -pick <numer>.
 */
public class CreateCommand implements AICommand {

    @Override
    public void execute(PlayerRef sender, UUID playerUuid, String[] args, HytaleAIMod mod) {
        if (args.length < 2) {
            sender.sendMessage(Message.raw("[System] Uzycie: !ai -create <Imie>"));
            return;
        }

        PlayerSettings settings = mod.getPlayerSettingsMap().get(playerUuid);
        if (settings == null || !settings.hasKey()) {
            sender.sendMessage(Message.raw("[System] Najpierw musisz dodac klucz API: !ai -setup <klucz>"));
            return;
        }

        String newName = args[1].trim();
        if (newName.isEmpty()) {
            sender.sendMessage(Message.raw("[System] Imie nie moze byc puste."));
            return;
        }

        CompanionProfile profile = mod.getPlayerProfiles().getOrDefault(playerUuid, new CompanionProfile());

        Runnable startWizard = () -> {
            WizardState wizard = new WizardState(newName);
            mod.getActiveWizards().put(playerUuid, wizard);
            sender.sendMessage(Message.raw(ArchetypeRegistry.get().formatList()));
            sender.sendMessage(Message.raw("[System] Imie: '" + newName + "' | Wpisz !ai -pick <numer> aby wybrac archetyp. (Wygasa za 5 min)"));
        };

        if (profile.getNpcName() != null && !profile.getNpcName().isEmpty()) {
            sender.sendMessage(Message.raw("[System] Masz juz kompana o imieniu '" + profile.getNpcName() + "'. Zostanie zastapiony! Wpisz !ai -confirm."));
            mod.getPendingConfirmations().put(playerUuid, startWizard);
        } else {
            startWizard.run();
        }
    }

    @Override
    public String getDescription() {
        return "Uruchamia kreatora nowego kompana (podaj imie, potem wybierz archetyp).";
    }

}
