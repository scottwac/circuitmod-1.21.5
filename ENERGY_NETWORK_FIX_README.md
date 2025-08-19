# Energy Network Desync Fix

## Problem Description

The electric networks in CircuitMod were experiencing desync issues when chunks got unloaded and reloaded. Even though all blocks showed the same merged network ID, they detected different quantities of blocks, making the grid unusable. The only way to fix this was to destroy and pick up all blocks.

## Root Cause

The issue was caused by a lack of global network state management:

1. **No Global Network Registry**: Energy networks were stored locally in each block entity's NBT, but there was no global registry to track all networks across the world.

2. **Chunk Unload/Reload Issues**: When chunks unloaded, network objects in memory were lost. When chunks reloaded, each block entity tried to restore its network from NBT, but:
   - Network IDs might not match between different block entities
   - The `connectedBlocks` map in each network was empty after NBT load
   - Each block entity thought it was in a network, but the network didn't know about it

3. **Desync Symptoms**: 
   - All blocks showed the same merged network ID
   - But each network instance had different block counts
   - This happened because each block entity created its own `EnergyNetwork` instance with the same ID from NBT

## Solution

### 1. Global Energy Network Manager (`EnergyNetworkManager`)

Created a centralized manager that:
- Maintains a global registry of all energy networks by ID
- Maps block positions to their network IDs for quick lookup
- Ensures network consistency across chunk loads/unloads
- Handles network registration, unregistration, and merging globally

### 2. Enhanced Network Persistence

Modified the `EnergyNetwork` class to:
- Register with the global manager when created
- Unregister when destroyed or merged
- Use the global manager for network lookups during NBT loading

### 3. Automatic Network Recovery

Added automatic recovery mechanisms:
- **Periodic Validation**: Every 10 seconds, validates all networks and repairs inconsistencies
- **Global Recovery**: Every 60 seconds, scans for orphaned blocks and reconnects them
- **Chunk Load Recovery**: Automatically attempts to reconnect networks when chunks are loaded

### 4. Commands for Manual Recovery

Added new commands for manual network management:

```
/circuitmod energy-recovery    # Performs immediate network recovery
/circuitmod energy-stats       # Shows current network statistics
```

## How It Works

### Network Creation
1. When a new network is created, it's automatically registered with the global manager
2. All blocks in the network are mapped to the network ID
3. The network persists across chunk loads/unloads

### Network Loading
1. When a block entity loads from NBT, it first tries to find an existing network with the same ID
2. If found, it joins the existing network (maintaining consistency)
3. If not found, it creates a new network and registers it globally

### Network Merging
1. When networks need to merge, the global manager handles the operation
2. All block mappings are updated to point to the merged network
3. The old network is properly unregistered

### Automatic Recovery
1. **Validation**: Checks each network for invalid block references and removes them
2. **Recovery**: Scans for blocks that should be connected but aren't, and reconnects them
3. **Consistency**: Ensures all blocks in a network have consistent references

## Benefits

1. **Eliminates Desync**: Networks maintain consistency across chunk loads/unloads
2. **Automatic Recovery**: Self-healing networks that recover from inconsistencies
3. **Better Performance**: Global tracking reduces redundant network operations
4. **Debugging**: Commands provide visibility into network state
5. **Reliability**: Networks are more robust and less prone to corruption

## Usage

### Automatic Recovery
The system automatically recovers networks every 10-60 seconds. No manual intervention is required.

### Manual Recovery
If you experience issues, use the recovery command:

```
/circuitmod energy-recovery
```

This will:
- Validate all existing networks
- Repair any inconsistencies
- Reconnect orphaned blocks
- Report the results

### Network Statistics
Monitor network health with:

```
/circuitmod energy-stats
```

This shows:
- Total number of networks
- Total number of connected blocks
- Overall network health

## Technical Details

### Files Modified
- `src/main/java/starduster/circuitmod/power/EnergyNetworkManager.java` (new)
- `src/main/java/starduster/circuitmod/power/EnergyNetwork.java` (modified)
- `src/main/java/starduster/circuitmod/block/entity/PowerCableBlockEntity.java` (modified)
- `src/main/java/starduster/circuitmod/command/ModCommands.java` (modified)
- `src/main/java/starduster/circuitmod/power/EnergyNetworkTickHandler.java` (new)
- `src/main/java/starduster/circuitmod/Circuitmod.java` (modified)

### Key Classes
- **`EnergyNetworkManager`**: Global network registry and management
- **`EnergyNetwork`**: Individual network implementation with global manager integration
- **`EnergyNetworkTickHandler`**: Automatic recovery and validation
- **`PowerCableBlockEntity`**: Updated to use global manager

### Performance Impact
- Minimal overhead: Global operations run every 10-60 seconds
- Efficient lookups: O(1) network lookup by block position
- Memory efficient: Only stores essential network metadata globally

## Troubleshooting

### If Networks Still Desync
1. Run `/circuitmod energy-recovery` to force recovery
2. Check logs for any error messages
3. Verify that the mod is properly initialized

### Performance Issues
1. Check network statistics with `/circuitmod energy-stats`
2. Look for unusually large networks (may indicate a bug)
3. Monitor server performance during recovery operations

### Log Messages
The system logs important operations:
- `[ENERGY-TICK] Periodic validation: X networks repaired`
- `[ENERGY-TICK] Global recovery: X blocks reconnected`
- `[ENERGY-TICK] Network status: X networks, Y total blocks`

## Future Improvements

1. **Network Visualization**: Client-side network display
2. **Advanced Diagnostics**: More detailed network health metrics
3. **Performance Optimization**: Smarter recovery algorithms
4. **Configuration**: Configurable recovery intervals and thresholds

## Conclusion

This fix addresses the fundamental issue of energy network desync by introducing proper global state management. Networks are now self-healing and maintain consistency across chunk loads/unloads, eliminating the need to manually destroy and rebuild networks.
