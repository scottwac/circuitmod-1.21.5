package starduster.circuitmod.expedition;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Handles loot generation for completed expeditions.
 */
public class ExpeditionLootTable {
    private static final List<LootEntry> LOOT_ENTRIES = new ArrayList<>();
    private static boolean initialized = false;

    public record LootEntry(Item item, double baseWeight, double minRewardMultiplier, int minQuantity, int maxQuantity) {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        // COMMON ITEMS (drop from any destination) - weight 100 = very common
        addEntry(Items.IRON_INGOT, 100.0, 1.0, 4, 16);
        addEntry(Items.COAL, 90.0, 1.0, 8, 32);
        addEntry(Items.COPPER_INGOT, 85.0, 1.0, 4, 12);
        addEntry(Items.REDSTONE, 70.0, 1.0, 4, 16);
        addEntry(Items.RAW_IRON, 60.0, 1.0, 3, 10);
        addEntry(Items.RAW_COPPER, 55.0, 1.0, 4, 14);

        // UNCOMMON ITEMS (need at least 1.3-1.5x multiplier destinations)
        addEntry(Items.GOLD_INGOT, 40.0, 1.5, 2, 8);
        addEntry(Items.LAPIS_LAZULI, 45.0, 1.3, 4, 12);
        addEntry(Items.QUARTZ, 50.0, 1.6, 4, 16);
        addEntry(Items.RAW_GOLD, 35.0, 1.5, 2, 6);
        addEntry(Items.IRON_BLOCK, 20.0, 1.5, 1, 3);

        // RARE ITEMS (need 2.0x+ multiplier - mid-tier dangerous destinations)
        addEntry(Items.DIAMOND, 15.0, 2.0, 1, 4);
        addEntry(Items.EMERALD, 12.0, 2.0, 1, 3);
        addEntry(Items.AMETHYST_SHARD, 20.0, 2.4, 2, 8);
        addEntry(Items.GOLD_BLOCK, 10.0, 2.0, 1, 2);

        // VERY RARE ITEMS (need 3.0x+ multiplier - high danger destinations)
        addEntry(Items.ANCIENT_DEBRIS, 5.0, 3.0, 1, 2);
        addEntry(Items.NETHERITE_SCRAP, 3.0, 3.5, 1, 1);
        addEntry(Items.DIAMOND_BLOCK, 4.0, 3.2, 1, 1);
        addEntry(Items.EMERALD_BLOCK, 3.5, 3.0, 1, 1);

        // LEGENDARY ITEMS (need 4.0x+ multiplier - only THE_MAW)
        addEntry(Items.NETHERITE_INGOT, 1.0, 4.5, 1, 1);
        addEntry(Items.NETHER_STAR, 0.5, 5.0, 1, 1);
        addEntry(Items.TOTEM_OF_UNDYING, 0.3, 5.0, 1, 1);

        // SPECIAL/UNIQUE ITEMS
        addEntry(Items.ECHO_SHARD, 2.0, 3.8, 1, 2);
        addEntry(Items.DISC_FRAGMENT_5, 1.5, 4.0, 1, 1);
        addEntry(Items.HEART_OF_THE_SEA, 0.8, 4.5, 1, 1);
    }

    private static void addEntry(Item item, double weight, double minMultiplier, int minQty, int maxQty) {
        LOOT_ENTRIES.add(new LootEntry(item, weight, minMultiplier, minQty, maxQty));
    }

    /**
     * Generate loot for a completed expedition
     */
    public static List<ItemStack> generateLoot(Expedition expedition, Random random) {
        initialize();
        
        List<ItemStack> loot = new ArrayList<>();
        double rewardMultiplier = expedition.getDestination().getRewardMultiplier() * expedition.getLootModifier();
        
        // Determine number of rolls based on reward multiplier
        int baseRolls = 3;
        int bonusRolls = (int)(rewardMultiplier - 1.0);
        int totalRolls = baseRolls + bonusRolls + random.nextInt(3); // 3-5 base + bonuses
        
        for (int i = 0; i < totalRolls; i++) {
            LootEntry entry = rollLootTable(rewardMultiplier, random);
            if (entry != null) {
                int quantity = random.nextInt(entry.minQuantity, entry.maxQuantity + 1);
                quantity = (int)(quantity * rewardMultiplier);
                quantity = Math.max(1, quantity);
                
                ItemStack stack = new ItemStack(entry.item, quantity);
                loot.add(stack);
            }
        }
        
        // Consolidate duplicate items
        return consolidateLoot(loot);
    }

    /**
     * Roll bonus loot for risky decisions
     */
    public static List<ItemStack> rollBonusLoot(Expedition expedition, Random random) {
        initialize();
        
        List<ItemStack> bonus = new ArrayList<>();
        double multiplier = expedition.getDestination().getRewardMultiplier() * 1.5; // Boosted for bonus
        
        LootEntry rareEntry = rollLootTable(multiplier, random);
        if (rareEntry != null) {
            ItemStack stack = new ItemStack(rareEntry.item, random.nextInt(1, 4));
            bonus.add(stack);
        }
        
        return bonus;
    }

    private static LootEntry rollLootTable(double rewardMultiplier, Random random) {
        // Filter entries by minimum reward multiplier
        List<LootEntry> eligibleEntries = LOOT_ENTRIES.stream()
            .filter(e -> rewardMultiplier >= e.minRewardMultiplier)
            .toList();
        
        if (eligibleEntries.isEmpty()) {
            return null;
        }
        
        // Weighted random selection
        double totalWeight = eligibleEntries.stream()
            .mapToDouble(LootEntry::baseWeight)
            .sum();
        
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        
        for (LootEntry entry : eligibleEntries) {
            cumulative += entry.baseWeight;
            if (roll <= cumulative) {
                return entry;
            }
        }
        
        return eligibleEntries.get(eligibleEntries.size() - 1);
    }

    private static List<ItemStack> consolidateLoot(List<ItemStack> loot) {
        Map<Item, Integer> consolidated = new HashMap<>();
        
        for (ItemStack stack : loot) {
            consolidated.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        
        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : consolidated.entrySet()) {
            int remaining = entry.getValue();
            int maxStackSize = entry.getKey().getMaxCount();
            
            while (remaining > 0) {
                int stackSize = Math.min(remaining, maxStackSize);
                result.add(new ItemStack(entry.getKey(), stackSize));
                remaining -= stackSize;
            }
        }
        
        return result;
    }
}

