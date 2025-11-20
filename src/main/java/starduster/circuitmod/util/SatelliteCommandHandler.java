package starduster.circuitmod.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.SatelliteControlBlockEntity;
import starduster.circuitmod.satellite.Satellite;
import starduster.circuitmod.satellite.SatelliteRegistry;

import java.util.*;

/**
 * Handles satellite control block commands and their execution.
 * This class contains all the available commands and their logic.
 */
public class SatelliteCommandHandler {
    private static final Map<String, CommandDefinition> COMMANDS = new HashMap<>();
    
    static {
        // Register general terminal commands (non-satellite-specific)
        registerCommand("help", "Display available commands (use 'help <type>' for satellite commands)", SatelliteCommandHandler::helpCommand);
        registerCommand("status", "Check terminal status", SatelliteCommandHandler::statusCommand);
        registerCommand("ping", "Ping the terminal", SatelliteCommandHandler::pingCommand);
        registerCommand("power", "Check power levels", SatelliteCommandHandler::powerCommand);
        registerCommand("reboot", "Reboot terminal systems", SatelliteCommandHandler::rebootCommand);
        registerCommand("clear", "Clear the console", SatelliteCommandHandler::clearCommand);
        registerCommand("coords", "Display current coordinates", SatelliteCommandHandler::coordsCommand);
        registerCommand("time", "Display world time", SatelliteCommandHandler::timeCommand);
        registerCommand("uptime", "Display terminal uptime", SatelliteCommandHandler::uptimeCommand);
        registerCommand("firework", "Launch a celebratory firework", SatelliteCommandHandler::fireworkCommand);
        registerCommand("weather", "Display weather information", SatelliteCommandHandler::weatherCommand);
        registerCommand("diagnostics", "Run system diagnostics", SatelliteCommandHandler::diagnosticsCommand);
        registerCommand("connect", "Connect to a satellite via access code", SatelliteCommandHandler::connectCommand);
        registerCommand("disconnect", "Terminate the current satellite session", SatelliteCommandHandler::disconnectCommand);
        registerCommand("satinfo", "Show information about the linked satellite", SatelliteCommandHandler::satInfoCommand);
        registerCommand("deploysat", "Launch a prototype satellite for testing", SatelliteCommandHandler::deploySatelliteCommand);
        registerCommand("satcmd", "Forward a command to the linked satellite", SatelliteCommandHandler::satcmdCommand);
    }
    
    /**
     * Register a command with its description and handler
     */
    private static void registerCommand(String name, String description, CommandExecutor executor) {
        COMMANDS.put(name.toLowerCase(), new CommandDefinition(name, description, executor));
    }
    
    /**
     * Execute a command and return the result
     */
    public static CommandResult executeCommand(String input, ServerWorld world, BlockPos pos, long creationTime, SatelliteControlBlockEntity terminal) {
        if (input == null || input.trim().isEmpty()) {
            return new CommandResult("");
        }
        
        String trimmedInput = input.trim();
        String[] parts = trimmedInput.split("\\s+");
        String commandName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        
        CommandDefinition command = COMMANDS.get(commandName);
        if (command == null) {
            // Check if user is connected to a satellite - if so, try forwarding the command
            if (terminal.isConnectedToSatellite()) {
                // Auto-forward to satcmd if connected to a satellite
                Circuitmod.LOGGER.info("[SATELLITE-CONTROL] Auto-forwarding '{}' to linked satellite", commandName);
                String[] satArgs = new String[parts.length];
                System.arraycopy(parts, 0, satArgs, 0, parts.length);
                CommandContext satContext = new CommandContext(trimmedInput, "satcmd", satArgs, world, pos, creationTime, terminal);
                return satcmdCommand(satContext);
            }
            return new CommandResult("Error: Unknown command '" + commandName + "'. Type 'help' for available commands.");
        }
        
        try {
            CommandContext context = new CommandContext(trimmedInput, commandName, args, world, pos, creationTime, terminal);
            return command.executor.execute(context);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[SATELLITE-CONTROL] Error executing command: " + commandName, e);
            return new CommandResult("Error: Command execution failed - " + e.getMessage());
        }
    }
    
    /**
     * Get a list of all available commands
     */
    public static List<String> getAvailableCommands() {
        return new ArrayList<>(COMMANDS.keySet());
    }
    
    // Command implementations
    
    private static CommandResult helpCommand(CommandContext ctx) {
        String[] args = ctx.args();
        
        // Check if user wants help for a specific satellite type
        if (args.length > 0) {
            String satType = args[0].toLowerCase();
            return getSatelliteHelp(satType);
        }
        
        // General help
        StringBuilder sb = new StringBuilder();
        sb.append("=== SATELLITE CONTROL TERMINAL ===\n");
        sb.append("Available commands:\n");
        
        List<String> commandNames = new ArrayList<>(COMMANDS.keySet());
        Collections.sort(commandNames);
        
        for (String name : commandNames) {
            CommandDefinition cmd = COMMANDS.get(name);
            sb.append(String.format("  %-12s - %s\n", cmd.name, cmd.description));
        }
        
        sb.append("\nSatellite Types:\n");
        sb.append("  strike       - Orbital strike satellite\n");
        sb.append("  scan         - Reconnaissance satellite\n");
        sb.append("  mining       - Resource extraction satellite\n");
        sb.append("\nType 'help <type>' for satellite-specific commands");
        
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult getSatelliteHelp(String satType) {
        return switch (satType) {
            case "strike" -> new CommandResult(
                "=== STRIKE SATELLITE COMMANDS ===\n" +
                "  strike <x> <y> <z>  - Launch orbital strike at coordinates\n" +
                "\nDescription: Offensive satellite capable of launching\n" +
                "TNT bombardments from orbit.\n" +
                "  Radius : 5 blocks\n" +
                "  Payload: 8 TNT\n" +
                "  Cooldown: 5 minutes"
            );
               case "scan" -> new CommandResult(
                   "=== SCAN SATELLITE COMMANDS ===\n" +
                   "  scan <x> <z> [size]           - Visual terrain map\n" +
                   "  locate structure <name>       - Locate nearest structure\n" +
                   "  locate biome <name>           - Locate nearest biome\n" +
                   "\nDescription: Reconnaissance satellite for terrain\n" +
                   "scanning and location services.\n" +
                   "  Scan: Renders colored terrain map (32-128 blocks)\n" +
                   "  Examples: scan 100 200 64\n" +
                   "            locate structure village\n" +
                   "            locate biome desert\n" +
                   "  Cooldown: 30 seconds"
               );
            case "mining" -> new CommandResult(
                "=== MINING SATELLITE COMMANDS ===\n" +
                "  drill <x> <z>              - Drill 3x3 column from surface to bedrock\n" +
                "  extract <x> <y> <z>        - Extract ores from 3x3 area\n" +
                "\nDescription: Resource extraction satellite with\n" +
                "Fortune III enchantment.\n" +
                "  Drill   : Mines entire 3x3 column from surface down to bedrock\n" +
                "  Extract : Mines only ore blocks in 3x3 area at specific height\n" +
                "  Cooldown: 2 minutes"
            );
            default -> new CommandResult("Unknown satellite type: " + satType + "\n" +
                "Available types: strike, scan, mining\n" +
                "Type 'help' for general commands");
        };
    }
    
    private static CommandResult statusCommand(CommandContext ctx) {
        List<Satellite> satellites = getSatellites(ctx.world());
        if (satellites.isEmpty()) {
            return new CommandResult("Status: No satellites linked to this controller.");
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("Connected Satellites:\n");
        for (Satellite satellite : satellites) {
            sb.append("  ")
                .append(satellite.formatLabel())
                .append(" | ")
                .append(satellite.getStatusLine())
                .append(" | Uptime ")
                .append(formatDuration(satellite.getUptimeMillis(now)))
                .append(" / ")
                .append(formatDuration(satellite.getLifetimeMillis()))
                .append("\n");
        }
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult pingCommand(CommandContext ctx) {
        long delay = 10 + (long)(Math.random() * 50);
        return new CommandResult("PONG! Response time: " + delay + "ms");
    }
    
    
    private static CommandResult powerCommand(CommandContext ctx) {
        List<Satellite> satellites = getSatellites(ctx.world());
        if (satellites.isEmpty()) {
            return new CommandResult("Power: No satellites linked to report.");
        }
        StringBuilder sb = new StringBuilder("Power Levels:\n");
        for (Satellite satellite : satellites) {
            double percent = satellite.getPowerLevelPercent();
            double seconds = Math.max(0, satellite.getRechargeTicks()) / 20.0;
            sb.append("  ")
                .append(satellite.formatLabel())
                .append(": ")
                .append(formatPercent(percent))
                .append(" charged | Cooldown remaining: ")
                .append(String.format(Locale.US, "%.1fs", seconds))
                .append("\n");
        }
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult rebootCommand(CommandContext ctx) {
        return new CommandResult(
            "Initiating system reboot...\n" +
            "Shutting down subsystems...\n" +
            "Clearing cache...\n" +
            "Restarting...\n" +
            "System reboot complete. All systems online."
        );
    }
    
    private static CommandResult clearCommand(CommandContext ctx) {
        return new CommandResult("", true);
    }
    
    private static CommandResult coordsCommand(CommandContext ctx) {
        BlockPos pos = ctx.blockPos();
        ServerWorld world = ctx.world();
        return new CommandResult(
            "Satellite Coordinates:\n" +
            "  X: " + pos.getX() + "\n" +
            "  Y: " + pos.getY() + "\n" +
            "  Z: " + pos.getZ() + "\n" +
            "  Dimension: " + world.getRegistryKey().getValue()
        );
    }
    
    private static CommandResult timeCommand(CommandContext ctx) {
        ServerWorld world = ctx.world();
        long timeOfDay = world.getTimeOfDay() % 24000;
        int hours = (int)((timeOfDay / 1000 + 6) % 24);
        int minutes = (int)((timeOfDay % 1000) * 60 / 1000);
        
        return new CommandResult(
            "World Time: " + String.format("%02d:%02d", hours, minutes) + "\n" +
            "Day: " + (world.getTimeOfDay() / 24000)
        );
    }
    
    private static CommandResult uptimeCommand(CommandContext ctx) {
        List<Satellite> satellites = getSatellites(ctx.world());
        if (satellites.isEmpty()) {
            return new CommandResult("Uptime: No satellites linked.");
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("Satellite Uptime / Lifetime:\n");
        for (Satellite satellite : satellites) {
            long uptime = satellite.getUptimeMillis(now);
            long lifetime = satellite.getLifetimeMillis();
            long remaining = Math.max(0, lifetime - uptime);
            sb.append("  ")
                .append(satellite.formatLabel())
                .append(": ")
                .append(formatDuration(uptime))
                .append(" / ")
                .append(formatDuration(lifetime))
                .append(" (")
                .append(formatDuration(remaining))
                .append(" remaining)")
                .append("\n");
        }
        return new CommandResult(sb.toString().trim());
    }
    
    private static CommandResult fireworkCommand(CommandContext ctx) {
        ServerWorld world = ctx.world();
        BlockPos pos = ctx.blockPos();
        // Spawn a firework entity at the block position
        net.minecraft.entity.projectile.FireworkRocketEntity firework = new net.minecraft.entity.projectile.FireworkRocketEntity(
            world,
            pos.getX() + 0.5,
            pos.getY() + 1.0,
            pos.getZ() + 0.5,
            createRandomFireworkStack()
        );
        
        world.spawnEntity(firework);
        
        return new CommandResult(
            "Firework launched!\n" +
            "Celebration sequence initiated.\n" +
            "Stand clear of launch area."
        );
    }
    
    private static CommandResult weatherCommand(CommandContext ctx) {
        ServerWorld world = ctx.world();
        BlockPos pos = ctx.blockPos();
        String biomeName = world.getBiome(pos).getKey()
            .map(key -> key.getValue().toString())
            .orElse("unknown");
        boolean raining = world.isRaining();
        boolean thundering = world.isThundering();
        return new CommandResult(String.format(
            "Weather Report:\n  Biome: %s\n  Raining: %s\n  Thundering: %s",
            biomeName,
            raining ? "YES" : "NO",
            thundering ? "YES" : "NO"
        ));
    }
    
    private static CommandResult diagnosticsCommand(CommandContext ctx) {
        List<Satellite> satellites = getSatellites(ctx.world());
        if (satellites.isEmpty()) {
            return new CommandResult("Diagnostics: No satellites linked.");
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("Satellite Health Report:\n");
        for (Satellite satellite : satellites) {
            double health = satellite.getHealthPercent(now);
            String state = health >= 75 ? "NOMINAL" : health >= 40 ? "DEGRADED" : "CRITICAL";
            sb.append("  ")
                .append(satellite.formatLabel())
                .append(": ")
                .append(formatPercent(health))
                .append(" health (")
                .append(state)
                .append(")\n");
        }
        return new CommandResult(sb.toString().trim());
    }

    private static CommandResult connectCommand(CommandContext ctx) {
        if (ctx.args().length < 1) {
            return new CommandResult("Usage: connect <access-code>");
        }
        return ctx.terminal().connectToSatellite(ctx.world(), ctx.args()[0]);
    }

    private static CommandResult disconnectCommand(CommandContext ctx) {
        return ctx.terminal().disconnectSatellite();
    }

    private static CommandResult satInfoCommand(CommandContext ctx) {
        return ctx.terminal().describeSatellite(ctx.world());
    }

    private static CommandResult deploySatelliteCommand(CommandContext ctx) {
        if (ctx.args().length < 2) {
            return new CommandResult("Usage: deploysat <type> <access-code>");
        }
        return ctx.terminal().deploySatellite(ctx.world(), ctx.args()[0], ctx.args()[1]);
    }


    private static CommandResult satcmdCommand(CommandContext ctx) {
        if (ctx.args().length < 1) {
            return new CommandResult("Usage: satcmd <command> [args...]");
        }
        String satelliteCommand = ctx.args()[0];
        String[] forwardArgs = Arrays.copyOfRange(ctx.args(), 1, ctx.args().length);
        return ctx.terminal().executeSatelliteCommand(ctx.world(), satelliteCommand, forwardArgs);
    }
    
    private static List<Satellite> getSatellites(ServerWorld world) {
        return new ArrayList<>(SatelliteRegistry.get(world).getAllSatellites());
    }
    
    private static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }
    
    private static String formatPercent(double value) {
        double clamped = Math.max(0, Math.min(100, value));
        return String.format(Locale.US, "%.1f%%", clamped);
    }
    
    /**
     * Create a random firework stack with colorful effects
     */
    private static net.minecraft.item.ItemStack createRandomFireworkStack() {
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(net.minecraft.item.Items.FIREWORK_ROCKET);
        
        // Random colors
        int[] colors = new int[]{
            0xFF0000, // Red
            0x00FF00, // Green
            0x0000FF, // Blue
            0xFFFF00, // Yellow
            0xFF00FF, // Magenta
            0x00FFFF  // Cyan
        };
        
        // Select random colors
        java.util.List<Integer> selectedColors = new java.util.ArrayList<>();
        int colorCount = 1 + (int)(Math.random() * 3);
        for (int i = 0; i < colorCount; i++) {
            selectedColors.add(colors[(int)(Math.random() * colors.length)]);
        }
        
        // Create firework explosion
        net.minecraft.component.type.FireworkExplosionComponent.Type explosionType = 
            net.minecraft.component.type.FireworkExplosionComponent.Type.values()[(int)(Math.random() * 5)];
        
        boolean hasTrail = Math.random() > 0.5;
        boolean hasTwinkle = Math.random() > 0.5;
        
        net.minecraft.component.type.FireworkExplosionComponent explosion = 
            new net.minecraft.component.type.FireworkExplosionComponent(
                explosionType,
                it.unimi.dsi.fastutil.ints.IntList.of(selectedColors.stream().mapToInt(Integer::intValue).toArray()),
                it.unimi.dsi.fastutil.ints.IntList.of(), // No fade colors
                hasTrail,
                hasTwinkle
            );
        
        // Create fireworks component with flight duration (1-3)
        int flightDuration = 1 + (int)(Math.random() * 3);
        net.minecraft.component.type.FireworksComponent fireworksComponent = 
            new net.minecraft.component.type.FireworksComponent(
                flightDuration,
                java.util.List.of(explosion)
            );
        
        // Set the fireworks component on the stack
        stack.set(net.minecraft.component.DataComponentTypes.FIREWORKS, fireworksComponent);
        
        return stack;
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
        SatelliteControlBlockEntity terminal
    ) {}
    
    /**
     * Result of a command execution
     */
    public record CommandResult(String output, boolean clearScreen) {
        public CommandResult(String output) {
            this(output, false);
        }
    }
}

