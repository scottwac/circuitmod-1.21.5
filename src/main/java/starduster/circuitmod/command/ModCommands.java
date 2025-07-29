package starduster.circuitmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.item.network.ItemNetworkManager;
import starduster.circuitmod.item.network.ItemNetwork;
import starduster.circuitmod.power.EnergyNetwork;
import net.minecraft.server.world.ServerWorld;

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
            .then(CommandManager.literal("recover-energy-networks")
                .executes(ModCommands::recoverEnergyNetworks))
        );
    }

    private static int refreshNetworks(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Circuitmod.LOGGER.info("[COMMAND] Force refreshing all item networks");
        
        // Force refresh all networks
        for (ItemNetwork network : ItemNetworkManager.getAllNetworks()) {
            Circuitmod.LOGGER.info("[COMMAND] Refreshing network {}", network.getNetworkId());
            // Force a network rescan to refresh all connections
            // network.forceRescanAllInventories(); // This method no longer exists in simplified network
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

    /**
     * Command to recover energy networks after a crash.
     */
    private static int recoverEnergyNetworks(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerWorld world = source.getWorld();
        
        source.sendMessage(Text.literal("Starting energy network recovery...").formatted(Formatting.YELLOW));
        
        // Perform global recovery
        starduster.circuitmod.power.EnergyNetwork.performGlobalRecovery(world);
        
        source.sendMessage(Text.literal("Energy network recovery complete!").formatted(Formatting.GREEN));
        
        return 1;
    }
} 