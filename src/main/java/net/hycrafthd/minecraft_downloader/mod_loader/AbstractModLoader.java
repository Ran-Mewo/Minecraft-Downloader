package net.hycrafthd.minecraft_downloader.mod_loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.hycrafthd.minecraft_downloader.Main;
import net.hycrafthd.minecraft_downloader.settings.LauncherVariables;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.hycrafthd.minecraft_downloader.util.FileUtil;

/**
 * Abstract base class for mod loaders
 */
public abstract class AbstractModLoader implements ModLoader {

    protected final String minecraftVersion;
    protected String loaderVersion;
    protected final List<File> classpathFiles = new ArrayList<>();
    protected String mainClass;

    public AbstractModLoader(String minecraftVersion, String loaderVersion) {
        this.minecraftVersion = minecraftVersion;
        this.loaderVersion = loaderVersion;
    }

    @Override
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public String getVersion() {
        return loaderVersion;
    }

    /**
     * Extract the install profile JSON from the installer JAR
     *
     * @param installerFile The installer JAR file
     * @param entryName The name of the entry in the JAR (e.g., "install_profile.json")
     * @return The install profile as a JsonObject
     */
    protected JsonObject extractInstallProfile(File installerFile, String entryName) {
        try (JarFile jarFile = new JarFile(installerFile)) {
            ZipEntry entry = jarFile.getEntry(entryName);
            if (entry == null) {
                throw new RuntimeException("Installer does not contain " + entryName);
            }

            try (InputStream is = jarFile.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(is)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract " + entryName + " from installer", e);
        }
    }

    /**
     * Extract a file from the installer JAR
     *
     * @param installerFile The installer JAR file
     * @param entryName The name of the entry in the JAR
     * @param outputFile The output file
     */
    protected void extractFile(File installerFile, String entryName, File outputFile) {
        try (JarFile jarFile = new JarFile(installerFile)) {
            ZipEntry entry = jarFile.getEntry(entryName);
            if (entry == null) {
                throw new RuntimeException("Installer does not contain " + entryName);
            }

            // Create parent directories
            FileUtil.createParentFolders(outputFile);

            try (InputStream is = jarFile.getInputStream(entry);
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            classpathFiles.add(outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract " + entryName + " from installer", e);
        }
    }

    /**
     * Set the main class in the provided settings
     *
     * @param settings The provided settings
     * @param mainClass The main class
     */
    protected void setMainClass(ProvidedSettings settings, String mainClass) {
        this.mainClass = mainClass;
        Main.LOGGER.info("{} main class: {}", getName(), mainClass);
        settings.addVariable(LauncherVariables.MOD_LOADER_MAIN_CLASS, mainClass);
    }

    // Default Minecraft libraries repository
    protected static final String MINECRAFT_LIBRARIES_URL = "https://libraries.minecraft.net/";

    /**
     * Download a file from a URL
     *
     * @param url The URL to download from
     * @param outputFile The output file
     */
    protected void downloadFile(String url, File outputFile) {
        // Create parent directories
        FileUtil.createParentFolders(outputFile);

        try {
            Main.LOGGER.info("Downloading {} from {}", outputFile.getName(), url);
            FileUtil.downloadFileException(url, outputFile, -1, null, "Failed to download " + outputFile.getName());
        } catch (Exception e) {
            // If download fails, try the Minecraft libraries repository as a fallback
            if (!url.startsWith(MINECRAFT_LIBRARIES_URL)) {
                String path = url.substring(url.indexOf("/", 8)); // Skip protocol and domain
                String fallbackUrl = MINECRAFT_LIBRARIES_URL + path.substring(1); // Remove leading slash

                try {
                    Main.LOGGER.info("Primary download failed, trying Minecraft repository: {}", fallbackUrl);
                    FileUtil.downloadFileException(fallbackUrl, outputFile, -1, null, "Failed to download " + outputFile.getName());
                } catch (Exception e2) {
                    // If both attempts fail, throw the original exception
                    throw e;
                }
            } else {
                // If already using Minecraft repository, rethrow the exception
                throw e;
            }
        }

        classpathFiles.add(outputFile);
    }
}
