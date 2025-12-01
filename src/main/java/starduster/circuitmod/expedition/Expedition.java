package starduster.circuitmod.expedition;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an active or completed expedition.
 */
public class Expedition {
    private final String expeditionId;
    private final UUID playerId;
    private final ExpeditionDestination destination;
    private ExpeditionStatus status;
    
    private final long launchTimestamp;
    private long eventTriggerTimestamp;
    private long expectedReturnTimestamp;
    
    @Nullable
    private ExpeditionEvent pendingEvent;
    
    private int fuelSpent;
    private double currentSuccessChance;
    private double lootModifier;
    
    private final List<ItemStack> collectedLoot;

    public Expedition(String expeditionId, UUID playerId, ExpeditionDestination destination,
                     long launchTimestamp, int fuelSpent) {
        this.expeditionId = expeditionId;
        this.playerId = playerId;
        this.destination = destination;
        this.status = ExpeditionStatus.AWAITING_LAUNCH;
        this.launchTimestamp = launchTimestamp;
        this.fuelSpent = fuelSpent;
        this.currentSuccessChance = 1.0 - destination.getRiskLevel();
        this.lootModifier = 1.0;
        this.collectedLoot = new ArrayList<>();
    }

    // Private constructor for NBT loading
    private Expedition(String expeditionId, UUID playerId, ExpeditionDestination destination,
                      ExpeditionStatus status, long launchTimestamp, long eventTriggerTimestamp,
                      long expectedReturnTimestamp, @Nullable ExpeditionEvent pendingEvent,
                      int fuelSpent, double currentSuccessChance, double lootModifier,
                      List<ItemStack> collectedLoot) {
        this.expeditionId = expeditionId;
        this.playerId = playerId;
        this.destination = destination;
        this.status = status;
        this.launchTimestamp = launchTimestamp;
        this.eventTriggerTimestamp = eventTriggerTimestamp;
        this.expectedReturnTimestamp = expectedReturnTimestamp;
        this.pendingEvent = pendingEvent;
        this.fuelSpent = fuelSpent;
        this.currentSuccessChance = currentSuccessChance;
        this.lootModifier = lootModifier;
        this.collectedLoot = new ArrayList<>(collectedLoot);
    }

    public String getExpeditionId() {
        return expeditionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ExpeditionDestination getDestination() {
        return destination;
    }

    public ExpeditionStatus getStatus() {
        return status;
    }

    public void setStatus(ExpeditionStatus status) {
        this.status = status;
    }

    public long getLaunchTimestamp() {
        return launchTimestamp;
    }

    public long getEventTriggerTimestamp() {
        return eventTriggerTimestamp;
    }

    public void setEventTriggerTimestamp(long eventTriggerTimestamp) {
        this.eventTriggerTimestamp = eventTriggerTimestamp;
    }

    public long getExpectedReturnTimestamp() {
        return expectedReturnTimestamp;
    }

    public void setExpectedReturnTimestamp(long expectedReturnTimestamp) {
        this.expectedReturnTimestamp = expectedReturnTimestamp;
    }

    @Nullable
    public ExpeditionEvent getPendingEvent() {
        return pendingEvent;
    }

    public void setPendingEvent(@Nullable ExpeditionEvent pendingEvent) {
        this.pendingEvent = pendingEvent;
    }

    public int getFuelSpent() {
        return fuelSpent;
    }

    public double getCurrentSuccessChance() {
        return currentSuccessChance;
    }

    public void setCurrentSuccessChance(double currentSuccessChance) {
        this.currentSuccessChance = Math.max(0.0, Math.min(1.0, currentSuccessChance));
    }

    public void modifySuccessChance(double modifier) {
        setCurrentSuccessChance(this.currentSuccessChance + modifier);
    }

    public double getLootModifier() {
        return lootModifier;
    }

    public void setLootModifier(double lootModifier) {
        this.lootModifier = lootModifier;
    }

    public void multiplyLootModifier(double multiplier) {
        this.lootModifier *= multiplier;
    }

    public List<ItemStack> getCollectedLoot() {
        return collectedLoot;
    }

    public void addLoot(ItemStack stack) {
        if (!stack.isEmpty()) {
            collectedLoot.add(stack.copy());
        }
    }

    public void addLoot(List<ItemStack> loot) {
        for (ItemStack stack : loot) {
            addLoot(stack);
        }
    }

    public void clearLoot() {
        collectedLoot.clear();
    }

    public boolean isAwaitingPlayerInput() {
        return status == ExpeditionStatus.AWAITING_DECISION && pendingEvent != null;
    }

    /**
     * Get time remaining in milliseconds based on current status.
     */
    public long getTimeRemainingMs() {
        long now = System.currentTimeMillis();
        return switch (status) {
            case IN_TRANSIT_OUTBOUND -> Math.max(0, eventTriggerTimestamp - now);
            case IN_TRANSIT_RETURN -> Math.max(0, expectedReturnTimestamp - now);
            default -> 0;
        };
    }

    /**
     * Get time remaining formatted as a string (e.g., "5:23")
     */
    public String getTimeRemainingFormatted() {
        long ms = getTimeRemainingMs();
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Get a summary line for display
     */
    public String getSummaryLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(destination.getDisplayName());
        sb.append(" - ");
        
        if (isAwaitingPlayerInput()) {
            sb.append("[!] DECISION NEEDED");
        } else if (status.isActive()) {
            sb.append(getTimeRemainingFormatted());
        } else if (status == ExpeditionStatus.COMPLETED_SUCCESS) {
            sb.append("SUCCESS - Ready to claim");
        } else if (status == ExpeditionStatus.COMPLETED_FAILURE) {
            sb.append("FAILED");
        } else {
            sb.append(status.getDisplayName());
        }
        
        return sb.toString();
    }

    public NbtCompound writeNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("ExpeditionId", expeditionId);
        nbt.putString("PlayerId", playerId.toString());
        nbt.putString("Destination", destination.name());
        nbt.putString("Status", status.name());
        nbt.putLong("LaunchTimestamp", launchTimestamp);
        nbt.putLong("EventTriggerTimestamp", eventTriggerTimestamp);
        nbt.putLong("ExpectedReturnTimestamp", expectedReturnTimestamp);
        nbt.putInt("FuelSpent", fuelSpent);
        nbt.putDouble("CurrentSuccessChance", currentSuccessChance);
        nbt.putDouble("LootModifier", lootModifier);
        
        if (pendingEvent != null) {
            nbt.put("PendingEvent", pendingEvent.writeNbt());
        }
        
        NbtList lootList = new NbtList();
        for (ItemStack stack : collectedLoot) {
            lootList.add(stack.toNbt(registries));
        }
        nbt.put("CollectedLoot", lootList);
        
        return nbt;
    }

    public static Expedition fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        String expeditionId = nbt.getString("ExpeditionId").orElse(UUID.randomUUID().toString());
        UUID playerId;
        try {
            playerId = UUID.fromString(nbt.getString("PlayerId").orElse(""));
        } catch (IllegalArgumentException e) {
            playerId = UUID.randomUUID();
        }
        
        ExpeditionDestination destination;
        try {
            destination = ExpeditionDestination.valueOf(nbt.getString("Destination").orElse("DUST_BELT_ALPHA"));
        } catch (IllegalArgumentException e) {
            destination = ExpeditionDestination.DUST_BELT_ALPHA;
        }
        
        ExpeditionStatus status;
        try {
            status = ExpeditionStatus.valueOf(nbt.getString("Status").orElse("AWAITING_LAUNCH"));
        } catch (IllegalArgumentException e) {
            status = ExpeditionStatus.AWAITING_LAUNCH;
        }
        
        long launchTimestamp = nbt.getLong("LaunchTimestamp").orElse(System.currentTimeMillis());
        long eventTriggerTimestamp = nbt.getLong("EventTriggerTimestamp").orElse(0L);
        long expectedReturnTimestamp = nbt.getLong("ExpectedReturnTimestamp").orElse(0L);
        int fuelSpent = nbt.getInt("FuelSpent").orElse(0);
        double currentSuccessChance = nbt.getDouble("CurrentSuccessChance").orElse(0.5);
        double lootModifier = nbt.getDouble("LootModifier").orElse(1.0);
        
        ExpeditionEvent pendingEvent = null;
        if (nbt.contains("PendingEvent")) {
            pendingEvent = ExpeditionEvent.fromNbt(nbt.getCompoundOrEmpty("PendingEvent"));
        }
        
        List<ItemStack> collectedLoot = new ArrayList<>();
        NbtList lootList = nbt.getListOrEmpty("CollectedLoot");
        for (int i = 0; i < lootList.size(); i++) {
            lootList.getCompound(i).ifPresent(compound -> {
                ItemStack.fromNbt(registries, compound).ifPresent(collectedLoot::add);
            });
        }
        
        return new Expedition(
            expeditionId, playerId, destination, status,
            launchTimestamp, eventTriggerTimestamp, expectedReturnTimestamp,
            pendingEvent, fuelSpent, currentSuccessChance, lootModifier, collectedLoot
        );
    }

    /**
     * Generate a unique expedition ID
     */
    public static String generateExpeditionId() {
        return "EXP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

