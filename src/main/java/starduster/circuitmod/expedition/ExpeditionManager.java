package starduster.circuitmod.expedition;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import starduster.circuitmod.Circuitmod;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Manages expedition lifecycle: launching, events, decisions, and completion.
 */
public class ExpeditionManager {
    private static final Random RANDOM = new Random();

    /**
     * Launch a new expedition for a player
     */
    public static Result<Expedition> launchExpedition(ServerWorld world, UUID playerId, 
                                                       ExpeditionDestination destination, int fuelAvailable) {
        ExpeditionRegistry registry = ExpeditionRegistry.get(world);

        // Validation
        if (!registry.canLaunchExpedition(playerId)) {
            return Result.failure("Maximum expeditions already in progress (" + 
                ExpeditionRegistry.MAX_CONCURRENT_EXPEDITIONS + "/" + 
                ExpeditionRegistry.MAX_CONCURRENT_EXPEDITIONS + ")");
        }

        int fuelRequired = destination.getBaseFuelCost();
        if (fuelAvailable < fuelRequired) {
            return Result.failure("Insufficient fuel. Required: " + fuelRequired + ", Available: " + fuelAvailable);
        }

        // Create expedition
        Expedition expedition = registry.createExpedition(playerId, destination, fuelRequired);
        expedition.setStatus(ExpeditionStatus.IN_TRANSIT_OUTBOUND);

        // Calculate event timing (30-70% through the journey)
        long totalJourneyMs = destination.getBaseTimeMs();
        double eventTiming = 0.3 + (RANDOM.nextDouble() * 0.4);
        expedition.setEventTriggerTimestamp(expedition.getLaunchTimestamp() + (long)(totalJourneyMs * eventTiming));
        expedition.setExpectedReturnTimestamp(expedition.getLaunchTimestamp() + totalJourneyMs);

        Circuitmod.LOGGER.info("[EXPEDITION] Launched expedition {} to {} for player {}", 
            expedition.getExpeditionId(), destination.getDisplayName(), playerId);

        return Result.success(expedition);
    }

    /**
     * Trigger the mid-journey event for an expedition
     */
    public static void triggerExpeditionEvent(Expedition expedition, ServerWorld world) {
        if (expedition.getStatus() != ExpeditionStatus.IN_TRANSIT_OUTBOUND) {
            return;
        }

        // Generate random event
        ExpeditionEvent event = ExpeditionEvent.generateRandomEvent(expedition.getDestination(), RANDOM);
        expedition.setPendingEvent(event);
        expedition.setStatus(ExpeditionStatus.AWAITING_DECISION);

        Circuitmod.LOGGER.info("[EXPEDITION] Event triggered for expedition {}: {}", 
            expedition.getExpeditionId(), event.getTitle());

        // Notify player if online
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(expedition.getPlayerId());
        if (player != null) {
            player.sendMessage(Text.literal("[Expedition] " + expedition.getDestination().getDisplayName() + 
                " - EVENT: " + event.getTitle() + " - Decision required!"), false);
        }
    }

    /**
     * Process player's decision for a pending event
     */
    public static Result<String> processPlayerDecision(ServerWorld world, Expedition expedition, boolean choseOptionB) {
        if (expedition.getStatus() != ExpeditionStatus.AWAITING_DECISION || expedition.getPendingEvent() == null) {
            return Result.failure("No decision pending for this expedition");
        }

        ExpeditionEvent event = expedition.getPendingEvent();
        EventChoice chosenOption = choseOptionB ? event.getChoiceB() : event.getChoiceA();

        // Apply choice modifiers
        expedition.modifySuccessChance(chosenOption.getSuccessChanceModifier());
        expedition.multiplyLootModifier(chosenOption.getLootModifier());

        // Check for instant failure
        if (RANDOM.nextDouble() < chosenOption.getInstantFailChance()) {
            expedition.setStatus(ExpeditionStatus.COMPLETED_FAILURE);
            expedition.setPendingEvent(null);
            expedition.clearLoot();

            String failureMessage = getFailureMessage(event.getType());
            Circuitmod.LOGGER.info("[EXPEDITION] Expedition {} failed due to risky choice: {}", 
                expedition.getExpeditionId(), failureMessage);

            return Result.success("DISASTER: " + failureMessage + " The expedition has been lost.");
        }

        // Check for bonus loot
        if (RANDOM.nextDouble() < chosenOption.getBonusLootChance()) {
            List<ItemStack> bonusLoot = ExpeditionLootTable.rollBonusLoot(expedition, RANDOM);
            expedition.addLoot(bonusLoot);
            Circuitmod.LOGGER.info("[EXPEDITION] Bonus loot awarded for expedition {}", 
                expedition.getExpeditionId());
        }

        // Clear event and resume expedition
        String outcomeMessage = chosenOption.getOutcomeDescription();
        expedition.setPendingEvent(null);
        expedition.setStatus(ExpeditionStatus.IN_TRANSIT_RETURN);

        // Recalculate return time
        long now = System.currentTimeMillis();
        long originalTotalTime = expedition.getDestination().getBaseTimeMs();
        long elapsedTime = now - expedition.getLaunchTimestamp();
        long remainingTime = Math.max(originalTotalTime - elapsedTime, 60000); // At least 1 minute
        expedition.setExpectedReturnTimestamp(now + remainingTime);

        Circuitmod.LOGGER.info("[EXPEDITION] Expedition {} resuming return journey, ETA: {}ms", 
            expedition.getExpeditionId(), remainingTime);

        return Result.success(outcomeMessage);
    }

    /**
     * Complete an expedition (success or failure based on success chance)
     */
    public static void completeExpedition(Expedition expedition, ServerWorld world) {
        if (expedition.getStatus() != ExpeditionStatus.IN_TRANSIT_RETURN) {
            return;
        }

        boolean success = RANDOM.nextDouble() < expedition.getCurrentSuccessChance();

        if (success) {
            expedition.setStatus(ExpeditionStatus.COMPLETED_SUCCESS);
            List<ItemStack> finalLoot = ExpeditionLootTable.generateLoot(expedition, RANDOM);
            expedition.addLoot(finalLoot);

            Circuitmod.LOGGER.info("[EXPEDITION] Expedition {} completed successfully with {} loot items", 
                expedition.getExpeditionId(), expedition.getCollectedLoot().size());
        } else {
            expedition.setStatus(ExpeditionStatus.COMPLETED_FAILURE);
            expedition.clearLoot();

            Circuitmod.LOGGER.info("[EXPEDITION] Expedition {} failed on return", 
                expedition.getExpeditionId());
        }

        // Notify player if online
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(expedition.getPlayerId());
        if (player != null) {
            String message = success 
                ? "[Expedition] " + expedition.getDestination().getDisplayName() + " returned successfully! Claim your rewards."
                : "[Expedition] " + expedition.getDestination().getDisplayName() + " was lost. No cargo recovered.";
            player.sendMessage(Text.literal(message), false);
        }
    }

    /**
     * Claim loot from a completed expedition
     */
    public static Result<List<ItemStack>> claimExpeditionLoot(ServerWorld world, String expeditionId, UUID playerId) {
        ExpeditionRegistry registry = ExpeditionRegistry.get(world);
        
        Expedition expedition = registry.getExpedition(expeditionId).orElse(null);
        if (expedition == null) {
            return Result.failure("Expedition not found");
        }

        if (!expedition.getPlayerId().equals(playerId)) {
            return Result.failure("This is not your expedition");
        }

        if (!expedition.getStatus().canClaim()) {
            return Result.failure("Expedition is still in progress");
        }

        List<ItemStack> loot = expedition.getCollectedLoot().stream()
            .map(ItemStack::copy)
            .toList();

        // Remove from registry
        registry.removeExpedition(expeditionId);

        return Result.success(loot);
    }

    private static String getFailureMessage(EventType eventType) {
        return switch (eventType) {
            case ASTEROID_COLLISION -> "The ship was destroyed by the asteroid impact!";
            case EQUIPMENT_MALFUNCTION -> "Critical equipment failure caused a catastrophic explosion!";
            case MYSTERIOUS_SIGNAL -> "The signal was a trap! Pirates ambushed and destroyed the ship!";
            case PIRATE_ENCOUNTER -> "The pirates caught up and destroyed the vessel!";
            case RADIATION_STORM -> "The radiation storm overwhelmed the shields!";
            case RICH_DEPOSIT_FOUND -> "The cavern collapsed, burying the ship!";
            case CREW_ILLNESS -> "The illness spread to critical crew members, causing system failure!";
            case NAVIGATION_ERROR -> "The ship flew into an asteroid field and was lost!";
            case ALIEN_ARTIFACT -> "The artifact activated and destroyed the ship in a mysterious energy burst!";
            case HULL_BREACH -> "The hull breach spread, causing total structural failure!";
        };
    }

    /**
     * Simple result wrapper for expedition operations
     */
    public record Result<T>(boolean success, T value, String errorMessage) {
        public static <T> Result<T> success(T value) {
            return new Result<>(true, value, null);
        }

        public static <T> Result<T> failure(String message) {
            return new Result<>(false, null, message);
        }
    }
}

