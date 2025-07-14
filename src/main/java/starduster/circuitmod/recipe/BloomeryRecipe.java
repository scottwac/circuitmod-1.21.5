package starduster.circuitmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public record BloomeryRecipe(Ingredient inputItem, ItemStack output) implements Recipe<BloomeryRecipeInput> {

    public DefaultedList<Ingredient> getIngredients() {
        DefaultedList<Ingredient> list = DefaultedList.of();
        list.add(this.inputItem);
        return list;
    }

    @Override
    public boolean matches(BloomeryRecipeInput input, World world) {
        if(world.isClient()) {
            return false;
        }

        return inputItem.test(input.getStackInSlot(0));
    }

    @Override
    public ItemStack craft(BloomeryRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        return output.copy();
    }

    @Override
    public RecipeSerializer<? extends Recipe<BloomeryRecipeInput>> getSerializer() {
        return ModRecipes.BLOOMERY_SERIALIZER;
    }

    @Override
    public RecipeType<? extends Recipe<BloomeryRecipeInput>> getType() {
        return ModRecipes.BLOOMERY_TYPE;
    }

    @Override
    public IngredientPlacement getIngredientPlacement() {
        return IngredientPlacement.forSingleSlot(inputItem);
    }

    @Override
    public RecipeBookCategory getRecipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public static class Serializer implements RecipeSerializer<BloomeryRecipe> {
        public static final MapCodec<BloomeryRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(BloomeryRecipe::inputItem),
                ItemStack.CODEC.fieldOf("result").forGetter(BloomeryRecipe::output)
        ).apply(inst, BloomeryRecipe::new));

        public static final PacketCodec<RegistryByteBuf, BloomeryRecipe> STREAM_CODEC =
                PacketCodec.tuple(
                        Ingredient.PACKET_CODEC, BloomeryRecipe::inputItem,
                        ItemStack.PACKET_CODEC, BloomeryRecipe::output,
                        BloomeryRecipe::new);

        @Override
        public MapCodec<BloomeryRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, BloomeryRecipe> packetCodec() {
            return STREAM_CODEC;
        }
    }
}
