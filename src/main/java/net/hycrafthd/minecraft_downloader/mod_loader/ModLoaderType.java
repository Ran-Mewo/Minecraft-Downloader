package net.hycrafthd.minecraft_downloader.mod_loader;

/**
 * Enum for the different types of mod loaders
 */
public enum ModLoaderType {
    FABRIC("Fabric"),
    FORGE("Forge"),
    NEOFORGE("NeoForge");
    
    private final String name;
    
    ModLoaderType(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Get a ModLoaderType from a string
     * 
     * @param name The name of the mod loader
     * @return The ModLoaderType or null if not found
     */
    public static ModLoaderType fromString(String name) {
        if (name == null) {
            return null;
        }
        
        for (ModLoaderType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.getName().equalsIgnoreCase(name)) {
                return type;
            }
        }
        
        return null;
    }
}
