package dev.dyslexic.cinematic.client.compat.emi;

import dev.dyslexic.cinematic.Cinematic;
import dev.dyslexic.cinematic.client.guide.RecipeViewer;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * Native EMI adapter for the Forge 1.20.1 field guide. This class is only loaded when EMI is present;
 * EMI remains an optional compile-time integration and is never bundled with Cinematic Interactions.
 */
public final class EmiRecipeViewer implements RecipeViewer {
    private static final List<String> NOT_STATION_RECIPES = List.of("jeresources", "justenoughprofessions");
    private static EmiRecipeViewer instance;

    private final EmiRecipeManager manager;

    private EmiRecipeViewer(EmiRecipeManager manager) {
        this.manager = manager;
    }

    @Nullable
    public static RecipeViewer current() {
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        if (manager == null) return null;
        if (instance == null || instance.manager != manager) instance = new EmiRecipeViewer(manager);
        return instance;
    }

    private List<EmiRecipeCategory> stationCategories(ItemStack station) {
        EmiStack target = EmiStack.of(station);
        return manager.getCategories().stream()
                .filter(category -> !NOT_STATION_RECIPES.contains(category.getId().getNamespace()))
                .filter(category -> manager.getWorkstations(category).stream()
                        .anyMatch(workstation -> contains(workstation, target)))
                .toList();
    }

    private static boolean contains(EmiIngredient ingredient, EmiStack target) {
        return ingredient.getEmiStacks().stream().anyMatch(stack -> stack.isEqual(target));
    }

    private static boolean hasOutput(EmiRecipe recipe, ItemStack target) {
        EmiStack wanted = EmiStack.of(target);
        return recipe.getOutputs().stream().anyMatch(output -> output.isEqual(wanted));
    }

    private static void addOutput(Map<String, ItemStack> outputs, EmiStack output) {
        ItemStack stack = output.getItemStack();
        if (stack.isEmpty()) return;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        String key = BuiltInRegistries.ITEM.getKey(copy.getItem()) + "|" + copy.getTag();
        outputs.putIfAbsent(key, copy);
    }

    @Override
    public List<ItemStack> search(String query, int limit) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || limit <= 0) return List.of();
        String id = q.replace(' ', '_');
        List<ItemStack> found = new ArrayList<>();
        for (EmiStack indexed : EmiApi.getIndexStacks()) {
            ItemStack stack = indexed.getItemStack();
            if (stack.isEmpty()) continue;
            if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || indexed.getId().toString().toLowerCase(Locale.ROOT).contains(id)) {
                found.add(stack.copy());
                if (found.size() >= limit) break;
            }
        }
        return found;
    }

    @Override
    public List<ItemStack> stationOutputs(ItemStack station) {
        Map<String, ItemStack> outputs = new LinkedHashMap<>();
        for (EmiRecipeCategory category : stationCategories(station)) {
            for (EmiRecipe recipe : manager.getRecipes(category)) {
                for (EmiStack output : recipe.getOutputs()) addOutput(outputs, output);
            }
        }
        List<ItemStack> result = new ArrayList<>(outputs.values());
        result.sort(Comparator.comparing(stack -> stack.getHoverName().getString()));
        return List.copyOf(result);
    }

    @Override
    public List<StationRecipe> stationRecipes(ItemStack station, ItemStack output) {
        List<StationRecipe> found = new ArrayList<>();
        for (EmiRecipeCategory category : stationCategories(station)) {
            for (EmiRecipe recipe : manager.getRecipes(category)) {
                if (hasOutput(recipe, output)) found.add(new EmiStationRecipe(recipe));
            }
        }
        // Put real shaped crafting recipes (tools, armor, stairs, etc.) before EMI's synthetic
        // two-of-the-same-item repair recipe, which otherwise tends to appear first.
        found.sort(Comparator.comparingInt(EmiRecipeViewer::recipePriority));
        return List.copyOf(found);
    }

    private static int recipePriority(StationRecipe stationRecipe) {
        if (!(stationRecipe instanceof EmiStationRecipe emi)) return 5;
        net.minecraft.world.item.crafting.Recipe<?> backing = emi.recipe.getBackingRecipe();
        if (backing instanceof ShapedRecipe) return 0;
        if (backing != null) return 1;
        return 10;
    }

    @Override
    public void showHowToGet(ItemStack stack) {
        EmiStack target = EmiStack.of(stack);
        Cinematic.LOGGER.info("[Cinematic] EMI show recipes: item={}, matches={}, screen={}",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), manager.getRecipesByOutput(target).size(),
                Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName());
        EmiApi.displayRecipes(target);
        Cinematic.LOGGER.info("[Cinematic] EMI show recipes returned: screen={}",
                Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName());
    }

    @Override
    public void showUses(ItemStack stack) {
        EmiStack target = EmiStack.of(stack);
        Cinematic.LOGGER.info("[Cinematic] EMI show uses: item={}, matches={}, screen={}",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), manager.getRecipesByInput(target).size(),
                Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName());
        EmiApi.displayUses(target);
        Cinematic.LOGGER.info("[Cinematic] EMI show uses returned: screen={}",
                Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName());
    }

    @Override
    public List<SourcePage> sources(ItemStack stack) {
        return List.of();
    }

    @Nullable
    @Override
    public Runnable professionPage(ItemStack station) {
        return null;
    }

    // EMI does not expose its recipe/uses key mappings through the public API.
    @Override
    public boolean isShowRecipeKey(int key, int scanCode) {
        return false;
    }

    @Override
    public boolean isShowUsesKey(int key, int scanCode) {
        return false;
    }

    @Override
    public Component keyNames() {
        return Component.literal("the buttons below");
    }

    private static final class EmiStationRecipe implements StationRecipe {
        private final EmiRecipe recipe;

        private EmiStationRecipe(EmiRecipe recipe) {
            this.recipe = recipe;
        }

        @Override
        public Component category() {
            return recipe.getCategory().getName();
        }

        @Nullable
        @Override
        public ResourceLocation recipeId() {
            return recipe.getId();
        }

        @Override
        public List<ItemStack> inputs() {
            List<ItemStack> inputs = new ArrayList<>();
            for (EmiIngredient ingredient : recipe.getInputs()) {
                ItemStack stack = ingredient.getEmiStacks().get(0).getItemStack();
                inputs.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
            return List.copyOf(inputs);
        }

        @Override
        public int width() {
            return 134;
        }

        @Override
        public int height() {
            return 58;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
            // Preserve the actual pattern for vanilla shaped recipes (stairs, tools, armor, etc.).
            // EMI ingredients are alternatives, so the guide previews the first representative stack per slot.
            net.minecraft.world.item.crafting.Recipe<?> backing = recipe.getBackingRecipe();
            if (backing instanceof ShapedRecipe shaped) {
                List<Ingredient> ingredients = shaped.getIngredients();
                int recipeWidth = shaped.getWidth();
                int recipeHeight = shaped.getHeight();
                for (int row = 0; row < recipeHeight; row++) {
                    for (int col = 0; col < recipeWidth; col++) {
                        int index = row * recipeWidth + col;
                        if (index >= ingredients.size()) continue;
                        ItemStack[] choices = ingredients.get(index).getItems();
                        if (choices.length > 0 && !choices[0].isEmpty()) {
                            graphics.renderItem(choices[0], x + 2 + col * 18, y + 2 + row * 18);
                        }
                    }
                }
            } else {
                // Non-vanilla and shapeless recipes have no public positional layout in EMI's recipe API.
                // Use a compact 3-column grid rather than falsely presenting every ingredient in one row.
                List<EmiIngredient> ingredients = recipe.getInputs();
                for (int i = 0; i < Math.min(9, ingredients.size()); i++) {
                    List<EmiStack> choices = ingredients.get(i).getEmiStacks();
                    if (choices.isEmpty()) continue;
                    ItemStack stack = choices.get(0).getItemStack();
                    if (!stack.isEmpty()) {
                        graphics.renderItem(stack, x + 2 + (i % 3) * 18, y + 2 + (i / 3) * 18);
                    }
                }
            }
            graphics.drawString(Minecraft.getInstance().font, "→", x + 76, y + 24, 0xFF5A4A37, false);
            for (EmiStack output : recipe.getOutputs()) {
                ItemStack stack = output.getItemStack();
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, x + 108, y + 20);
                    break;
                }
            }
        }

        @Override
        public void open() {
            Cinematic.LOGGER.info("[Cinematic] EMI open recipe: id={}, category={}, screen={}",
                    recipe.getId(), recipe.getCategory().getId(),
                    Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName());
            EmiApi.displayRecipe(recipe);
            Cinematic.LOGGER.info("[Cinematic] EMI open recipe returned: screen={}",
                    Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName());
        }
    }
}
