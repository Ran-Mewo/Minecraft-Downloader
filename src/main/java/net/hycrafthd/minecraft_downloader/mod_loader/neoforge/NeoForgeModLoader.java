package net.hycrafthd.minecraft_downloader.mod_loader.neoforge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

public class NeoForgeModLoader extends AbstractModLoader {

    private static final String NEOFORGE_MAVEN_URL = "https://maven.neoforged.net/releases";
    private static final String NEOFORGE_VERSIONS_API = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";

    public NeoForgeModLoader(String minecraftVersion, String neoforgeVersion) {
        super(minecraftVersion, neoforgeVersion);
    }

    @Override
    public String getName() {
        return "NeoForge";
    }

    @Override
    public List<File> downloadAndInstall(ProvidedSettings settings) {
        Main.LOGGER.info("Downloading and installing NeoForge mod loader");

        // If no neoforge version is specified, get the latest for this Minecraft version
        if (loaderVersion == null) {
            loaderVersion = getLatestNeoForgeVersion();
            Main.LOGGER.info("Using latest NeoForge version for Minecraft {}: {}", minecraftVersion, loaderVersion);
        }

        // Create the mod loader directory
        File modLoaderDir = new File(settings.getOutputDirectory(), "neoforge");
        FileUtil.createFolders(modLoaderDir);

        // Download the NeoForge installer
        String installerFileName = "neoforge-" + loaderVersion + "-installer.jar";
        File installerFile = new File(modLoaderDir, installerFileName);
        String installerUrl = NEOFORGE_MAVEN_URL + "/net/neoforged/neoforge/" + loaderVersion + "/" + installerFileName;

        Main.LOGGER.info("Downloading NeoForge installer from {}", installerUrl);
        FileUtil.downloadFileException(installerUrl, installerFile, -1, null, "Failed to download NeoForge installer");

        // Extract the version JSON
        JsonObject versionJson = extractInstallProfile(installerFile, "version.json");

        // Get the main class
        String mainClass = versionJson.get("mainClass").getAsString();
        setMainClass(settings, mainClass);

        // Process the libraries from the version JSON
        if (versionJson.has("libraries")) {
            JsonArray libraries = versionJson.getAsJsonArray("libraries");
            for (JsonElement element : libraries) {
                JsonObject library = element.getAsJsonObject();
                if (library.has("name")) {
                    String name = library.get("name").getAsString();
                    String url = library.has("url") ? library.get("url").getAsString() : NEOFORGE_MAVEN_URL + "/";

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

        // Add default NeoForge JVM arguments
        args.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
        args.add("-Dfml.ignorePatchDiscrepancies=true");

        // Try to extract JVM arguments from version.json
        try {
            // Create the mod loader directory
            File modLoaderDir = new File(new File(System.getProperty("java.io.tmpdir")), "neoforge-" + loaderVersion);
            FileUtil.createFolders(modLoaderDir);

            // Download the NeoForge installer if we don't have it already
            String installerFileName = "neoforge-" + loaderVersion + "-installer.jar";
            File installerFile = new File(modLoaderDir, installerFileName);
            if (!installerFile.exists()) {
                String installerUrl = NEOFORGE_MAVEN_URL + "/net/neoforged/neoforge/" + loaderVersion + "/" + installerFileName;
                FileUtil.downloadFileException(installerUrl, installerFile, -1, null, "Failed to download NeoForge installer");
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
                Main.LOGGER.debug("Found {} JVM arguments in NeoForge version.json", args.size());

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
            Main.LOGGER.warn("Failed to extract JVM arguments from NeoForge version.json, using defaults", e);

            // Add essential JVM arguments for NeoForge
            args.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
            args.add("--add-opens=java.base/java.util.jar=ALL-UNNAMED");
            args.add("--add-opens=java.base/sun.security.util=ALL-UNNAMED");
            args.add("--add-exports=java.base/sun.security.util=ALL-UNNAMED");
            args.add("--add-exports=jdk.naming.dns/com.sun.jndi.dns=java.naming");
        }

        return args;
    }

    @Override
    public List<String> getAdditionalGameArguments() {
        List<String> args = new ArrayList<>();

        // Try to extract game arguments from version.json
        try {
            // Create the mod loader directory
            File modLoaderDir = new File(new File(System.getProperty("java.io.tmpdir")), "neoforge-" + loaderVersion);
            FileUtil.createFolders(modLoaderDir);

            // Download the NeoForge installer if we don't have it already
            String installerFileName = "neoforge-" + loaderVersion + "-installer.jar";
            File installerFile = new File(modLoaderDir, installerFileName);
            if (!installerFile.exists()) {
                String installerUrl = NEOFORGE_MAVEN_URL + "/net/neoforged/neoforge/" + loaderVersion + "/" + installerFileName;
                FileUtil.downloadFileException(installerUrl, installerFile, -1, null, "Failed to download NeoForge installer");
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
                Main.LOGGER.debug("Found {} game arguments in NeoForge version.json", args.size());
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to extract game arguments from NeoForge version.json", e);
        }

        return args;
    }

    private String getLatestNeoForgeVersion() {
        try {
            // Cut the "1." part from the Minecraft version (e.g., 1.21.5 -> 21.5)
            String cutVersion = minecraftVersion;
            if (cutVersion.startsWith("1.")) {
                cutVersion = cutVersion.substring(2);
            }

            Main.LOGGER.info("Looking for NeoForge versions for Minecraft {} (cut version: {})", minecraftVersion, cutVersion);

            // Get the list of NeoForge versions from the API
            URL url = new URL(NEOFORGE_VERSIONS_API);
            try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
                JsonObject response = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray versions = response.getAsJsonArray("versions");

                // Find versions that match the cut Minecraft version
                List<String> matchingVersions = new ArrayList<>();
                for (JsonElement element : versions) {
                    String version = element.getAsString();
                    if (version.startsWith(cutVersion + ".")) {
                        matchingVersions.add(version);
                    }
                }

                if (matchingVersions.isEmpty()) {
                    throw new RuntimeException("No NeoForge version found for Minecraft " + minecraftVersion);
                }

                // Sort versions in reverse order to get the latest
                Collections.sort(matchingVersions, Collections.reverseOrder());

                // Get the latest version
                String latestVersion = matchingVersions.get(0);
                Main.LOGGER.info("Found latest NeoForge version for Minecraft {}: {}", minecraftVersion, latestVersion);

                return latestVersion;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get latest NeoForge version for Minecraft " + minecraftVersion, e);
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
        "https://maven.minecraftforge.net/",
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
