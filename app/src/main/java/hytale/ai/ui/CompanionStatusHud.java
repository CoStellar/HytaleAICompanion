package hytale.ai.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class CompanionStatusHud extends CustomUIHud {

    public static final String LAYOUT = "hytale_ai/CompanionStatusHud.ui";

    private String companionName;
    private String stateName;
    private String stance;
    private boolean rlEnabled;
    private float hpPercent;

    public CompanionStatusHud(@Nonnull PlayerRef playerRef,
                               String companionName, String stateName,
                               String stance, boolean rlEnabled, float hpPercent) {
        super(playerRef);
        this.companionName = companionName;
        this.stateName     = stateName;
        this.stance        = stance;
        this.rlEnabled     = rlEnabled;
        this.hpPercent     = hpPercent;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cmd) {
        cmd.append(LAYOUT);
        applyValues(cmd);
    }

    public void updateValues(String stateName, String stance, boolean rlEnabled, float hpPercent) {
        this.stateName  = stateName;
        this.stance     = stance;
        this.rlEnabled  = rlEnabled;
        this.hpPercent  = hpPercent;

        UICommandBuilder cmd = new UICommandBuilder();
        applyValues(cmd);
        update(false, cmd);
    }

    public void updateHp(float hpPercent) {
        this.hpPercent = hpPercent;
        String hpText = hpPercent < 0 ? "HP: --" : "HP: " + Math.round(hpPercent * 100) + "%";
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#HpLabel.Text", hpText);
        update(false, cmd);
    }

    public void hide() {
        update(true, new UICommandBuilder());
    }

    private void applyValues(UICommandBuilder cmd) {
        cmd.set("#CompanionName.Text", companionName != null ? companionName : "?");
        cmd.set("#CompanionState.Text", "Stan: " + (stateName != null ? stateName : "?"));
        cmd.set("#StanceLabel.Text", stance != null ? stance : "?");
        cmd.set("#RlStatus.Text", "RL: " + (rlEnabled ? "WL" : "WYL"));

        String hpText;
        if (hpPercent < 0) {
            hpText = "HP: --";
        } else {
            int pct = Math.round(hpPercent * 100);
            hpText = "HP: " + pct + "%";
        }
        cmd.set("#HpLabel.Text", hpText);
    }
}
