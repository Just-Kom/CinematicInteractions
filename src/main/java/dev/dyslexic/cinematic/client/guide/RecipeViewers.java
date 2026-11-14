package dev.dyslexic.cinematic.client.guide;

import dev.dyslexic.cinematic.Cinematic;
import javax.annotation.Nullable;
//? if neoforge {
import net.neoforged.fml.ModList;
//?} else if fabric {
/*import net.fabricmc.loader.api.FabricLoader;
*///?} else if forge {
/*import net.minecraftforge.fml.ModList;
*///?}

/** The installed recipe viewer, if any. Prefer native EMI support, then fall back to JEI. */
public final class RecipeViewers {
    private RecipeViewers() {}

    private static boolean emiFailed;
    private static boolean jeiFailed;

    /** Load the Forge-only adapter without making shared sources depend on a Forge-only class. */
    @Nullable
    private static RecipeViewer loadForgeEmi() throws ReflectiveOperationException {
        Class<?> adapter = Class.forName("dev.dyslexic.cinematic.client.compat.emi.EmiRecipeViewer");
        Object viewer = adapter.getMethod("current").invoke(null);
        return viewer instanceof RecipeViewer recipeViewer ? recipeViewer : null;
    }

    /** The viewer once its runtime is ready (after the world has loaded its recipes), else null. */
    @Nullable
    public static RecipeViewer get() {
        //? if forge {
        if (!emiFailed && ModList.get().isLoaded("emi")) {
            try {
                RecipeViewer viewer = loadForgeEmi();
                if (viewer != null) return viewer;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                emiFailed = true;
                Cinematic.LOGGER.warn("[Cinematic] EMI integration unavailable; trying the next recipe viewer", e);
            }
        }
        //?}

        //? if neoforge {
        if (jeiFailed || !ModList.get().isLoaded("jei")) {
        //?} else if fabric {
        /*if (jeiFailed || !FabricLoader.getInstance().isModLoaded("jei")) {
        *///?} else if forge {
        /*if (jeiFailed || !ModList.get().isLoaded("jei")) {
        *///?}
            return null;
        }
        try {
            // the adapter names JEI's classes: it is loaded only when JEI is installed
            return dev.dyslexic.cinematic.client.compat.jei.JeiRecipeViewer.current();
        } catch (LinkageError | RuntimeException e) {
            jeiFailed = true;
            Cinematic.LOGGER.warn("[Cinematic] JEI integration unavailable; the scenes go on without the field guide", e);
            return null;
        }
    }
}
