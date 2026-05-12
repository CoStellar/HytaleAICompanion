package hytale.ai.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class CompanionRespawnHud extends CustomUIHud {

    public static final String LAYOUT = "hytale_ai/CompanionRespawnHud.ui";

    private String text;

    public CompanionRespawnHud(@Nonnull PlayerRef playerRef, String text) {
        super(playerRef);
        this.text = text;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cmd) {
        cmd.append(LAYOUT);
        cmd.set("#RespawnLabel.Text", text);
    }

    public void updateText(String newText) {
        this.text = newText;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#RespawnLabel.Text", newText);
        update(false, cmd);
    }

    public void hide() {
        update(true, new UICommandBuilder());
    }
}
