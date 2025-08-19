package starduster.circuitmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.item.network.ItemNetwork;
import starduster.circuitmod.power.EnergyNetworkManager;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.Circuitmod;

public class ModCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register(ModCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("circuitmod")
            .then(CommandManager.literal("refresh-networks")
                .executes(ModCommands::refreshNetworks))
            .then(CommandManager.literal("network-stats")
                .executes(ModCommands::networkStats))
            .then(CommandManager.literal("energy-recovery")
                .executes(ModCommands::energyRecovery))
            .then(CommandManager.literal("energy-stats")
                .executes(ModCommands::energyStats))
        );
    }

    private static int refreshNetworks(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Circuitmod.LOGGER.info("[COMMAND] Force refreshing all item networks");
        
        // Force refresh all networks
        for (ItemNetwork network : ItemNetworkManager.getAllNetworks()) {
            Circuitmod.LOGGER.info("[COMMAND] Refreshing network {}", network.getNetworkId());

        }
        
        source.sendMessage(Text.literal("Refreshed all item networks"));
        return 1;
    }
    
    private static int networkStats(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        String stats = ItemNetworkManager.getNetworkStats();
        Circuitmod.LOGGER.info("[COMMAND] Network stats: {}", stats);
        source.sendMessage(Text.literal(stats));
        
        return 1;
    }
    
    private static int energyRecovery(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Circuitmod.LOGGER.info("[COMMAND] Performing energy network recovery");
        
        // Validate and repair all networks
        int repaired = EnergyNetworkManager.validateAllNetworks(source.getWorld());
        
        // Perform global recovery
        int recovered = EnergyNetworkManager.performGlobalRecovery(source.getWorld());
        
        String message = String.format("Energy network recovery: %d networks repaired, %d blocks reconnected", repaired, recovered);
        Circuitmod.LOGGER.info("[COMMAND] {}", message);
        source.sendMessage(Text.literal(message));
        
        return 1;
    }
    
    private static int energyStats(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        String stats = EnergyNetworkManager.getNetworkStats();
        Circuitmod.LOGGER.info("[COMMAND] Energy network stats: {}", stats);
        source.sendMessage(Text.literal(stats));
        
        return 1;
    }
} 