package net.hycrafthd.minecraft_downloader.mod_loader;

import java.io.File;
import java.util.List;

import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;

/**
 * Interface for all mod loaders
 */
public interface ModLoader {
    
    /**
     * Get the name of the mod loader
     * 
     * @return The name of the mod loader
     */
    String getName();
    
    /**
     * Get the version of the mod loader
     * 
     * @return The version of the mod loader
     */
    String getVersion();
    
    /**
     * Get the Minecraft version this mod loader is for
     * 
     * @return The Minecraft version
     */
    String getMinecraftVersion();
    
    /**
     * Download and install the mod loader
     * 
     * @param settings The provided settings
     * @return List of files that should be added to the classpath
     */
    List<File> downloadAndInstall(ProvidedSettings settings);
    
    /**
     * Get additional JVM arguments required by this mod loader
     * 
     * @return List of additional JVM arguments
     */
    List<String> getAdditionalJvmArguments();
    
    /**
     * Get additional game arguments required by this mod loader
     * 
     * @return List of additional game arguments
     */
    List<String> getAdditionalGameArguments();
}
