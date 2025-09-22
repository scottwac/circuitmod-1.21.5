package starduster.circuitmod.util;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.item.ModItems;

public class ModCreativeTabs {
    public void onInitialize() {

        }
    public static void initialize() {
        Circuitmod.LOGGER.info("ModCreativeTabs initialized");

        Registry.register(Registries.ITEM_GROUP, Identifier.of(Circuitmod.MOD_ID, "main"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ModItems.STEEL_INGOT))
                        .displayName(Text.translatable("itemgroup.circuitmod.main"))
                        .entries((context, entries) -> {
                            // Add blocks first
                            entries.add(ModBlocks.BATTERY);
                            entries.add(ModBlocks.POWER_CABLE);
                            entries.add(ModBlocks.BLOOMERY);
                            entries.add(ModBlocks.CRUSHER);
                            entries.add(ModBlocks.ELECTRIC_FURNACE);
                            entries.add(ModBlocks.XP_GENERATOR);
                            entries.add(ModBlocks.GENERATOR);
                            entries.add(ModBlocks.SOLAR_PANEL);
                            entries.add(ModBlocks.TESLA_COIL);
                            entries.add(ModBlocks.DRILL_BLOCK);
                            entries.add(ModBlocks.LASER_MINING_DRILL_BLOCK);
                            entries.add(ModBlocks.QUARRY_BLOCK);
                            entries.add(ModBlocks.CONSTRUCTOR_BLOCK);
                            entries.add(ModBlocks.BLUEPRINT_DESK);


                            entries.add(ModBlocks.ELECTRIC_CARPET);
                            entries.add(ModBlocks.ITEM_PIPE);
                            entries.add(ModBlocks.OUTPUT_PIPE);
                            entries.add(ModBlocks.SORTING_PIPE);
                            entries.add(ModBlocks.REACTOR_BLOCK);
                            entries.add(ModBlocks.NUKE);

                            // Add ingots and materials
                            entries.add(ModItems.BLOOM);

                            // Add ores
                            entries.add(ModBlocks.BAUXITE_ORE);
                            entries.add(ModBlocks.DEEPSLATE_BAUXITE_ORE);
                            entries.add(ModBlocks.LEAD_ORE);
                            entries.add(ModBlocks.DEEPSLATE_LEAD_ORE);
                            entries.add(ModBlocks.URANIUM_ORE);
                            entries.add(ModBlocks.DEEPSLATE_URANIUM_ORE);
                            entries.add(ModBlocks.ZIRCON_ORE);
                            entries.add(ModBlocks.DEEPSLATE_ZIRCON_ORE);

                            entries.add(ModItems.RAW_BAUXITE);
                            entries.add(ModItems.RAW_LEAD);
                            entries.add(ModItems.RAW_URANIUM);
                            entries.add(ModItems.ZIRCON);

                            entries.add(ModItems.CRUSHED_BAUXITE);
                            entries.add(ModItems.CRUSHED_URANIUM);

                            entries.add(ModItems.LEAD_POWDER);
                            entries.add(ModItems.ZIRCONIUM_POWDER);
                            entries.add(ModItems.STONE_DUST);

                            entries.add(ModItems.ALUMINUM_INGOT);
                            entries.add(ModItems.LEAD_INGOT);
                            entries.add(ModItems.STEEL_INGOT);
                            entries.add(ModItems.URANIUM_PELLET);
                            entries.add(ModItems.FUEL_ROD);
                            entries.add(ModItems.ZIRCONIUM_INGOT);

                            entries.add(ModItems.GRAPHITE);
                            entries.add(ModItems.GRAPHITE_POWDER);

                            entries.add(ModItems.IRON_POLE);
                            entries.add(ModItems.ZIRCONIUM_TUBE);

                            entries.add(ModBlocks.STEEL_BLOCK);

                            entries.add(ModItems.STEEL_SHOVEL);
                            entries.add(ModItems.STEEL_PICKAXE);
                            entries.add(ModItems.STEEL_AXE);
                            entries.add(ModItems.STEEL_HOE);
                            entries.add(ModItems.STEEL_SWORD);

                            // Add blank blueprint item
                            entries.add(ModItems.BLANK_BLUEPRINT);

                            // Add movement items
                            entries.add(ModItems.PULSE_STICK);

                            entries.add(ModItems.STIMULANTS);


                            // Add more mod items here as they are created
                        })
                        .build());

        Registry.register(Registries.ITEM_GROUP, Identifier.of(Circuitmod.MOD_ID, "alt"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ModItems.BLOOM))
                        .displayName(Text.translatable("itemgroup.circuitmod.alt"))
                        .entries((context, entries) -> {

                            entries.add(ModBlocks.CREATIVE_GENERATOR); // Add creative generator to creative tab
                            entries.add(ModBlocks.MEGA_CREATIVE_GENERATOR); // Add mega creative generator to creative tab
                            entries.add(ModBlocks.CREATIVE_CONSUMER); // Add creative consumer to creative tab

                            entries.add(ModBlocks.FLUID_TANK);

                            entries.add(ModItems.NATURAL_RUBBER);
                            entries.add(ModItems.SYNTHETIC_RUBBER);
                            entries.add(ModItems.PLASTIC_PELLET);
                            entries.add(ModItems.PLASTIC_BAR);

                            entries.add(ModBlocks.LAUNCH_PAD);

                            // Add consumable items
                            entries.add(ModItems.MINING_EXPLOSIVE);

                        })
                        .build());


        Registry.register(Registries.ITEM_GROUP, Identifier.of(Circuitmod.MOD_ID, "lunar"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ModBlocks.LUNAR_REGOLITH))
                        .displayName(Text.translatable("itemgroup.circuitmod.lunar"))
                        .entries((context, entries) -> {

                            entries.add(ModBlocks.LUNAR_REGOLITH);
                            entries.add(ModBlocks.LUNAR_EJECTA);
                            entries.add(ModBlocks.LUNAR_ANORTHOSITE);
                            entries.add(ModBlocks.LUNAR_BASALT);
                            entries.add(ModBlocks.LUNAR_DEEPBASALT);
                            entries.add(ModBlocks.LUNAR_MANTLEROCK);
                            entries.add(ModBlocks.LUNAR_BRECCIA);
                            entries.add(ModBlocks.LUNAR_IMPACT_GLASS);
                            entries.add(ModBlocks.LUNAR_ICE);

                            entries.add(ModBlocks.ANORTHOSITE_IRON_ORE);
                            entries.add(ModBlocks.ANORTHOSITE_COPPER_ORE);
                            entries.add(ModBlocks.ANORTHOSITE_GOLD_ORE);
                            entries.add(ModBlocks.ANORTHOSITE_DIAMOND_ORE);
                            entries.add(ModBlocks.ANORTHOSITE_ZIRCON_ORE);
                            entries.add(ModBlocks.ANORTHOSITE_TITANIUM_ORE);

                            entries.add(ModBlocks.DEEPBASALT_IRON_ORE);
                            entries.add(ModBlocks.DEEPBASALT_COPPER_ORE);
                            entries.add(ModBlocks.DEEPBASALT_GOLD_ORE);
                            entries.add(ModBlocks.DEEPBASALT_DIAMOND_ORE);
                            entries.add(ModBlocks.DEEPBASALT_ZIRCON_ORE);
                            entries.add(ModBlocks.DEEPBASALT_URANIUM_ORE);
                            entries.add(ModBlocks.DEEPBASALT_TITANIUM_ORE);

                            entries.add(ModBlocks.MANTLEROCK_DIAMOND_ORE);
                            entries.add(ModBlocks.MANTLEROCK_URANIUM_ORE);
                            entries.add(ModBlocks.MANTLEROCK_TITANIUM_ORE);

                        })
                        .build());
    }
}
