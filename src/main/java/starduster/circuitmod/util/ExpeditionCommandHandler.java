package starduster.circuitmod.util;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ExpeditionControlBlockEntity;
import starduster.circuitmod.expedition.*;

import java.util.*;

/**
 * Handles expedition control block commands and their execution.
 */
public class ExpeditionCommandHandler {
    private static final Map<String, CommandDefinition> COMMANDS = new HashMap<>();
    
    static {
        // Register terminal commands
        registerCommand("help", "Display available commands", ExpeditionCommandHandler::helpCommand);
        registerCommand("status", "Show expedition status overview", ExpeditionCommandHandler::statusCommand);
        registerCommand("fuel", "Display current fuel level", ExpeditionCommandHandler::fuelCommand);
        registerCommand("refuel", "Add fuel (hold coal/charcoal/blaze rod)", ExpeditionCommandHandler::refuelCommand);
        registerCommand("destinations", "List available expedition destinations", ExpeditionCommandHandler::destinationsCommand);
        registerCommand("dest", "Alias for destinations", ExpeditionCommandHandler::destinationsCommand);
        registerCommand("launch", "Launch expedition to destination", ExpeditionCommandHandler::launchCommand);
        registerCommand("active", "Show active expeditions", ExpeditionCommandHandler::activeCommand);
        registerCommand("pending", "Show expeditions awaiting decisions", ExpeditionCommandHandler::pendingCommand);
        registerCommand("decide", "Make decision for pending event", ExpeditionCommandHandler::decideCommand);
        registerCommand("completed", "Show completed expeditions", ExpeditionCommandHandler::completedCommand);
        registerCommand("claim", "Claim rewards from completed expedition", ExpeditionCommandHandler::claimCommand);
        registerCommand("info", "Show detailed info about an expedition", ExpeditionCommandHandler::infoCommand);
        registerCommand("clear", "Clear the console", ExpeditionCommandHandler::clearCommand);
        registerCommand("monitor", "Toggle monitor mode (progress bars)", ExpeditionCommandHandler::monitorCommand);
        registerCommand("terminal", "Exit monitor mode, return to terminal", ExpeditionCommandHandler::terminalCommand);
    }
    
    private static void registerCommand(String name, String description, CommandExecutor executor) {
        COMMANDS.put(name.toLowerCase(), new CommandDefinition(name, description, executor));
    }
    
    public static CommandResult executeCommand(String input, ServerWorld world, BlockPos pos, 
                                                long creationTime, ExpeditionControlBlockEntity terminal,
                                                UUID playerId) {
        if (input == null || input.trim().isEmpty()) {
            return new CommandResult("");
        }
        
        String trimmedInput = input.trim();
        String[] parts = trimmedInput.split("\\s+");
        String commandName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        
        CommandDefinition command = COMMANDS.get(commandName);
        if (command == null) {
            return new CommandResult("Error: Unknown command '" + commandName + "'. Type 'help' for available commands.");
        }
        
        try {
            CommandContext context = new CommandContext(trimmedInput, commandName, args, world, pos, creationTime, terminal, playerId);
            return command.executor.execute(context);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[EXPEDITION-CONTROL] Error executing command: " + commandName, e);
            return new CommandResult("Error: Command execution failed - " + e.getMessage());
        }
    }
    
    // Command implementations
    
    private static CommandResult helpCommand(CommandContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EXPEDITION CONTROL TERMINAL ===\n");
        sb.append("Available commands:\n\n");
        
        sb.append("NAVIGATION:\n");
        sb.append("  destinations   - List expedition destinations\n");
        sb.append("  launch <dest>  - Launch expedition to destination\n\n");
        
        sb.append("MANAGEMENT:\n");
        sb.append("  status         - Overview of all expeditions\n");
        sb.append("  active         - Show active expeditions\n");
        sb.append("  pending        - Show events awaiting decisions\n");
        sb.append("  completed      - Show completed expeditions\n");
        sb.append("  info <id>      - Detailed expedition info\n\n");
        
        sb.append("ACTIONS:\n");
        sb.append("  decide <id> <a|b> - Respond to pending event\n");
        sb.append("  claim <id>     - Claim expedition rewards\n\n");
        
        sb.append("RESOURCES:\n");
        sb.append("  fuel           - Check fuel levels\n");
        sb.append("  refuel         - Pull fuel from adjacent chest\n\n");
        
        sb.append("OTHER:\n");
        sb.append("  monitor        - Show expedition progress bars\n");
        sb.append("  terminal       - Return to command terminal\n");
        sb.append("  clear          - Clear console");
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult statusCommand(CommandContext ctx) {
        ExpeditionRegistry registry = ExpeditionRegistry.get(ctx.world());
        UUID playerId = ctx.playerId();
        
        List<Expedition> active = registry.getActiveExpeditions(playerId);
        List<Expedition> completed = registry.getCompletedExpeditions(playerId);
        int slots = registry.getAvailableSlots(playerId);
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== EXPEDITION STATUS ===\n");
        sb.append("Fuel: ").append(ctx.terminal().getStoredFuel()).append("/").append(ctx.terminal().getMaxFuel()).append("\n");
        sb.append("Slots: ").append(slots).append("/").append(ExpeditionRegistry.MAX_CONCURRENT_EXPEDITIONS).append(" available\n\n");
        
        if (active.isEmpty() && completed.isEmpty()) {
            sb.append("No expeditions. Use 'launch <destination>' to start one.");
        } else {
            if (!active.isEmpty()) {
                sb.append("Active Expeditions:\n");
                for (Expedition exp : active) {
                    String marker = exp.isAwaitingPlayerInput() ? "[!] " : "[ ] ";
                    sb.append("  ").append(marker).append(exp.getSummaryLine()).append("\n");
                    sb.append("      ID: ").append(exp.getExpeditionId()).append("\n");
                }
            }
            
            if (!completed.isEmpty()) {
                sb.append("\nCompleted Expeditions:\n");
                for (Expedition exp : completed) {
                    String status = exp.getStatus() == ExpeditionStatus.COMPLETED_SUCCESS ? "[OK]" : "[X]";
                    sb.append("  ").append(status).append(" ").append(exp.getDestination().getDisplayName());
                    sb.append(" - ID: ").append(exp.getExpeditionId()).append("\n");
                }
            }
        }
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult fuelCommand(CommandContext ctx) {
        int fuel = ctx.terminal().getStoredFuel();
        int max = ctx.terminal().getMaxFuel();
        int percent = (int)((fuel * 100.0) / max);
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== FUEL STATUS ===\n");
        sb.append("Current: ").append(fuel).append("/").append(max).append(" (").append(percent).append("%)\n\n");
        sb.append("Fuel values:\n");
        sb.append("  Coal:        50 fuel\n");
        sb.append("  Charcoal:    50 fuel\n");
        sb.append("  Blaze Rod: 1000 fuel\n\n");
        sb.append("Place a chest adjacent to this terminal with fuel items.\n");
        sb.append("Use 'refuel' to pull fuel from the chest.");
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult refuelCommand(CommandContext ctx) {
        // Check if there's an adjacent chest
        if (ctx.terminal().findAdjacentChest() == null) {
            return new CommandResult("Error: No chest found adjacent to the terminal.\nPlace a chest next to this block with coal, charcoal, or blaze rods.");
        }
        
        // Check if tank is already full
        if (ctx.terminal().getStoredFuel() >= ctx.terminal().getMaxFuel()) {
            return new CommandResult("Fuel tank is already full!");
        }
        
        // Try to refuel from the adjacent chest
        int fuelAdded = ctx.terminal().refuelFromAdjacentChest(0); // 0 = fill to max
        
        if (fuelAdded < 0) {
            return new CommandResult("Error: No chest found adjacent to the terminal.");
        } else if (fuelAdded == 0) {
            return new CommandResult("No valid fuel items found in adjacent chest.\nValid fuels: Coal, Charcoal, Blaze Rods");
        } else {
            return new CommandResult("Refueled! Added " + fuelAdded + " fuel from adjacent chest.\n" +
                "Fuel: " + ctx.terminal().getStoredFuel() + "/" + ctx.terminal().getMaxFuel());
        }
    }
    
    private static CommandResult destinationsCommand(CommandContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AVAILABLE DESTINATIONS ===\n\n");
        
        for (ExpeditionDestination dest : ExpeditionDestination.values()) {
            sb.append(String.format("%-22s [%3d fuel] %s\n", 
                dest.getDisplayName(), dest.getBaseFuelCost(), dest.getRiskIndicator()));
            sb.append("  Time: ~").append(dest.getBaseTimeMinutes()).append(" min | ");
            sb.append("Reward: x").append(String.format("%.1f", dest.getRewardMultiplier())).append("\n");
            sb.append("  ").append(dest.getDescription()).append("\n\n");
        }
        
        sb.append("Use 'launch <destination>' to start an expedition.");
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult launchCommand(CommandContext ctx) {
        if (ctx.args().length < 1) {
            return new CommandResult("Usage: launch <destination>\nUse 'destinations' to see available options.");
        }
        
        // Parse destination name (join args in case of spaces)
        String destName = String.join(" ", ctx.args());
        ExpeditionDestination destination = ExpeditionDestination.fromName(destName);
        
        if (destination == null) {
            return new CommandResult("Unknown destination: " + destName + "\nUse 'destinations' to see available options.");
        }
        
        // Check fuel
        int fuelRequired = destination.getBaseFuelCost();
        int fuelAvailable = ctx.terminal().getStoredFuel();
        
        if (fuelAvailable < fuelRequired) {
            return new CommandResult("Insufficient fuel!\nRequired: " + fuelRequired + "\nAvailable: " + fuelAvailable);
        }
        
        // Launch expedition
        ExpeditionManager.Result<Expedition> result = ExpeditionManager.launchExpedition(
            ctx.world(), ctx.playerId(), destination, fuelAvailable
        );
        
        if (!result.success()) {
            return new CommandResult("Launch failed: " + result.errorMessage());
        }
        
        // Consume fuel
        ctx.terminal().consumeFuel(fuelRequired);
        
        Expedition expedition = result.value();
        StringBuilder sb = new StringBuilder();
        sb.append("=== EXPEDITION LAUNCHED ===\n");
        sb.append("ID: ").append(expedition.getExpeditionId()).append("\n");
        sb.append("Destination: ").append(destination.getDisplayName()).append("\n");
        sb.append("Fuel consumed: ").append(fuelRequired).append("\n");
        sb.append("ETA: ~").append(destination.getBaseTimeMinutes()).append(" minutes\n\n");
        sb.append("An event will occur during the journey.\n");
        sb.append("Monitor with 'status' or 'active' commands.");
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult activeCommand(CommandContext ctx) {
        ExpeditionRegistry registry = ExpeditionRegistry.get(ctx.world());
        List<Expedition> active = registry.getActiveExpeditions(ctx.playerId());
        
        if (active.isEmpty()) {
            return new CommandResult("No active expeditions.\nUse 'launch <destination>' to start one.");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== ACTIVE EXPEDITIONS ===\n\n");
        
        for (Expedition exp : active) {
            sb.append("ID: ").append(exp.getExpeditionId()).append("\n");
            sb.append("Destination: ").append(exp.getDestination().getDisplayName()).append("\n");
            sb.append("Status: ").append(exp.getStatus().getDisplayName()).append("\n");
            
            if (exp.isAwaitingPlayerInput()) {
                sb.append("[!] DECISION REQUIRED - use 'info ").append(exp.getExpeditionId()).append("'\n");
            } else {
                sb.append("Time remaining: ").append(exp.getTimeRemainingFormatted()).append("\n");
            }
            sb.append("\n");
        }
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult pendingCommand(CommandContext ctx) {
        ExpeditionRegistry registry = ExpeditionRegistry.get(ctx.world());
        List<Expedition> pending = registry.getActiveExpeditions(ctx.playerId()).stream()
            .filter(Expedition::isAwaitingPlayerInput)
            .toList();
        
        if (pending.isEmpty()) {
            return new CommandResult("No expeditions awaiting decisions.");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== PENDING DECISIONS ===\n\n");
        
        for (Expedition exp : pending) {
            ExpeditionEvent event = exp.getPendingEvent();
            sb.append("ID: ").append(exp.getExpeditionId()).append("\n");
            sb.append("Destination: ").append(exp.getDestination().getDisplayName()).append("\n");
            sb.append("Event: ").append(event.getTitle()).append("\n\n");
            sb.append(event.getDescription()).append("\n\n");
            sb.append("Options:\n");
            sb.append("  [A] ").append(event.getChoiceA().getButtonText()).append("\n");
            sb.append("  [B] ").append(event.getChoiceB().getButtonText()).append("\n\n");
            sb.append("Use: decide ").append(exp.getExpeditionId()).append(" <a|b>\n");
            sb.append("---\n");
        }
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult decideCommand(CommandContext ctx) {
        if (ctx.args().length < 2) {
            return new CommandResult("Usage: decide <expedition-id> <a|b>");
        }
        
        String expeditionId = ctx.args()[0].toUpperCase();
        String choice = ctx.args()[1].toLowerCase();
        
        if (!choice.equals("a") && !choice.equals("b")) {
            return new CommandResult("Invalid choice. Use 'a' or 'b'.");
        }
        
        ExpeditionRegistry registry = ExpeditionRegistry.get(ctx.world());
        Optional<Expedition> optExp = registry.getExpedition(expeditionId);
        
        if (optExp.isEmpty()) {
            return new CommandResult("Expedition not found: " + expeditionId);
        }
        
        Expedition expedition = optExp.get();
        
        if (!expedition.getPlayerId().equals(ctx.playerId())) {
            return new CommandResult("This is not your expedition.");
        }
        
        if (!expedition.isAwaitingPlayerInput()) {
            return new CommandResult("No decision pending for this expedition.");
        }
        
        boolean choseB = choice.equals("b");
        ExpeditionManager.Result<String> result = ExpeditionManager.processPlayerDecision(ctx.world(), expedition, choseB);
        
        if (!result.success()) {
            return new CommandResult("Error: " + result.errorMessage());
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== DECISION MADE ===\n\n");
        sb.append(result.value()).append("\n\n");
        
        if (expedition.getStatus() == ExpeditionStatus.COMPLETED_FAILURE) {
            sb.append("The expedition was lost.");
        } else {
            sb.append("Expedition continuing to return...\n");
            sb.append("ETA: ").append(expedition.getTimeRemainingFormatted());
        }
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult completedCommand(CommandContext ctx) {
        ExpeditionRegistry registry = ExpeditionRegistry.get(ctx.world());
        List<Expedition> completed = registry.getCompletedExpeditions(ctx.playerId());
        
        if (completed.isEmpty()) {
            return new CommandResult("No completed expeditions to claim.");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== COMPLETED EXPEDITIONS ===\n\n");
        
        for (Expedition exp : completed) {
            String status = exp.getStatus() == ExpeditionStatus.COMPLETED_SUCCESS ? "SUCCESS" : "FAILED";
            sb.append("ID: ").append(exp.getExpeditionId()).append(" [").append(status).append("]\n");
            sb.append("Destination: ").append(exp.getDestination().getDisplayName()).append("\n");
            
            if (exp.getStatus() == ExpeditionStatus.COMPLETED_SUCCESS) {
                sb.append("Loot: ").append(exp.getCollectedLoot().size()).append(" items\n");
                sb.append("Use: claim ").append(exp.getExpeditionId()).append("\n");
            } else {
                sb.append("No cargo recovered.\n");
                sb.append("Use: claim ").append(exp.getExpeditionId()).append(" (to dismiss)\n");
            }
            sb.append("\n");
        }
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult claimCommand(CommandContext ctx) {
        if (ctx.args().length < 1) {
            return new CommandResult("Usage: claim <expedition-id>");
        }
        
        String expeditionId = ctx.args()[0].toUpperCase();
        
        ExpeditionManager.Result<List<ItemStack>> result = ExpeditionManager.claimExpeditionLoot(
            ctx.world(), expeditionId, ctx.playerId()
        );
        
        if (!result.success()) {
            return new CommandResult("Error: " + result.errorMessage());
        }
        
        List<ItemStack> loot = result.value();
        
        // Try to deposit items into adjacent chest first
        Inventory chest = ctx.terminal().findAdjacentChest();
        List<ItemStack> overflowItems = new ArrayList<>();
        
        if (chest != null) {
            for (ItemStack stack : loot) {
                ItemStack remaining = insertIntoInventory(chest, stack.copy());
                if (!remaining.isEmpty()) {
                    overflowItems.add(remaining);
                }
            }
            chest.markDirty();
        } else {
            // No chest, give directly to player
            overflowItems.addAll(loot);
        }
        
        // Give overflow items to player (or drop if inventory full)
        if (!overflowItems.isEmpty()) {
            ServerPlayerEntity player = ctx.world().getServer().getPlayerManager().getPlayer(ctx.playerId());
            if (player != null) {
                for (ItemStack stack : overflowItems) {
                    if (!player.getInventory().insertStack(stack.copy())) {
                        player.dropItem(stack.copy(), false);
                    }
                }
            }
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== EXPEDITION CLAIMED ===\n\n");
        
        if (loot.isEmpty()) {
            sb.append("No items recovered from this expedition.");
        } else {
            if (chest != null) {
                sb.append("Rewards deposited to adjacent chest:\n");
            } else {
                sb.append("Rewards received (no chest found):\n");
            }
            Map<String, Integer> itemCounts = new HashMap<>();
            for (ItemStack stack : loot) {
                String name = stack.getName().getString();
                itemCounts.merge(name, stack.getCount(), Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
                sb.append("  ").append(entry.getValue()).append("x ").append(entry.getKey()).append("\n");
            }
            if (!overflowItems.isEmpty() && chest != null) {
                sb.append("\nSome items didn't fit in chest - given to player.");
            }
        }
        
        return new CommandResult(sb.toString().trim());
    }
    
    /**
     * Insert an item stack into an inventory, returning any that didn't fit
     */
    private static ItemStack insertIntoInventory(Inventory inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        
        ItemStack toInsert = stack.copy();
        
        // First, try to merge with existing stacks
        for (int i = 0; i < inventory.size() && !toInsert.isEmpty(); i++) {
            ItemStack slotStack = inventory.getStack(i);
            if (!slotStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(slotStack, toInsert)) {
                int space = slotStack.getMaxCount() - slotStack.getCount();
                int transfer = Math.min(space, toInsert.getCount());
                if (transfer > 0) {
                    slotStack.increment(transfer);
                    toInsert.decrement(transfer);
                }
            }
        }
        
        // Then, try to find empty slots
        for (int i = 0; i < inventory.size() && !toInsert.isEmpty(); i++) {
            ItemStack slotStack = inventory.getStack(i);
            if (slotStack.isEmpty()) {
                inventory.setStack(i, toInsert.copy());
                toInsert = ItemStack.EMPTY;
            }
        }
        
        return toInsert;
    }
    
    private static CommandResult infoCommand(CommandContext ctx) {
        if (ctx.args().length < 1) {
            return new CommandResult("Usage: info <expedition-id>");
        }
        
        String expeditionId = ctx.args()[0].toUpperCase();
        
        ExpeditionRegistry registry = ExpeditionRegistry.get(ctx.world());
        Optional<Expedition> optExp = registry.getExpedition(expeditionId);
        
        if (optExp.isEmpty()) {
            return new CommandResult("Expedition not found: " + expeditionId);
        }
        
        Expedition exp = optExp.get();
        
        if (!exp.getPlayerId().equals(ctx.playerId())) {
            return new CommandResult("This is not your expedition.");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== EXPEDITION DETAILS ===\n\n");
        sb.append("ID: ").append(exp.getExpeditionId()).append("\n");
        sb.append("Destination: ").append(exp.getDestination().getDisplayName()).append("\n");
        sb.append("Status: ").append(exp.getStatus().getDisplayName()).append("\n");
        sb.append("Fuel spent: ").append(exp.getFuelSpent()).append("\n");
        sb.append("Success chance: ").append(String.format("%.0f%%", exp.getCurrentSuccessChance() * 100)).append("\n");
        sb.append("Loot modifier: x").append(String.format("%.2f", exp.getLootModifier())).append("\n");
        
        if (exp.getStatus().isActive()) {
            sb.append("Time remaining: ").append(exp.getTimeRemainingFormatted()).append("\n");
        }
        
        if (exp.isAwaitingPlayerInput() && exp.getPendingEvent() != null) {
            ExpeditionEvent event = exp.getPendingEvent();
            sb.append("\n=== PENDING EVENT ===\n");
            sb.append(event.getTitle()).append("\n\n");
            sb.append(event.getDescription()).append("\n\n");
            sb.append("Options:\n");
            sb.append("  [A] ").append(event.getChoiceA().getButtonText()).append("\n");
            sb.append("  [B] ").append(event.getChoiceB().getButtonText()).append("\n\n");
            sb.append("Use: decide ").append(exp.getExpeditionId()).append(" <a|b>");
        }
        
        if (!exp.getCollectedLoot().isEmpty()) {
            sb.append("\n\nCollected loot: ").append(exp.getCollectedLoot().size()).append(" items");
        }
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult clearCommand(CommandContext ctx) {
        return new CommandResult("", true);
    }
    
    private static CommandResult monitorCommand(CommandContext ctx) {
        ctx.terminal().setMonitorMode(true);
        
        // Include status info so monitor view has expedition data to display
        ExpeditionRegistry registry = ExpeditionRegistry.get(ctx.world());
        UUID playerId = ctx.playerId();
        List<Expedition> active = registry.getActiveExpeditions(playerId);
        List<Expedition> completed = registry.getCompletedExpeditions(playerId);
        
        StringBuilder sb = new StringBuilder();
        sb.append("Monitor mode enabled.\n\n");
        
        if (!active.isEmpty()) {
            sb.append("Active Expeditions:\n");
            for (Expedition exp : active) {
                String marker = exp.isAwaitingPlayerInput() ? "[!] " : "[ ] ";
                sb.append("  ").append(marker).append(exp.getSummaryLine()).append("\n");
                sb.append("      ID: ").append(exp.getExpeditionId()).append("\n");
            }
        }
        
        if (!completed.isEmpty()) {
            sb.append("\nCompleted Expeditions:\n");
            for (Expedition exp : completed) {
                String status = exp.getStatus() == ExpeditionStatus.COMPLETED_SUCCESS ? "[OK]" : "[X]";
                sb.append("  ").append(status).append(" ").append(exp.getDestination().getDisplayName());
                sb.append(" - ID: ").append(exp.getExpeditionId()).append("\n");
            }
        }
        
        if (active.isEmpty() && completed.isEmpty()) {
            sb.append("No active expeditions.");
        }
        
        return new CommandResult(sb.toString().trim(), false, true);
    }
    
    private static CommandResult terminalCommand(CommandContext ctx) {
        ctx.terminal().setMonitorMode(false);
        return new CommandResult("Terminal mode restored.");
    }
    
    // Helper classes
    
    @FunctionalInterface
    private interface CommandExecutor {
        CommandResult execute(CommandContext context);
    }
    
    private record CommandDefinition(String name, String description, CommandExecutor executor) {}
    
    public record CommandContext(
        String rawInput,
        String commandName,
        String[] args,
        ServerWorld world,
        BlockPos blockPos,
        long creationTime,
        ExpeditionControlBlockEntity terminal,
        UUID playerId
    ) {}
    
    public record CommandResult(String output, boolean clearScreen, boolean switchToMonitor) {
        public CommandResult(String output) {
            this(output, false, false);
        }
        
        public CommandResult(String output, boolean clearScreen) {
            this(output, clearScreen, false);
        }
    }
}

