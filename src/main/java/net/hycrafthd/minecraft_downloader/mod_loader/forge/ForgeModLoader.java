package net.hycrafthd.minecraft_downloader.mod_loader.forge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.hycrafthd.minecraft_downloader.Main;
import net.hycrafthd.minecraft_downloader.mod_loader.AbstractModLoader;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.hycrafthd.minecraft_downloader.util.FileUtil;

public class ForgeModLoader extends AbstractModLoader {

    private static final String FORGE_MAVEN_URL = "https://maven.minecraftforge.net";
    private static final String FORGE_FILES_URL = "https://files.minecraftforge.net/net/minecraftforge/forge";

    public ForgeModLoader(String minecraftVersion, String forgeVersion) {
        super(minecraftVersion, forgeVersion);
    }

    @Override
    public String getName() {
        return "Forge";
    }

    @Override
    public List<File> downloadAndInstall(ProvidedSettings settings) {
        Main.LOGGER.info("Downloading and installing Forge mod loader");

        // If no forge version is specified, get the latest for this Minecraft version
        if (loaderVersion == null) {
            loaderVersion = getLatestForgeVersion();
            Main.LOGGER.info("Using latest Forge version for Minecraft {}: {}", minecraftVersion, loaderVersion);
        }

        // Create the mod loader directory
        File modLoaderDir = new File(settings.getOutputDirectory(), "forge");
        FileUtil.createFolders(modLoaderDir);

        // Download the Forge installer
        String installerFileName = "forge-" + loaderVersion + "-installer.jar";
        File installerFile = new File(modLoaderDir, installerFileName);
        String installerUrl = FORGE_MAVEN_URL + "/net/minecraftforge/forge/" + loaderVersion + "/" + installerFileName;

        Main.LOGGER.info("Downloading Forge installer from {}", installerUrl);
        FileUtil.downloadFileException(installerUrl, installerFile, -1, null, "Failed to download Forge installer");

        // Extract the version info
        JsonObject versionJson = extractInstallProfile(installerFile, "version.json");
        String mainClass = versionJson.get("mainClass").getAsString();

        setMainClass(settings, mainClass);

        // Process the libraries from the install profile
        if (versionJson.has("libraries")) {
            JsonArray libraries = versionJson.getAsJsonArray("libraries");
            for (JsonElement element : libraries) {
                JsonObject library = element.getAsJsonObject();
                if (library.has("name")) {
                    String name = library.get("name").getAsString();
                    String url = library.has("url") ? library.get("url").getAsString() : FORGE_MAVEN_URL + "/";

                    // Download the library
                    downloadMavenLibrary(modLoaderDir, name, url);
                }
            }
        }

        // Process the libraries from the version JSON
        if (versionJson.has("libraries")) {
            JsonArray libraries = versionJson.getAsJsonArray("libraries");
            for (JsonElement element : libraries) {
                JsonObject library = element.getAsJsonObject();
                if (library.has("name")) {
                    String name = library.get("name").getAsString();
                    String url = library.has("url") ? library.get("url").getAsString() : FORGE_MAVEN_URL + "/";

                    // Download the library
                    downloadMavenLibrary(modLoaderDir, name, url);
                }
            }
        }

        return classpathFiles;
    }

    @Override
    public List<String> getAdditionalJvmArguments() {
        List<String> args = new ArrayList<>();

        // Add default Forge JVM arguments
        args.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
        args.add("-Dfml.ignorePatchDiscrepancies=true");

        // Try to extract JVM arguments from version.json
        try {
            // Create the mod loader directory
            File modLoaderDir = new File(new File(System.getProperty("java.io.tmpdir")), "forge-" + loaderVersion);
            FileUtil.createFolders(modLoaderDir);

            // Download the Forge installer if we don't have it already
            String installerFileName = "forge-" + loaderVersion + "-installer.jar";
            File installerFile = new File(modLoaderDir, installerFileName);
            if (!installerFile.exists()) {
                String installerUrl = FORGE_MAVEN_URL + "/net/minecraftforge/forge/" + loaderVersion + "/" + installerFileName;
                FileUtil.downloadFileException(installerUrl, installerFile, -1, null, "Failed to download Forge installer");
            }

            // Extract the version info
            JsonObject versionJson = extractInstallProfile(installerFile, "version.json");

            // Extract JVM arguments from the version.json
            if (versionJson.has("arguments") && versionJson.getAsJsonObject("arguments").has("jvm")) {
                JsonArray jvmArgs = versionJson.getAsJsonObject("arguments").getAsJsonArray("jvm");

                // Process each argument
                for (JsonElement arg : jvmArgs) {
                    if (arg.isJsonPrimitive()) {
                        String argStr = arg.getAsString();

                        // Replace classpath_separator with the correct value
                        argStr = argStr.replace("${classpath_separator}", File.pathSeparator);

                        // Add the argument
                        args.add(argStr);
                    }
                }
                Main.LOGGER.debug("Found {} JVM arguments in Forge version.json", args.size());

                // Add critical module path arguments that might be missing
                boolean hasAddOpensJavaLangInvoke = false;
                for (String arg : args) {
                    if (arg.contains("--add-opens java.base/java.lang.invoke=")) {
                        hasAddOpensJavaLangInvoke = true;
                        break;
                    }
                }

                if (!hasAddOpensJavaLangInvoke) {
                    Main.LOGGER.info("Adding missing module opens for java.lang.invoke");
                    args.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to extract JVM arguments from Forge version.json, using defaults", e);

            // Add essential JVM arguments for Forge
            args.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
            args.add("--add-opens=java.base/java.util.jar=ALL-UNNAMED");
            args.add("--add-opens=java.base/sun.security.util=ALL-UNNAMED");
            args.add("--add-exports=java.base/sun.security.util=ALL-UNNAMED");
        }

        return args;
    }

    @Override
    public List<String> getAdditionalGameArguments() {
        List<String> args = new ArrayList<>();

        // Try to extract game arguments from version.json
        try {
            // Create the mod loader directory
            File modLoaderDir = new File(new File(System.getProperty("java.io.tmpdir")), "forge-" + loaderVersion);
            FileUtil.createFolders(modLoaderDir);

            // Download the Forge installer if we don't have it already
            String installerFileName = "forge-" + loaderVersion + "-installer.jar";
            File installerFile = new File(modLoaderDir, installerFileName);
            if (!installerFile.exists()) {
                String installerUrl = FORGE_MAVEN_URL + "/net/minecraftforge/forge/" + loaderVersion + "/" + installerFileName;
                FileUtil.downloadFileException(installerUrl, installerFile, -1, null, "Failed to download Forge installer");
            }

            // Extract the version info
            JsonObject versionJson = extractInstallProfile(installerFile, "version.json");

            // Extract game arguments from the version.json
            if (versionJson.has("arguments") && versionJson.getAsJsonObject("arguments").has("game")) {
                JsonArray gameArgs = versionJson.getAsJsonObject("arguments").getAsJsonArray("game");
                for (JsonElement arg : gameArgs) {
                    if (arg.isJsonPrimitive()) {
                        args.add(arg.getAsString());
                    }
                }
                Main.LOGGER.debug("Found {} game arguments in Forge version.json", args.size());
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to extract game arguments from Forge version.json", e);
        }

        return args;
    }

    private String getLatestForgeVersion() {
        try {
            // For Forge, we'll use a different approach
            // We'll try to find the latest version by checking the existence of the installer file

            Main.LOGGER.info("Looking for latest Forge version for Minecraft {}", minecraftVersion);

            // Try to find the latest version by checking the existence of the installer file
            // We'll start with a high version number and work our way down
            for (int i = 100; i >= 1; i--) {
                String version = minecraftVersion + "-" + i + "." + i + "." + i;
                String installerUrl = FORGE_MAVEN_URL + "/net/minecraftforge/forge/" + version + "/forge-" + version + "-installer.jar";

                try {
                    URL url = new URL(installerUrl);
                    url.openStream().close();

                    // If we get here, the file exists
                    Main.LOGGER.info("Found Forge version: {}", version);
                    return version;
                } catch (IOException e) {
                    // File doesn't exist, try the next version
                }
            }

            // If we get here, we couldn't find a version
            throw new RuntimeException("No Forge version found for Minecraft " + minecraftVersion);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get latest Forge version for Minecraft " + minecraftVersion, e);
        }
    }

    /**
     * Download a Maven library
     *
     * @param modLoaderDir The mod loader directory
     * @param mavenCoordinates The Maven coordinates (e.g., "org.example:library:1.0.0")
     * @param repositoryUrl The repository URL
     */
    // Additional Maven repositories to try
    private static final String[] ADDITIONAL_MAVEN_REPOS = {
        "https://maven.neoforged.net/releases/",
        "https://repo.maven.apache.org/maven2/",
        "https://maven.fabricmc.net/"
    };

    private void downloadMavenLibrary(File modLoaderDir, String mavenCoordinates, String repositoryUrl) {
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
