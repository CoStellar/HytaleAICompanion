package hytale.ai;

import hytale.ai.api.GeminiProvider;
import hytale.ai.commands.CommandManager;
import hytale.ai.config.CompanionProfile;
import hytale.ai.config.PlayerProfileManager;
import hytale.ai.config.PlayerSettings;
import hytale.ai.config.PlayerSettingsManager;
import hytale.ai.npc.*;

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
import javax.annotation.Nonnull;

/**
 * Główna klasa modyfikacji (Entry Point) implementująca inteligentnych kompanów NPC w grze Hytale.
 */
public class HytaleAIMod extends JavaPlugin {

    public static final Logger LOGGER = Logger.getLogger("AI-NPC");

    private final Map<UUID, NpcBrain> activeCompanions = new ConcurrentHashMap<>();
    private final Map<UUID, CompanionProfile> playerProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSettings> playerSettingsMap = new ConcurrentHashMap<>();
    private final Map<UUID, Ref<EntityStore>> activeCompanionEntities = new ConcurrentHashMap<>();
    private final Map<UUID, CompanionController> activeControllers = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> pendingConfirmations = new ConcurrentHashMap<>();

    public HytaleAIMod(JavaPluginInit init) {
        super(init);
    }

    public Map<UUID, CompanionProfile> getPlayerProfiles() { return playerProfiles; }
    public Map<UUID, PlayerSettings> getPlayerSettingsMap() { return playerSettingsMap; }
    public Map<UUID, Runnable> getPendingConfirmations() { return pendingConfirmations; }
    public Map<UUID, Ref<EntityStore>> getActiveCompanionEntities() { return activeCompanionEntities; }
    public Map<UUID, NpcBrain> getActiveCompanions() { return activeCompanions; }

    @Override
    public void setup0() {
        super.setup0();
        LOGGER.info("[AI NPC] Inicjalizacja zaawansowanego systemu kompanow...");

        PlayerProfileManager.init();
        PlayerSettingsManager.init();

        // ---------------------------------------------------------
        // REJESTRACJA ZDARZEŃ GRACZA (DOŁĄCZENIE / WYJŚCIE)
        // ---------------------------------------------------------

        // 1. Ładowanie danych z dysku do RAM po wejściu na serwer
        HytaleServer.get().getEventBus().registerGlobal(PlayerConnectEvent.class, event -> {
            UUID playerUuid = event.getPlayerRef().getUuid();

            // Wczytanie profilu kompana do pamięci RAM
            CompanionProfile profile = PlayerProfileManager.loadProfile(playerUuid);
            if (profile != null) {
                playerProfiles.put(playerUuid, profile);
            }

            // Wczytanie ustawień (np. klucza API) do pamięci RAM
            PlayerSettings settings = PlayerSettingsManager.loadSettings(playerUuid);
            if (settings != null) {
                playerSettingsMap.put(playerUuid, settings);
            }

            LOGGER.info("[HytaleAI] Załadowano dane z dysku dla gracza: " + playerUuid);
        });

        // 2. Czyszczenie pamięci i odspawnowanie po wyjściu z serwera
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

            // Usunięcie z cache, zapobiega wyciekom pamięci
            activeCompanions.remove(playerUuid);
            playerProfiles.remove(playerUuid);
            playerSettingsMap.remove(playerUuid);
            activeControllers.remove(playerUuid);

            LOGGER.info("[HytaleAI] Wyczyszczono profil z pamięci RAM dla: " + playerUuid);
        });

        // ---------------------------------------------------------
        // SYSTEMY ECS (ŚMIERĆ KOMPANA ITD.)
        // ---------------------------------------------------------

        getEntityStoreRegistry().registerSystem(new RefSystem<EntityStore>() {
            @Override
            public Query<EntityStore> getQuery() {
                return Query.and(DeathComponent.getComponentType());
            }

            @Override
            public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
                UUID deadPlayerUuid = null;
                for (Map.Entry<UUID, Ref<EntityStore>> entry : activeCompanionEntities.entrySet()) {
                    if (entry.getValue().equals(ref)) {
                        deadPlayerUuid = entry.getKey();
                        break;
                    }
                }

                if (deadPlayerUuid != null) {
                    CompanionProfile profile = playerProfiles.get(deadPlayerUuid);
                    if (profile != null) {
                        profile.setSummoned(false);
                        profile.setDeathTimestamp(System.currentTimeMillis());
                        PlayerProfileManager.saveProfile(deadPlayerUuid, profile);

                        World world = store.getExternalData().getWorld();
                        for (PlayerRef player : world.getPlayerRefs()) {
                            if (player.getUuid().equals(deadPlayerUuid)) {
                                player.sendMessage(Message.raw("[System] Twoj kompan " + profile.getNpcName() + " stracil przytomnosc!"));
                                break;
                            }
                        }
                        activeCompanionEntities.remove(deadPlayerUuid);
                        activeControllers.remove(deadPlayerUuid);
                    }
                }
            }
            @Override
            public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {}
        });

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
        Store<EntityStore> store = sender.getReference().getStore();

        if(store == null) return;
        World world = store.getExternalData().getWorld();
        CompletableFuture<String> contextFuture = new CompletableFuture<>();

        world.execute(() -> {
            try {
                String ctx = hytale.ai.npc.WorldContextBuilder.buildContext(sender.getReference(), companionRef);
                contextFuture.complete(ctx);
            } catch (Exception e) {
                LOGGER.warning("[AI NPC] Blad budowania kontekstu: " + e.getMessage());
                contextFuture.complete("Brak danych o otoczeniu.\n\n");
            }
        });

        contextFuture.thenCompose(worldContext -> brain.chatWithPlayer(sender.getUsername(), prompt, worldContext, settings.isDebugMode()))
                .thenAccept(aiResponse -> {
                    // Wyświetlamy graczowi dialog (to jest bezpieczne z wątku pobocznego)
                    String cleanResponse = removeDiacritics(aiResponse.dialogue());
                    sender.sendMessage(Message.raw("[" + profile.getNpcName() + "] " + cleanResponse));

                    // Wypisujemy monolog do konsoli serwera
                    if (settings.isDebugMode()) {
                        LOGGER.info("[Wewnętrzna Myśl Kompana]: " + aiResponse.thought_process());
                    }

                    // KLUCZOWA ZMIANA: Wrzucamy wykonanie akcji fizycznej na GŁÓWNY WĄTEK silnika Hytale!
                    world.execute(() -> {
                        if (controller != null) {
                            controller.handleAiDecision(aiResponse);
                        }
                    });
                }).exceptionally(ex -> {
                    // Dodajemy wyłapywanie błędów, żeby ciche błędy z wątków już nas nie dręczyły
                    LOGGER.severe("[AI NPC] Blad podczas przetwarzania odpowiedzi LLM: " + ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });
    }

    public void spawnCompanion(UUID playerUuid, CompanionProfile profile, Ref<EntityStore> playerRef) {
        Store<EntityStore> store = playerRef.getStore();
        if(store == null) return;
        World world = store.getExternalData().getWorld();

        world.execute(() -> {
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d playerPos = transform.getPosition();
            Vector3d spawnPos = new Vector3d(playerPos.getX() + 2.0, playerPos.getY(), playerPos.getZ() + 2.0);

            try {
                // Używamy samej nazwy pliku JSON jako ID (bez prefixu hytale_ai:)
                String npcId = "ai_companion";
                LOGGER.info("[AI NPC] Próba zespawnowania NPC o ID: " + npcId);

                var result = NPCPlugin.get().spawnNPC(store, npcId, profile.getNpcName(), spawnPos, new Vector3f(0, 0, 0));

                if (result != null && result.first() != null) {
                    Ref<EntityStore> npcRef = result.first();
                    activeCompanionEntities.put(playerUuid, npcRef);

                    // Przekazujemy: 1. Ref bota, 2. Ref gracza (pobrany z argumentu metody), 3. Store
                    CompanionController controller = new CompanionController(npcRef, playerRef, store);
                    activeControllers.put(playerUuid, controller);

                    NPCEntity.setAppearance(npcRef, profile.getInGameModel(), store);
                    LOGGER.info("[AI NPC] SUKCES! Zespawnowano kompana: " + profile.getNpcName());
                } else {
                    // Jeśli wejdzie tutaj, to znaczy że ID jest ciągle złe
                    LOGGER.severe("[AI NPC] BŁĄD: spawnNPC zwróciło NULL! Silnik nie rozpoznaje ID: " + npcId);
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
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").replace('ł', 'l').replace('Ł', 'L');
    }
}