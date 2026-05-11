package hytale.ai;

import hytale.ai.api.GeminiProvider;
import hytale.ai.commands.CommandManager;
import hytale.ai.config.*;
import hytale.ai.npc.*;
import hytale.ai.rl.CompanionCombatSystem;
import hytale.ai.rl.QLearningAgent;

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

    private final QLearningAgent rlAgent = new QLearningAgent();

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
    public QLearningAgent getRlAgent() { return rlAgent; }

    @Override
    public void setup0() {
        super.setup0();
        LOGGER.info("[AI NPC] Inicjalizacja zaawansowanego systemu kompanow...");

        PlayerProfileManager.init();
        PlayerSettingsManager.init();
        ArchetypeRegistry.get().load();
        TalkLines.get().load();
        rlAgent.load();

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

            rlAgent.save();
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
                        for (PlayerRef player : world.getPlayerRefs()) {
                            if (player.getUuid().equals(deadOwner)) {
                                player.sendMessage(Message.raw("[System] Twoj kompan " + profile.getNpcName() + " stracil przytomnosc!"));
                                break;
                            }
                        }
                        activeCompanionEntities.remove(deadOwner);
                        activeControllers.remove(deadOwner);
                    }
                }
            }

            @Override
            public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
                                        @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {}
        });

        // --- Systemy RL i reakcji spontanicznych ---
        getEntityStoreRegistry().registerSystem(new CompanionCombatSystem(this, rlAgent));
        getEntityStoreRegistry().registerSystem(new SpontaneousReactionSystem(this));

        new CommandManager(this);
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

                    CompanionController controller = new CompanionController(npcRef, playerRef, store, playerUuid);
                    activeControllers.put(playerUuid, controller);

                    NPCEntity.setAppearance(npcRef, profile.getInGameModel(), store);
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
        if (entityRef != null && entityRef.isValid()) {
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                if (entityRef.isValid()) store.removeEntity(entityRef, RemoveReason.REMOVE);
            });
        }
    }

    private String removeDiacritics(String text) {
        if (text == null) return "";
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("").replace('ł', 'l').replace('Ł', 'L');
    }
}
