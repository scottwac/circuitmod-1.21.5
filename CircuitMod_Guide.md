# CircuitMod - Complete Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Energy System](#energy-system)
3. [Power Infrastructure](#power-infrastructure)
4. [Mining Machines](#mining-machines)
5. [Processing Machines](#processing-machines)
6. [Advanced Machines](#advanced-machines)
7. [Materials & Ores](#materials--ores)
8. [Progression Guide](#progression-guide)
9. [Tips & Strategies](#tips--strategies)

## Introduction

CircuitMod is an industrial Minecraft mod that adds electricity-based automation, mining, and processing systems. The mod focuses on power generation, distribution, and consumption through various machines for mining, smelting, crushing, and construction.

## Energy System

### Power Generation

#### Combustion Generator
- **Energy Output**: 6 energy per tick while burning
- **Fuel**: Uses any vanilla fuel (coal, lava buckets, etc.)
- **Crafting**: 
  ```
  AIA  A = Aluminum Ingot, I = Redstone
  CFC  C = Copper Ingot, F = Furnace
  SSS  S = Steel Ingot
  ```

#### Solar Generator
- **Energy Output**: 1-10 energy per tick (varies by time and weather)
- **Peak Output**: 10 energy/tick at noon with clear skies
- **Weather Effects**: 30% efficiency in rain, 10% in thunderstorms
- **Night Output**: 0 energy/tick
- **Prerequisites**: Solar Cell → Solar Module → Solar Generator
- **Solar Cell Crafting**:
  ```
  GGG  G = Glass Pane
  LRL  L = Lapis Lazuli, R = Redstone
  ACA  A = Aluminum Ingot, C = Copper Ingot
  ```
- **Solar Module Crafting**:
  ```
  CCC  C = Solar Cell
  CCC
  CCC
  ```
- **Solar Generator Crafting**:
  ```
  SSS  S = Solar Module
  ARA  A = Aluminum Ingot, R = Redstone
  ACA  C = Copper Ingot
  ```

#### Fission Reactor
- **Energy Output**: 60 energy per fuel rod per tick + 30% bonus per additional rod
- **Max Fuel Rods**: 9
- **Example**: 
  - 1 rod = 60 energy/tick
  - 2 rods = 138 energy/tick (120 + 18 bonus)
  - 9 rods = 684 energy/tick (540 + 144 bonus)
- **Fuel**: Fuel Rods (consume durability over time)
- **Zirconium Tube Crafting** (needed for reactor and fuel rods):
  ```
  Z  Z = Zirconium Ingot
  Z
  Z
  ```
- **Fuel Rod Crafting**:
  ```
  UUU  U = Uranium Pellet
  UZU  Z = Zirconium Tube
  UUU
  ```
- **Reactor Crafting**:
  ```
  ZZZ  Z = Zirconium Tube
  AWA  A = Aluminum Ingot, W = Water Bucket
  RCR  R = Redstone Block, C = Copper Ingot
  ```

#### Creative Generators (Creative Mode)
- **Creative Generator**: 1000 energy/tick
- **Mega Creative Generator**: 100,000 energy/tick

### Power Consumption

#### High Consumption Machines
- **Quarry**: Up to 1000 energy/tick (demand scales with energy availability)
- **Constructor**: Up to 1000 energy/tick (100 energy per block placed)
- **Drill**: Up to 1000 energy/tick
- **Laser Mining Drill**: Up to 1000 energy/tick

#### Low Consumption Machines
- **Crusher**: 1 energy/tick when active
- **Electric Furnace**: Variable energy consumption while smelting
- **Mass Fabricator**: 100,000 energy total per item

## Power Infrastructure

### Cables
- **Power Cable**: Transfers energy between machines
- **Output**: 12 cables per craft
- **Crafting**:
  ```
  BBB  B = Black Wool
  CCC  C = Copper Ingot
  BBB
  ```

### Battery Block
- **Function**: Stores energy for the power network
- **Crafting**:
  ```
  CAC  C = Copper Ingot, A = Aluminum Ingot
  BRB  B = Black Wool, R = Redstone Block
  BRB
  ```

## Mining Machines

### Quarry
- **Function**: Mines a 16x16 area downward from bedrock level
- **Energy**: Up to 1000 energy/tick (faster with more power)
- **Mining Speed**: Varies by block hardness and energy received
- **Storage**: 12 inventory slots
- **Crafting**:
  ```
  APA  A = Aluminum Ingot, P = Diamond Pickaxe
  IRI  I = Iron Ingot, R = Redstone
  ACA  C = Copper Ingot
  ```

### Drill
- **Function**: Mines vertically downward in a single column
- **Energy**: Up to 1000 energy/tick
- **Depth**: Configurable mining depth
- **Crafting**:
  ```
  AIA  A = Aluminum Ingot, I = Iron Ingot
  PRI  P = Diamond Pickaxe, R = Redstone
  ACA  C = Copper Ingot
  ```

### Laser Mining Drill
- **Function**: Advanced drill with laser mining capabilities
- **Energy**: Up to 1000 energy/tick
- **Crafting**:
  ```
  APA  A = Aluminum Ingot, P = Diamond Pickaxe
  IDI  I = End Crystal, D = Diamond Block
  ACA  C = Copper Ingot
  ```

## Processing Machines

### Crusher
- **Function**: Crushes ores into 2x crushed ore + stone dust
- **Energy**: 1 energy/tick when active
- **Processing Time**: 30 ticks (1.5 seconds)
- **Recipes**:
  - Bauxite Ore → 2x Crushed Bauxite + Stone Dust
  - Lead Ore → 2x Lead Powder + Stone Dust  
  - Uranium Ore → 2x Crushed Uranium + Stone Dust
  - Zircon Ore → 2x Zirconium Powder + Stone Dust
- **Crafting**:
  ```
  S S  S = Steel Ingot
  IHI  I = Iron Ingot, H = Hopper
  ICI  C = Copper Ingot
  ```

### Electric Furnace
- **Function**: Smelts items faster than regular furnace with double output for vanilla recipes
- **Energy**: Variable consumption while active
- **Benefits**: 
  - 2x output for vanilla smelting recipes
  - Normal output for custom electric furnace recipes
- **Crafting**:
  ```
  AAA  A = Aluminum Ingot
  AFA  F = Furnace
  SSS  S = Steel Ingot
  ```

### Bloomery
- **Function**: Converts raw iron into steel bloom (for steel production)
- **Energy**: None (uses fuel like a furnace)
- **Recipe**: Raw Iron → Steel Bloom
- **Crafting**:
  ```
  M M  M = Packed Mud
  MFM  F = Furnace
  MMM
  ```

## Advanced Machines

### Constructor
- **Function**: Builds structures from blueprints automatically
- **Energy**: 1000 energy/tick demand, 100 energy per block placed
- **Features**:
  - Uses blueprints from Blueprint Desk
  - 13 inventory slots (1 for blueprint, 12 for materials)
  - Configurable placement offset and rotation
- **Crafting**:
  ```
  ARA  A = Aluminum Ingot, R = Redstone Block
  GTG  G = Diamond, T = Crafting Table
  AAA
  ```

### Blueprint Desk
- **Function**: Scans areas and creates construction blueprints
- **Usage**: Place two desks to define scan area corners
- **Output**: 2 blueprint desks per craft
- **Crafting**:
  ```
  ABA  A = Aluminum Ingot, B = Blank Blueprint
  RTA  R = Redstone, T = Crafting Table
  ACA  C = Copper Ingot
  ```

### Tesla Coil
- **Function**: Damages nearby entities with electricity
- **Energy**: Consumes power when active
- **Range**: Damages entities in nearby area
- **Crafting**:
  ```
  AAA  A = Aluminum Ingot
  ACA  C = Copper Block
  SRS  S = Steel Ingot, R = Redstone Torch
  ```

### Mass Fabricator
- **Function**: Creates valuable materials from energy
- **Energy**: 100,000 energy per item
- **Outputs**: Diamond, Emerald, Netherite Ingot, Gold Ingot
- **Crafting**:
  ```
  ONO  O = Obsidian, N = Netherite Ingot
  DGD  D = Diamond, G = Gold Block
  ERE  E = Emerald, R = Redstone Block
  ```

## Utility Items & Weapons

### Blank Blueprint
- **Function**: Required for crafting Blueprint Desk
- **Output**: 4 blank blueprints per craft
- **Crafting**:
  ```
  DDD  D = Blue Dye
  DPD  P = Paper
  DDD
  ```

### Item Pipe
- **Function**: Transports items between machines and containers
- **Output**: 8 item pipes per craft
- **Crafting**:
  ```
  BBB  B = Glass Pane
      (empty middle row)
  BBB
  ```

### Electric Carpet
- **Function**: Damages entities that walk on it (powered by electricity)
- **Energy**: Consumes power when dealing damage
- **Crafting**:
  ```
  CCC  C = Copper Grate
  ```

### Nuclear Bomb
- **Function**: Extremely powerful explosive device
- **Warning**: Use with extreme caution!
- **Crafting**:
  ```
  SUS  S = Steel Ingot, U = Uranium Pellet
  UTU  T = TNT
  UBU  B = Steel Block
  ```

## Materials & Ores

### New Ores
- **Bauxite Ore**: Source of aluminum (found in overworld)
- **Lead Ore**: Source of lead for various recipes
- **Uranium Ore**: Nuclear fuel material
- **Zircon Ore**: Source of zirconium for reactor components

### Key Materials
- **Aluminum Ingot**: Core crafting material for most machines
- **Steel Ingot**: Advanced crafting material (from steel bloom)
- **Lead Ingot**: Used in advanced recipes
- **Uranium Pellet**: Nuclear fuel component
- **Zirconium Ingot**: Reactor construction material

### Processing Chain Examples

#### Aluminum Production
1. Mine Bauxite Ore
2. Crush in Crusher → 2x Crushed Bauxite + Stone Dust
3. Smelt Crushed Bauxite → Aluminum Ingot

#### Steel Production  
1. Mine Iron Ore or use Raw Iron
2. Process in Bloomery → Steel Bloom
3. Smelt Steel Bloom → Steel Ingot

#### Uranium Processing
1. Mine Uranium Ore
2. Crush in Crusher → 2x Crushed Uranium + Stone Dust
3. Smelt Crushed Uranium → Uranium Pellet

## Progression Guide

### Early Game
1. **Mine basic resources**: Iron, copper, coal
2. **Find bauxite ore** for aluminum production
3. **Build bloomery** for steel production
4. **Craft combustion generator** for initial power

### Mid Game
1. **Set up power grid** with cables and battery blocks
2. **Build crusher** for ore processing efficiency
3. **Craft electric furnace** for faster, more efficient smelting
4. **Create quarry** for automated mining

### Late Game
1. **Build fission reactor** for massive power generation
2. **Set up constructor system** with blueprint desk for automation
3. **Create advanced mining systems** with laser drills
4. **Build mass fabricator** for material generation

## Tips & Strategies

### Power Management
- **Start with combustion generators** - reliable but fuel-dependent
- **Solar panels are eco-friendly** but weather/time dependent
- **Reactors provide massive power** but require uranium fuel rods
- **Always use battery blocks** to store excess energy

### Efficiency Tips
- **Use crushers before smelting ores** for double output
- **Electric furnaces give bonus output** for vanilla recipes
- **Quarries work faster with more power** - don't limit energy supply
- **Plan your power grid** - use cables to distribute energy efficiently

### Automation Strategies
- **Quarries provide bulk materials** for large projects
- **Constructors automate building** - scan with blueprint desk first
- **Process ores in bulk** using crusher → electric furnace chains
- **Item pipes help with logistics** for connecting machines

### Safety Notes
- **Tesla coils damage players** - place carefully
- **Reactors need fuel rod management** - monitor durability
- **Power networks require planning** - calculate consumption vs generation
- **Electric carpet damages entities** - use for mob farms or defense

This guide covers the core systems and progression path for CircuitMod. Experiment with different setups to find what works best for your playstyle and projects! 