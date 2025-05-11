package net.hycrafthd.minecraft_downloader.mod_loader;

import net.hycrafthd.minecraft_downloader.Main;
import net.hycrafthd.minecraft_downloader.mod_loader.fabric.FabricModLoader;
import net.hycrafthd.minecraft_downloader.mod_loader.forge.ForgeModLoader;
import net.hycrafthd.minecraft_downloader.mod_loader.neoforge.NeoForgeModLoader;

/**
 * Factory for creating mod loaders
 */
public class ModLoaderFactory {
    
    /**
     * Create a mod loader
     * 
     * @param type The type of mod loader
     * @param minecraftVersion The Minecraft version
     * @param loaderVersion The loader version (can be null for latest)
     * @return The mod loader
     */
    public static ModLoader createModLoader(ModLoaderType type, String minecraftVersion, String loaderVersion) {
        if (type == null) {
            return null;
        }
        
        Main.LOGGER.info("Creating {} mod loader for Minecraft {}, loader version: {}", 
                type.getName(), minecraftVersion, loaderVersion != null ? loaderVersion : "latest");
        
        switch (type) {
            case FABRIC:
                return new FabricModLoader(minecraftVersion, loaderVersion);
            case FORGE:
                return new ForgeModLoader(minecraftVersion, loaderVersion);
            case NEOFORGE:
                return new NeoForgeModLoader(minecraftVersion, loaderVersion);
            default:
                throw new IllegalArgumentException("Unknown mod loader type: " + type);
        }
    }
}
