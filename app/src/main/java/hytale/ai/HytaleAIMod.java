package hytale.ai;

import hytale.ai.api.GeminiProvider;
import hytale.ai.commands.CommandManager;
import hytale.ai.config.*;
import hytale.ai.npc.*;
import hytale.ai.rl.CompanionCombatSystem;
import hytale.ai.rl.QLearningAgent;
import hytale.ai.ui.CompanionControlPage;
import hytale.ai.ui.CompanionRespawnHud;
import hytale.ai.ui.CompanionStatusHud;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.Message;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

public class HytaleAIMod extends JavaPlugin {

    public static final Logger LOGGER = Logger.getLogger("AI-NPC");
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private final Map<UUID, NpcBrain> activeCompanions = new ConcurrentHashMap<>();
    private final Map<UUID, CompanionProfile> playerProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSettings> playerSettingsMap = new ConcurrentHashMap<>();
    private final Map<UUID, Ref<EntityStore>> activeCompanionEntities = new ConcurrentHashMap<>();
    private final Map<UUID, CompanionController> activeControllers = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> pendingConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, WizardState> activeWizards = new ConcurrentHashMap<>();

    private final Map<UUID, QLearningAgent> playerAgents = new ConcurrentHashMap<>();
    private final Map<UUID, CompanionStatusHud> playerHuds = new ConcurrentHashMap<>();
    private final Map<UUID, CompanionRespawnHud> playerRespawnHuds = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> respawnTimerFutures = new ConcurrentHashMap<>();

    // O key (GameModeSwap) → blocked to prevent default game action
    private PacketFilter oKeyFilter;
    // Left Alt (walk toggle) → open companion panel
    private PacketFilter altKeyFilter;
    // Per-player walk-state for rising-edge detection
    private final Map<UUID, Boolean> altPrevState = new ConcurrentHashMap<>();

    public HytaleAIMod(JavaPluginInit init) {
        super(init);
    }

    // --- Publiczne akcesory ---
    public Map<UUID, CompanionProfile> getPlayerProfiles() { return playerProfiles; }
    public Map<UUID, PlayerSettings> getPlayerSettingsMap() { return playerSettingsMap; }
    public Map<UUID, Runnable> getPendingConfirmations() { return pendingConfirmations; }
    public Map<UUID, Ref<EntityStore>> getActiveCompanionEntities() { return activeCompanionEntities; }
    public Map<UUID, NpcBrain> getActiveCompanions() { return activeCompanions; }
    public Map<UUID, CompanionController> getActiveControllers() { return activeControllers; }
    public Map<UUID, WizardState> getActiveWizards() { return activeWizards; }

    public QLearningAgent getAgentForPlayer(UUID uuid) { return playerAgents.get(uuid); }
    public CompanionStatusHud getPlayerHud(UUID uuid) { return playerHuds.get(uuid); }

    @Override
    public void setup0() {
        super.setup0();
        LOGGER.info("[AI NPC] Inicjalizacja zaawansowanego systemu kompanow...");

        PlayerProfileManager.init();
        PlayerSettingsManager.init();
        ArchetypeRegistry.get().load();
        TalkLines.get().load();

        // --- Zdarzenia gracza ---
        HytaleServer.get().getEventBus().registerGlobal(PlayerConnectEvent.class, event -> {
            UUID playerUuid = event.getPlayerRef().getUuid();

            CompanionProfile profile = PlayerProfileManager.loadProfile(playerUuid);
            if (profile != null) {
                playerProfiles.put(playerUuid, profile);
            }

            PlayerSettings settings = PlayerSettingsManager.loadSettings(playerUuid);
            if (settings != null) {
                playerSettingsMap.put(playerUuid, settings);
            }

            QLearningAgent agent = new QLearningAgent(playerUuid);
            agent.loadOrInitFromBase();
            playerAgents.put(playerUuid, agent);

            LOGGER.info("[HytaleAI] Zaladowano dane gracza: " + playerUuid);
        });

        HytaleServer.get().getEventBus().registerGlobal(PlayerDisconnectEvent.class, event -> {
            UUID playerUuid = event.getPlayerRef().getUuid();
            CompanionProfile profile = playerProfiles.get(playerUuid);

            if (profile == null) profile = PlayerProfileManager.loadProfile(playerUuid);

            if (profile != null) {
                profile.setSummoned(false);
                PlayerProfileManager.saveProfile(playerUuid, profile);
            }

            if (event.getPlayerRef().getReference() != null && event.getPlayerRef().getReference().getStore() != null) {
                despawnCompanion(playerUuid, event.getPlayerRef().getReference().getStore());
            }

            activeCompanions.remove(playerUuid);
            playerProfiles.remove(playerUuid);
            playerSettingsMap.remove(playerUuid);
            activeControllers.remove(playerUuid);
            activeWizards.remove(playerUuid);

            QLearningAgent agent = playerAgents.remove(playerUuid);
            if (agent != null) agent.save();

            altPrevState.remove(playerUuid);
            hideRespawnHud(playerUuid);
            LOGGER.info("[HytaleAI] Wyczyszczono dane gracza: " + playerUuid);
        });

        // --- System ECS: smierc kompana ---
        getEntityStoreRegistry().registerSystem(new RefSystem<EntityStore>() {
            @Override
            public Query<EntityStore> getQuery() {
                return Query.and(DeathComponent.getComponentType());
            }

            @Override
            public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
                                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
                UUID deadOwner = null;
                for (Map.Entry<UUID, Ref<EntityStore>> entry : activeCompanionEntities.entrySet()) {
                    if (entry.getValue().equals(ref)) {
                        deadOwner = entry.getKey();
                        break;
                    }
                }

                if (deadOwner != null) {
                    CompanionProfile profile = playerProfiles.get(deadOwner);
                    if (profile != null) {
                        profile.setSummoned(false);
                        profile.setDeathTimestamp(System.currentTimeMillis());
                        PlayerProfileManager.saveProfile(deadOwner, profile);

                        World world = store.getExternalData().getWorld();
                        PlayerRef ownerRef = null;
                        for (PlayerRef player : world.getPlayerRefs()) {
                            if (player.getUuid().equals(deadOwner)) {
                                player.sendMessage(Message.raw("[System] Twoj kompan " + profile.getNpcName() + " stracil przytomnosc!"));
                                ownerRef = player;
                                break;
                            }
                        }
                        activeCompanionEntities.remove(deadOwner);
                        activeControllers.remove(deadOwner);
                        activeCompanions.remove(deadOwner); // clear NpcBrain so chat re-init on re-summon
                        hideHud(deadOwner);
                        if (ownerRef != null) {
                            showRespawnTimer(ownerRef, deadOwner, profile.getDeathTimestamp());
                        }
                    }
                }
            }

            @Override
            public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
                                        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {}
        });

        // --- Klawisz O (GameModeSwap) → blokuj akcję gry, panel otwiera Alt ---
        oKeyFilter = PacketAdapters.registerInbound((PlayerPacketFilter) (playerRef, packet) -> {
            if (!(packet instanceof SyncInteractionChains interactionChains)) return false;
            if (interactionChains.updates == null) return false;
            for (SyncInteractionChain chain : interactionChains.updates) {
                if (chain.interactionType == InteractionType.GameModeSwap && chain.initial) {
                    return true; // block default game action; panel is now opened via Alt
                }
            }
            return false;
        });

        // --- Lewy Alt (walking toggle) → otwiera panel kompana ---
        altKeyFilter = PacketAdapters.registerInbound((PlayerPacketFilter) (playerRef, packet) -> {
            if (!(packet instanceof ClientMovement movement)) return false;
            UUID uuid = playerRef.getUuid();
            boolean walkNow = movement.movementStates != null && movement.movementStates.walking;
            boolean walkPrev = altPrevState.getOrDefault(uuid, false);
            altPrevState.put(uuid, walkNow);
            if (walkNow && !walkPrev) {
                openCompanionPanel(playerRef, uuid);
            }
            return false;
        });

        new CommandManager(this);
    }

    /** Otwiera CompanionControlPage na wątku świata dla podanego gracza. */
    private void openCompanionPanel(PlayerRef playerRef, UUID playerUuid) {
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null) return;
        Store<EntityStore> store = entityRef.getStore();
        if (store == null) return;
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (!entityRef.isValid()) return;
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(entityRef, store,
                    new CompanionControlPage(playerRef, playerUuid, HytaleAIMod.this));
        });
    }

    @Override
    protected void start() {
        super.start();
        getEntityStoreRegistry().registerSystem(new CompanionCombatSystem(this));
        getEntityStoreRegistry().registerSystem(new SpontaneousReactionSystem(this));
    }

    @Override
    protected void shutdown() {
        if (oKeyFilter   != null) PacketAdapters.deregisterInbound(oKeyFilter);
        if (altKeyFilter != null) PacketAdapters.deregisterInbound(altKeyFilter);
    }

    public void processNaturalConversation(PlayerRef sender, UUID playerUuid, String prompt) {
        PlayerSettings settings = playerSettingsMap.get(playerUuid);
        CompanionProfile profile = playerProfiles.get(playerUuid);
        CompanionController controller = activeControllers.get(playerUuid);

        if (settings == null || !settings.hasKey()) {
            sender.sendMessage(Message.raw("[System] Brak klucza API (!ai -setup <klucz>)."));
            return;
        }

        if (profile == null || profile.getNpcName() == null) {
            sender.sendMessage(Message.raw("[System] Najpierw musisz stworzyc kompana (!ai -create)."));
            return;
        }

        sender.sendMessage(Message.raw("Ty do [" + profile.getNpcName() + "]: " + prompt));

        NpcBrain brain = activeCompanions.computeIfAbsent(playerUuid, id -> {
            GeminiProvider provider = new GeminiProvider(settings.getApiKey(), settings.getAiModel());
            return new NpcBrain(profile, playerUuid, provider);
        });

        Ref<EntityStore> companionRef = activeCompanionEntities.get(playerUuid);
        Ref<EntityStore> playerEntityRef = sender.getReference();
        if (playerEntityRef == null) return;
        Store<EntityStore> store = playerEntityRef.getStore();
        if (store == null) return;

        World world = store.getExternalData().getWorld();
        CompletableFuture<String> contextFuture = new CompletableFuture<>();

        world.execute(() -> {
            try {
                String ctx = WorldContextBuilder.buildContext(playerEntityRef, companionRef);
                contextFuture.complete(ctx);
            } catch (Exception e) {
                LOGGER.warning("[AI NPC] Blad budowania kontekstu: " + e.getMessage());
                contextFuture.complete("Brak danych o otoczeniu.\n\n");
            }
        });

        contextFuture.thenCompose(worldContext -> brain.chatWithPlayer(sender.getUsername(), prompt, worldContext, settings.isDebugMode()))
                .thenAccept(aiResponse -> {
                    String cleanResponse = removeDiacritics(aiResponse.dialogue());
                    sender.sendMessage(Message.raw("[" + profile.getNpcName() + "] " + cleanResponse));

                    if (settings.isDebugMode()) {
                        LOGGER.info("[Mysl Kompana]: " + aiResponse.thought_process());
                    }

                    world.execute(() -> {
                        if (controller != null) {
                            controller.handleAiDecision(aiResponse);
                        }
                    });
                }).exceptionally(ex -> {
                    LOGGER.severe("[AI NPC] Blad LLM: " + ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });
    }

    public void spawnCompanion(UUID playerUuid, CompanionProfile profile, Ref<EntityStore> playerRef) {
        if (playerRef == null) return;
        Store<EntityStore> store = playerRef.getStore();
        if (store == null) return;
        World world = store.getExternalData().getWorld();

        world.execute(() -> {
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d pos = transform.getPosition();
            Vector3d spawnPos = new Vector3d(pos.getX() + 2.0, pos.getY(), pos.getZ() + 2.0);

            try {
                String npcId = profile.getRoleId();
                LOGGER.info("[AI NPC] Spawnowanie kompana: ID=" + npcId + " model=" + profile.getInGameModel());

                var result = NPCPlugin.get().spawnNPC(store, npcId, profile.getNpcName(), spawnPos, new Vector3f(0, 0, 0));

                if (result != null && result.first() != null) {
                    Ref<EntityStore> npcRef = result.first();
                    activeCompanionEntities.put(playerUuid, npcRef);

                    QLearningAgent playerAgent = playerAgents.get(playerUuid);
                    CompanionController controller = new CompanionController(npcRef, playerRef, store, playerUuid, playerAgent, profile.getNpcName(), HytaleAIMod.this);
                    activeControllers.put(playerUuid, controller);

                    NPCEntity.setAppearance(npcRef, profile.getInGameModel(), store);

                    hideRespawnHud(playerUuid);
                    PlayerRef pRef = findPlayerRef(world, playerUuid);
                    if (pRef != null) {
                        PlayerSettings hudSettings = playerSettingsMap.get(playerUuid);
                        boolean rlOn = hudSettings != null && hudSettings.isRlEnabled();
                        String stance = profile.getCombatStance() != null ? profile.getCombatStance() : "?";
                        CompanionStatusHud hud = new CompanionStatusHud(pRef, profile.getNpcName(), "Idle", stance, rlOn, -1f);
                        hud.show();
                        playerHuds.put(playerUuid, hud);
                    }

                    LOGGER.info("[AI NPC] Spawning OK: " + profile.getNpcName());
                } else {
                    LOGGER.severe("[AI NPC] spawnNPC zwrocilo NULL dla ID: " + npcId);
                }
            } catch (Exception e) {
                LOGGER.severe("[AI NPC] Blad spawnowania: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void despawnCompanion(UUID playerUuid, Store<EntityStore> store) {
        Ref<EntityStore> entityRef = activeCompanionEntities.remove(playerUuid);
        activeControllers.remove(playerUuid);
        hideHud(playerUuid);
        if (entityRef != null && entityRef.isValid()) {
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                if (entityRef.isValid()) store.removeEntity(entityRef, RemoveReason.REMOVE);
            });
        }
    }

    private void hideHud(UUID playerUuid) {
        CompanionStatusHud hud = playerHuds.remove(playerUuid);
        if (hud != null) hud.hide();
    }

    private void hideRespawnHud(UUID playerUuid) {
        CompanionRespawnHud hud = playerRespawnHuds.remove(playerUuid);
        if (hud != null) hud.hide();
        ScheduledFuture<?> future = respawnTimerFutures.remove(playerUuid);
        if (future != null) future.cancel(false);
    }

    private void showRespawnTimer(PlayerRef playerRef, UUID playerUuid, long deathTimestamp) {
        long cooldownMs = 3L * 60 * 1000;
        long secsLeft = Math.max(1L, (cooldownMs - (System.currentTimeMillis() - deathTimestamp) + 999) / 1000);
        CompanionRespawnHud rHud = new CompanionRespawnHud(playerRef, "Kompan odpoczywa — " + secsLeft + "s");
        rHud.show();
        playerRespawnHuds.put(playerUuid, rHud);

        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            long rem = cooldownMs - (System.currentTimeMillis() - deathTimestamp);
            if (rem <= 0) {
                hideRespawnHud(playerUuid);
            } else {
                long secs = (rem + 999) / 1000;
                CompanionRespawnHud h = playerRespawnHuds.get(playerUuid);
                if (h != null) h.updateText("Kompan odpoczywa — " + secs + "s");
            }
        }, 1, 1, TimeUnit.SECONDS);
        respawnTimerFutures.put(playerUuid, future);
    }

    private PlayerRef findPlayerRef(World world, UUID uuid) {
        for (PlayerRef pRef : world.getPlayerRefs()) {
            if (pRef.getUuid().equals(uuid)) return pRef;
        }
        return null;
    }

    private String removeDiacritics(String text) {
        if (text == null) return "";
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("").replace('ł', 'l').replace('Ł', 'L');
    }
}
