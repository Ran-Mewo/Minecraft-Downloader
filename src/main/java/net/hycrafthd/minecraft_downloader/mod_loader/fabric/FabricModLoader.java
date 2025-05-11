package net.hycrafthd.minecraft_downloader.mod_loader.fabric;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.hycrafthd.minecraft_downloader.Constants;
import net.hycrafthd.minecraft_downloader.Main;
import net.hycrafthd.minecraft_downloader.mod_loader.AbstractModLoader;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.hycrafthd.minecraft_downloader.util.FileUtil;

public class FabricModLoader extends AbstractModLoader {

    private static final String FABRIC_META_URL = "https://meta.fabricmc.net/v2";
    private static final String FABRIC_MAVEN_URL = "https://maven.fabricmc.net";

    private String intermediaryVersion;

    public FabricModLoader(String minecraftVersion, String loaderVersion) {
        super(minecraftVersion, loaderVersion);
    }

    @Override
    public String getName() {
        return "Fabric";
    }

    @Override
    public List<File> downloadAndInstall(ProvidedSettings settings) {
        Main.LOGGER.info("Downloading and installing Fabric mod loader");

        // If no loader version is specified, get the latest
        if (loaderVersion == null) {
            loaderVersion = getLatestLoaderVersion();
            Main.LOGGER.info("Using latest Fabric loader version: {}", loaderVersion);
        }

        // Get the intermediary version for this Minecraft version
        intermediaryVersion = getIntermediaryVersion();
        Main.LOGGER.info("Using Fabric intermediary version: {}", intermediaryVersion);

        // Create the mod loader directory
        File modLoaderDir = new File(settings.getOutputDirectory(), "fabric");
        FileUtil.createFolders(modLoaderDir);

        // Get the launcher metadata
        JsonObject launcherMeta = getLauncherMetadata();
        if (launcherMeta == null) {
            throw new RuntimeException("Failed to get Fabric launcher metadata");
        }

        // Get the main class
        if (!launcherMeta.has("mainClass") || !launcherMeta.getAsJsonObject("mainClass").has("client")) {
            Main.LOGGER.error("Fabric launcher metadata does not contain mainClass.client field: {}", launcherMeta);
            throw new RuntimeException("Invalid Fabric launcher metadata format");
        }
        String mainClass = launcherMeta.getAsJsonObject("mainClass").get("client").getAsString();
        setMainClass(settings, mainClass);

        // Download the required libraries
        downloadLibraries(modLoaderDir, launcherMeta);

        return classpathFiles;
    }

    @Override
    public List<String> getAdditionalJvmArguments() {
        List<String> args = new ArrayList<>();

        try {
            // Get the launcher metadata
            URL profileUrl = new URL(FABRIC_META_URL + "/versions/loader/" + minecraftVersion + "/" + loaderVersion + "/profile/json");
            Main.LOGGER.debug("Fetching Fabric profile from {}", profileUrl);

            JsonObject profile;
            try (InputStreamReader reader = new InputStreamReader(profileUrl.openStream())) {
                profile = JsonParser.parseReader(reader).getAsJsonObject();
            }

            // Extract JVM arguments from the profile
            if (profile.has("arguments") && profile.getAsJsonObject("arguments").has("jvm")) {
                JsonArray jvmArgs = profile.getAsJsonObject("arguments").getAsJsonArray("jvm");
                for (JsonElement arg : jvmArgs) {
                    if (arg.isJsonPrimitive()) {
                        args.add(arg.getAsString());
                    }
                }
                Main.LOGGER.debug("Found {} JVM arguments in Fabric profile", args.size());
            }
        } catch (IOException e) {
            Main.LOGGER.warn("Failed to get Fabric JVM arguments from profile, using defaults", e);
        }

        // Add default Fabric-specific JVM arguments if none were found
        if (args.isEmpty()) {
            args.add("-Dfabric.gameJarPath=${primary_jar}");
            args.add("-Dfabric.development=false");
        }

        return args;
    }

    @Override
    public List<String> getAdditionalGameArguments() {
        List<String> args = new ArrayList<>();

        try {
            // Get the launcher metadata
            URL profileUrl = new URL(FABRIC_META_URL + "/versions/loader/" + minecraftVersion + "/" + loaderVersion + "/profile/json");
            Main.LOGGER.debug("Fetching Fabric profile from {}", profileUrl);

            JsonObject profile;
            try (InputStreamReader reader = new InputStreamReader(profileUrl.openStream())) {
                profile = JsonParser.parseReader(reader).getAsJsonObject();
            }

            // Extract game arguments from the profile
            if (profile.has("arguments") && profile.getAsJsonObject("arguments").has("game")) {
                JsonArray gameArgs = profile.getAsJsonObject("arguments").getAsJsonArray("game");
                for (JsonElement arg : gameArgs) {
                    if (arg.isJsonPrimitive()) {
                        args.add(arg.getAsString());
                    }
                }
                Main.LOGGER.debug("Found {} game arguments in Fabric profile", args.size());
            }
        } catch (IOException e) {
            Main.LOGGER.warn("Failed to get Fabric game arguments from profile", e);
        }

        return args;
    }

    private String getLatestLoaderVersion() {
        try {
            Main.LOGGER.info("Looking for latest Fabric loader version");

            URL url = new URL(FABRIC_META_URL + "/versions/loader");
            Main.LOGGER.debug("Fetching Fabric loader versions from {}", url);

            try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
                JsonArray versions = JsonParser.parseReader(reader).getAsJsonArray();
                Main.LOGGER.debug("Found {} Fabric loader versions", versions.size());

                if (versions.size() > 0) {
                    JsonObject version = versions.get(0).getAsJsonObject();
                    String loaderVersion = version.get("version").getAsString();
                    Main.LOGGER.info("Latest Fabric loader version: {}", loaderVersion);
                    return loaderVersion;
                }
            }
        } catch (IOException e) {
            Main.LOGGER.error("Failed to get latest Fabric loader version", e);
            throw new RuntimeException("Failed to get latest Fabric loader version", e);
        }

        throw new RuntimeException("No Fabric loader versions found");
    }

    private String getIntermediaryVersion() {
        try {
            Main.LOGGER.info("Looking for Fabric intermediary version for Minecraft {}", minecraftVersion);

            URL url = new URL(FABRIC_META_URL + "/versions/intermediary/" + minecraftVersion);
            Main.LOGGER.debug("Fetching Fabric intermediary versions from {}", url);

            try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
                JsonArray versions = JsonParser.parseReader(reader).getAsJsonArray();
                Main.LOGGER.debug("Found {} Fabric intermediary versions", versions.size());

                if (versions.size() > 0) {
                    JsonObject version = versions.get(0).getAsJsonObject();
                    String intermediaryVersion = version.get("version").getAsString();
                    Main.LOGGER.info("Fabric intermediary version for Minecraft {}: {}", minecraftVersion, intermediaryVersion);
                    return intermediaryVersion;
                }
            }
        } catch (IOException e) {
            Main.LOGGER.error("Failed to get Fabric intermediary version for Minecraft {}", minecraftVersion, e);
            throw new RuntimeException("Failed to get Fabric intermediary version for Minecraft " + minecraftVersion, e);
        }

        throw new RuntimeException("No Fabric intermediary version found for Minecraft " + minecraftVersion);
    }

    private JsonObject getLauncherMetadata() {
        try {
            // First, get the loader version info
            URL loaderUrl = new URL(FABRIC_META_URL + "/versions/loader/" + minecraftVersion + "/" + loaderVersion);
            Main.LOGGER.debug("Fetching Fabric loader info from {}", loaderUrl);

            JsonObject loaderInfo;
            try (InputStreamReader reader = new InputStreamReader(loaderUrl.openStream())) {
                loaderInfo = JsonParser.parseReader(reader).getAsJsonObject();
            }

            // Then, get the full profile
            URL profileUrl = new URL(FABRIC_META_URL + "/versions/loader/" + minecraftVersion + "/" + loaderVersion + "/profile/json");
            Main.LOGGER.debug("Fetching Fabric profile from {}", profileUrl);

            JsonObject profile;
            try (InputStreamReader reader = new InputStreamReader(profileUrl.openStream())) {
                profile = JsonParser.parseReader(reader).getAsJsonObject();
            }

            // Create a custom launcher metadata object
            JsonObject launcherMeta = new JsonObject();

            // Add main class info
            JsonObject mainClass = new JsonObject();
            mainClass.addProperty("client", "net.fabricmc.loader.impl.launch.knot.KnotClient");
            mainClass.addProperty("server", "net.fabricmc.loader.impl.launch.knot.KnotServer");
            launcherMeta.add("mainClass", mainClass);

            // Add libraries
            JsonObject libraries = new JsonObject();

            // Extract libraries from profile
            if (profile.has("libraries")) {
                // Check if libraries is an object or array
                if (profile.get("libraries").isJsonObject()) {
                    libraries = profile.getAsJsonObject("libraries");
                } else if (profile.get("libraries").isJsonArray()) {
                    // If it's an array, convert to our expected format
                    JsonArray librariesArray = profile.getAsJsonArray("libraries");
                    JsonArray common = new JsonArray();

                    // Add all libraries to common
                    for (JsonElement element : librariesArray) {
                        common.add(element);
                    }

                    libraries.add("common", common);
                }
            } else {
                // Create a default libraries object
                JsonArray common = new JsonArray();
                libraries.add("common", common);
            }

            launcherMeta.add("libraries", libraries);

            return launcherMeta;
        } catch (IOException e) {
            Main.LOGGER.error("Failed to get Fabric launcher metadata", e);
            throw new RuntimeException("Failed to get Fabric launcher metadata", e);
        }
    }

    private void downloadLibraries(File modLoaderDir, JsonObject launcherMeta) {
        try {
            // Download fabric-loader
            String loaderPath = "net/fabricmc/fabric-loader/" + loaderVersion + "/fabric-loader-" + loaderVersion + ".jar";
            File loaderFile = new File(modLoaderDir, "fabric-loader-" + loaderVersion + ".jar");
            String loaderUrl = FABRIC_MAVEN_URL + "/" + loaderPath;

            downloadFile(loaderUrl, loaderFile);

            // Download intermediary
            String intermediaryPath = "net/fabricmc/intermediary/" + intermediaryVersion + "/intermediary-" + intermediaryVersion + ".jar";
            File intermediaryFile = new File(modLoaderDir, "intermediary-" + intermediaryVersion + ".jar");
            String intermediaryUrl = FABRIC_MAVEN_URL + "/" + intermediaryPath;

            downloadFile(intermediaryUrl, intermediaryFile);

            // Check if launcherMeta has libraries
            if (!launcherMeta.has("libraries")) {
                Main.LOGGER.error("Fabric launcher metadata does not contain libraries field: {}", launcherMeta);
                throw new RuntimeException("Invalid Fabric launcher metadata format");
            }

            JsonObject libraries = launcherMeta.getAsJsonObject("libraries");

            // Download common libraries
            if (libraries.has("common")) {
                JsonArray commonLibraries = libraries.getAsJsonArray("common");
                for (JsonElement element : commonLibraries) {
                    try {
                        if (element.isJsonObject()) {
                            JsonObject library = element.getAsJsonObject();
                            if (library.has("name")) {
                                String name = library.get("name").getAsString();
                                String url = library.has("url") ? library.get("url").getAsString() : FABRIC_MAVEN_URL + "/";

                                downloadLibrary(modLoaderDir, name, url);
                            } else {
                                Main.LOGGER.warn("Library object does not have a name: {}", library);
                            }
                        } else {
                            Main.LOGGER.warn("Library element is not an object: {}", element);
                        }
                    } catch (Exception e) {
                        Main.LOGGER.warn("Failed to process library: {}", element, e);
                    }
                }
            } else {
                Main.LOGGER.warn("No common libraries found in Fabric launcher metadata");
            }

            // Download client libraries if any
            if (libraries.has("client")) {
                JsonArray clientLibraries = libraries.getAsJsonArray("client");
                for (JsonElement element : clientLibraries) {
                    try {
                        if (element.isJsonObject()) {
                            JsonObject library = element.getAsJsonObject();
                            if (library.has("name")) {
                                String name = library.get("name").getAsString();
                                String url = library.has("url") ? library.get("url").getAsString() : FABRIC_MAVEN_URL + "/";

                                downloadLibrary(modLoaderDir, name, url);
                            } else {
                                Main.LOGGER.warn("Library object does not have a name: {}", library);
                            }
                        } else {
                            Main.LOGGER.warn("Library element is not an object: {}", element);
                        }
                    } catch (Exception e) {
                        Main.LOGGER.warn("Failed to process library: {}", element, e);
                    }
                }
            }
        } catch (Exception e) {
            Main.LOGGER.error("Failed to download Fabric libraries", e);
            throw new RuntimeException("Failed to download Fabric libraries", e);
        }
    }

    // Additional Maven repositories to try
    private static final String[] ADDITIONAL_MAVEN_REPOS = {
        "https://maven.minecraftforge.net/",
        "https://maven.neoforged.net/releases/",
        "https://repo.maven.apache.org/maven2/"
    };

    private void downloadLibrary(File modLoaderDir, String mavenCoordinates, String repositoryUrl) {
        try {
            // Parse maven coordinates
            String[] parts = mavenCoordinates.split(":");
            if (parts.length < 3) {
                Main.LOGGER.warn("Invalid maven coordinates: {}", mavenCoordinates);
                return;
            }

            String groupId = parts[0];
            String artifactId = parts[1];
            String version = parts[2];

            // Handle classifier if present
            String classifier = "";
            if (parts.length > 3) {
                classifier = "-" + parts[3];
            }

            // Create path
            String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + classifier + ".jar";
            File file = new File(modLoaderDir, artifactId + "-" + version + classifier + ".jar");

            // Try the primary repository first
            String url = repositoryUrl + path;

            try {
                downloadFile(url, file);
            } catch (Exception e) {
                // If the primary repository fails, try additional repositories
                boolean downloaded = false;

                // Try Minecraft libraries repository first (handled by AbstractModLoader.downloadFile)
                try {
                    String minecraftUrl = MINECRAFT_LIBRARIES_URL + path;
                    downloadFile(minecraftUrl, file);
                    downloaded = true;
                } catch (Exception e2) {
                    // Try other repositories
                    for (String repo : ADDITIONAL_MAVEN_REPOS) {
                        if (repo.equals(repositoryUrl)) {
                            continue; // Skip if it's the same as the primary repository
                        }

                        try {
                            String altUrl = repo + path;
                            Main.LOGGER.info("Trying alternative repository: {}", altUrl);
                            downloadFile(altUrl, file);
                            downloaded = true;
                            break;
                        } catch (Exception e3) {
                            // Continue to the next repository
                        }
                    }
                }

                if (!downloaded) {
                    // If all repositories fail, log a warning
                    Main.LOGGER.warn("Failed to download library from all repositories: {}", mavenCoordinates);
                    throw e; // Rethrow the original exception
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to download library: {}", mavenCoordinates, e);
        }
    }
}
