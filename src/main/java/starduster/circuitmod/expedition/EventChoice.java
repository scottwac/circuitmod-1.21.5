package starduster.circuitmod.expedition;

import net.minecraft.nbt.NbtCompound;

/**
 * Represents a choice the player can make during an expedition event.
 */
public class EventChoice {
    private final String buttonText;
    private final String outcomeDescription;
    private final double successChanceModifier;
    private final double lootModifier;
    private final double instantFailChance;
    private final double bonusLootChance;

    public EventChoice(String buttonText, String outcomeDescription, 
                       double successChanceModifier, double lootModifier,
                       double instantFailChance, double bonusLootChance) {
        this.buttonText = buttonText;
        this.outcomeDescription = outcomeDescription;
        this.successChanceModifier = successChanceModifier;
        this.lootModifier = lootModifier;
        this.instantFailChance = instantFailChance;
        this.bonusLootChance = bonusLootChance;
    }

    public String getButtonText() {
        return buttonText;
    }

    public String getOutcomeDescription() {
        return outcomeDescription;
    }

    public double getSuccessChanceModifier() {
        return successChanceModifier;
    }

    public double getLootModifier() {
        return lootModifier;
    }

    public double getInstantFailChance() {
        return instantFailChance;
    }

    public double getBonusLootChance() {
        return bonusLootChance;
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("ButtonText", buttonText);
        nbt.putString("OutcomeDescription", outcomeDescription);
        nbt.putDouble("SuccessChanceModifier", successChanceModifier);
        nbt.putDouble("LootModifier", lootModifier);
        nbt.putDouble("InstantFailChance", instantFailChance);
        nbt.putDouble("BonusLootChance", bonusLootChance);
        return nbt;
    }

    public static EventChoice fromNbt(NbtCompound nbt) {
        return new EventChoice(
            nbt.getString("ButtonText").orElse(""),
            nbt.getString("OutcomeDescription").orElse(""),
            nbt.getDouble("SuccessChanceModifier").orElse(0.0),
            nbt.getDouble("LootModifier").orElse(1.0),
            nbt.getDouble("InstantFailChance").orElse(0.0),
            nbt.getDouble("BonusLootChance").orElse(0.0)
        );
    }
}

