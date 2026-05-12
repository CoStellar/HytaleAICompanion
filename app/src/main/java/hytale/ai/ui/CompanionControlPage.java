package hytale.ai.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import hytale.ai.CompanionActions;
import hytale.ai.HytaleAIMod;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerSettings;
import hytale.ai.npc.CompanionController;
import hytale.ai.rl.QLearningAgent;

import javax.annotation.Nonnull;
import java.util.UUID;

public class CompanionControlPage extends InteractiveCustomUIPage<CompanionControlPage.PageEventData> {

    public static final String LAYOUT = "hytale_ai/CompanionControlPage.ui";

    private final HytaleAIMod mod;
    private final UUID playerUuid;

    public CompanionControlPage(@Nonnull PlayerRef playerRef, UUID playerUuid, HytaleAIMod mod) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.mod = mod;
        this.playerUuid = playerUuid;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        refreshStatus(cmd);
        bindEvents(events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                 @Nonnull PageEventData data) {
        if (data.action == null) {
            sendUpdate(null, false);
            return;
        }

        switch (data.action) {
            case "close"             -> this.close();
            case "send_chat"         -> handleSendChat(data.chatText);
            case "stance_passive"    -> handleStance("PASYWNY");
            case "stance_defensive"  -> handleStance("DEFENSYWNY");
            case "stance_aggressive" -> handleStance("AGRESYWNY");
            case "summon"            -> handleSummon(ref, store);
            case "rl_toggle"         -> handleRlToggle();
            case "debug_toggle"      -> handleDebugToggle();
            case "model_flash"       -> handleModel("gemini-2.5-flash");
            case "model_flash20"     -> handleModel("gemini-2.0-flash");
            case "model_pro"         -> handleModel("gemini-1.5-pro");
            default                  -> sendUpdate(null, false);
        }
    }

    // ── Action handlers ────────────────────────────────────────────────────────

    private void handleSendChat(String chatText) {
        if (chatText != null && !chatText.isBlank()) {
            mod.processNaturalConversation(playerRef, playerUuid, chatText);
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#ChatInput.Value", "");
            refreshStatus(cmd);
            sendUpdate(cmd, false);
        } else {
            sendUpdate(null, false);
        }
    }

    private void handleStance(String stance) {
        CompanionActions.setStance(playerUuid, stance, mod);
        UICommandBuilder cmd = new UICommandBuilder();
        refreshStatus(cmd);
        sendUpdate(cmd, false);
    }

    private void handleSummon(Ref<EntityStore> ref, Store<EntityStore> store) {
        CompanionProfile profile = mod.getPlayerProfiles().get(playerUuid);
        UICommandBuilder cmd = new UICommandBuilder();

        if (profile == null || profile.getNpcName() == null) {
            cmd.set("#StatusLine1.Text", "Brak kompana — uzyj !ai -create.");
            sendUpdate(cmd, false);
            return;
        }

        if (!profile.isSummoned() && profile.getDeathTimestamp() > 0) {
            long cooldownMs = 3L * 60 * 1000;
            long elapsed = System.currentTimeMillis() - profile.getDeathTimestamp();
            if (elapsed < cooldownMs) {
                long secondsLeft = (cooldownMs - elapsed) / 1000;
                cmd.set("#StatusLine1.Text", profile.getNpcName() + " odpoczywa — " + secondsLeft + "s cooldown.");
                sendUpdate(cmd, false);
                return;
            }
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef != null) {
            CompanionActions.summonOrDismiss(playerUuid, playerEntityRef, store, mod);
        }
        refreshStatus(cmd);
        sendUpdate(cmd, false);
    }

    private void handleRlToggle() {
        CompanionActions.toggleRl(playerUuid, mod);
        UICommandBuilder cmd = new UICommandBuilder();
        refreshStatus(cmd);
        sendUpdate(cmd, false);
    }

    private void handleDebugToggle() {
        CompanionActions.toggleDebug(playerUuid, mod);
        UICommandBuilder cmd = new UICommandBuilder();
        refreshStatus(cmd);
        sendUpdate(cmd, false);
    }

    private void handleModel(String model) {
        CompanionActions.setLlmModel(playerUuid, model, mod);
        UICommandBuilder cmd = new UICommandBuilder();
        refreshStatus(cmd);
        sendUpdate(cmd, false);
    }

    // ── UI state refresh ───────────────────────────────────────────────────────

    private void refreshStatus(UICommandBuilder cmd) {
        CompanionProfile profile  = mod.getPlayerProfiles().get(playerUuid);
        PlayerSettings   settings = mod.getPlayerSettingsMap().get(playerUuid);
        QLearningAgent   agent    = mod.getAgentForPlayer(playerUuid);

        boolean summoned = profile != null && profile.isSummoned();
        cmd.set("#SummonBtn.Text", summoned ? "Oddal" : "Przywolaj");

        boolean rlOn = settings != null && settings.isRlEnabled();
        cmd.set("#RlBtn.Text", "RL: " + (rlOn ? "WL" : "WYL"));

        boolean debugOn = settings != null && settings.isDebugMode();
        cmd.set("#DebugBtn.Text", "Debug: " + (debugOn ? "WL" : "WYL"));

        String state = "?";
        CompanionController ctrl = mod.getActiveControllers().get(playerUuid);
        if (ctrl != null) state = ctrl.getLastState();

        String stance = profile != null ? profile.getCombatStance() : "?";
        cmd.set("#StatusLine1.Text", "Stan: " + state + "  |  Nastawienie: " + stance);

        String modelName = settings != null ? settings.getAiModel() : "?";
        String agentInfo = agent != null
                ? "Decyzji: " + agent.getTotalDecisions() + "  |  e=" + String.format("%.3f", agent.getEpsilon())
                : "";
        cmd.set("#StatusLine2.Text", "Model: " + modelName + "  |  RL: " + (rlOn ? "WL" : "WYL") + "  |  " + agentInfo);
    }

    // ── Event bindings ────────────────────────────────────────────────────────

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn",
                new EventData().append("Action", "close"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#SendBtn",
                new EventData().append("Action", "send_chat").append("@ChatText", "#ChatInput.Value"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#StancePassiveBtn",
                new EventData().append("Action", "stance_passive"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#StanceDefensiveBtn",
                new EventData().append("Action", "stance_defensive"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#StanceAggressiveBtn",
                new EventData().append("Action", "stance_aggressive"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#SummonBtn",
                new EventData().append("Action", "summon"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RlBtn",
                new EventData().append("Action", "rl_toggle"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DebugBtn",
                new EventData().append("Action", "debug_toggle"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModelFlashBtn",
                new EventData().append("Action", "model_flash"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModelFlash20Btn",
                new EventData().append("Action", "model_flash20"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModelProBtn",
                new EventData().append("Action", "model_pro"), false);
    }

    // ── Event data codec ──────────────────────────────────────────────────────

    public static class PageEventData {
        public static final BuilderCodec<PageEventData> CODEC =
                BuilderCodec.builder(PageEventData.class, PageEventData::new)
                        .append(new KeyedCodec<>("Action",    Codec.STRING),
                                (e, s) -> e.action   = s, e -> e.action)
                        .add()
                        .append(new KeyedCodec<>("@ChatText", Codec.STRING),
                                (e, s) -> e.chatText = s, e -> e.chatText)
                        .add()
                        .build();

        String action;
        String chatText;

        public PageEventData() {}
    }
}
