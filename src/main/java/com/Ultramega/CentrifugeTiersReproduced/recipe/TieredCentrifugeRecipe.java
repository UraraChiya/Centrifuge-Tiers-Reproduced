package com.ultramega.centrifugetiersreproduced.recipe;

import com.ultramega.centrifugetiersreproduced.CentrifugeTiersReproduced;
import com.ultramega.centrifugetiersreproduced.blockentity.InventoryHandlerHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import cy.jdkdigital.productivebees.ProductiveBeesConfig;
import cy.jdkdigital.productivebees.common.recipe.CentrifugeRecipe;
import cy.jdkdigital.productivebees.init.ModRecipeTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class TieredCentrifugeRecipe extends CentrifugeRecipe {
    public final ResourceLocation id;
    public final Ingredient ingredient;
    public final Map<String, Integer> fluidOutput;
    private final Integer processingTime;

    public TieredCentrifugeRecipe(ResourceLocation id, Ingredient ingredient, Map<Ingredient, IntArrayTag> itemOutput, Map<String, Integer> fluidOutput, int processingTime) {
        super(id, ingredient, itemOutput, fluidOutput, processingTime);
        this.id = id;
        this.ingredient = ingredient;
        this.fluidOutput = fluidOutput;
        this.processingTime = processingTime;
    }

    public int getProcessingTime() {
        return this.processingTime > 0 ? this.processingTime : ProductiveBeesConfig.GENERAL.centrifugeProcessingTime.get();
    }

    @Override
    public boolean matches(Container inv, Level worldIn) {
        if (this.ingredient.getItems().length > 0) {
            ItemStack invStack = inv.getItem(InventoryHandlerHelper.INPUT_SLOT[0]);

            for (ItemStack stack : this.ingredient.getItems()) {
                if (stack.getItem().equals(invStack.getItem())) {
                    // Check configurable honeycombs
                    if (stack.hasTag() && invStack.hasTag()) {
                        return stack.getTag().equals(invStack.getTag());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public boolean matches(Container inv, Level worldIn, int index) {
        if (this.ingredient.getItems().length > 0) {
            ItemStack invStack = inv.getItem(InventoryHandlerHelper.INPUT_SLOT[index]);

            for (ItemStack stack : this.ingredient.getItems()) {
                if (stack.getItem().equals(invStack.getItem())) {
                    // Check configurable honeycombs
                    if (stack.hasTag() && invStack.hasTag()) {
                        return stack.getTag().equals(invStack.getTag());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Nonnull
    @Override
    public ItemStack assemble(Container inv, RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Nonnull
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Nullable
    public Pair<Fluid, Integer> getFluidOutputs() {
        for (Map.Entry<String, Integer> entry : fluidOutput.entrySet()) {
            Fluid fluid = getPreferredFluidByMod(entry.getKey());

            if (fluid != Fluids.EMPTY) {
                return Pair.of(fluid, entry.getValue());
            }
        }

        return null;
    }

    @Nonnull
    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Nonnull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.CENTRIFUGE.get();
    }

    @Nonnull
    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.CENTRIFUGE_TYPE.get();
    }

    public static class Serializer<T extends TieredCentrifugeRecipe> implements RecipeSerializer<T> {
        final TieredCentrifugeRecipe.Serializer.IRecipeFactory<T> factory;

        public Serializer(TieredCentrifugeRecipe.Serializer.IRecipeFactory<T> factory) {
            this.factory = factory;
        }

        @Override
        public T fromJson(ResourceLocation id, JsonObject json) {
            Ingredient ingredient;
            if (GsonHelper.isArrayNode(json, "ingredient")) {
                ingredient = Ingredient.fromJson(GsonHelper.getAsJsonArray(json, "ingredient"));
            } else {
                ingredient = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "ingredient"));
            }

            JsonArray jsonArray = GsonHelper.getAsJsonArray(json, "outputs");
            Map<Ingredient, IntArrayTag> itemOutputs = new LinkedHashMap<>();
            Map<String, Integer> fluidOutputs = new LinkedHashMap<>();
            jsonArray.forEach(el -> {
                JsonObject jsonObject = el.getAsJsonObject();
                if (jsonObject.has("item")) {
                    int min = GsonHelper.getAsInt(jsonObject, "min", 1);
                    int max = GsonHelper.getAsInt(jsonObject, "max", 1);
                    int chance = GsonHelper.getAsInt(jsonObject, "chance", 100);
                    IntArrayTag nbt = new IntArrayTag(new int[]{min, max, chance});

                    Ingredient produce;
                    if (GsonHelper.isArrayNode(jsonObject, "item")) {
                        produce = Ingredient.fromJson(GsonHelper.getAsJsonArray(jsonObject, "item"));
                    } else {
                        produce = Ingredient.fromJson(GsonHelper.getAsJsonObject(jsonObject, "item"));
                    }

                    itemOutputs.put(produce, nbt);
                } else if (jsonObject.has("fluid")) {
                    int amount = GsonHelper.getAsInt(jsonObject, "amount", 250);

                    JsonObject fluid = GsonHelper.getAsJsonObject(jsonObject, "fluid");
                    String fluidResourceLocation = "";
                    if (fluid.has("tag")) {
                        fluidResourceLocation = GsonHelper.getAsString(fluid, "tag");
                    } else if (fluid.has("fluid")) {
                        fluidResourceLocation = GsonHelper.getAsString(fluid, "fluid");
                    }

                    fluidOutputs.put(fluidResourceLocation, amount);
                }
            });

            int processingTime = json.has("processingTime") ? json.get("processingTime").getAsInt() : -1;
            return this.factory.create(id, ingredient, itemOutputs, fluidOutputs, processingTime);
        }

        @Override
        public T fromNetwork(@Nonnull ResourceLocation id, @Nonnull FriendlyByteBuf buffer) {
            try {
                Ingredient ingredient = Ingredient.fromNetwork(buffer);

                Map<Ingredient, IntArrayTag> itemOutput = new LinkedHashMap<>();
                IntStream.range(0, buffer.readInt()).forEach(
                        i -> itemOutput.put(Ingredient.fromNetwork(buffer), new IntArrayTag(new int[]{buffer.readInt(), buffer.readInt(), buffer.readInt()}))
                );

                Map<String, Integer> fluidOutput = new LinkedHashMap<>();
                IntStream.range(0, buffer.readInt()).forEach(
                        i -> fluidOutput.put(buffer.readUtf(), buffer.readInt())
                );

                return this.factory.create(id, ingredient, itemOutput, fluidOutput, buffer.readInt());
            } catch (Exception e) {
                CentrifugeTiersReproduced.LOGGER.error("Error reading centrifuge recipe from packet. " + id, e);
                throw e;
            }
        }

        @Override
        public void toNetwork(@Nonnull FriendlyByteBuf buffer, @Nonnull T recipe) {
            try {
                recipe.ingredient.toNetwork(buffer);
                buffer.writeInt(recipe.itemOutput.size());

                recipe.itemOutput.forEach((key, value) -> {
                    key.toNetwork(buffer);
                    buffer.writeInt(value.get(0).getAsInt());
                    buffer.writeInt(value.get(1).getAsInt());
                    buffer.writeInt(value.get(2).getAsInt());
                });

                buffer.writeInt(recipe.fluidOutput.size());
                recipe.fluidOutput.forEach((key, value) -> {
                    buffer.writeUtf(key);
                    buffer.writeInt(value);
                });
                buffer.writeInt(recipe.getProcessingTime());
            } catch (Exception e) {
                CentrifugeTiersReproduced.LOGGER.error("Error writing centrifuge recipe to packet. " + recipe.getId(), e);
                throw e;
            }
        }

        public interface IRecipeFactory<T extends TieredCentrifugeRecipe> {
            T create(ResourceLocation id, Ingredient input, Map<Ingredient, IntArrayTag> itemOutput, Map<String, Integer> fluidOutput, Integer processingTime);
        }
    }
}