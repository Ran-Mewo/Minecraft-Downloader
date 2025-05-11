package net.hycrafthd.minecraft_downloader;

import java.io.File;
import java.util.List;
import java.util.Set;

import net.hycrafthd.minecraft_downloader.mod_loader.ModLoader;
import net.hycrafthd.minecraft_downloader.settings.GeneratedSettings;
import net.hycrafthd.minecraft_downloader.settings.LauncherVariables;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;

public class MinecraftModLoader {

	public static void launch(ProvidedSettings settings, ModLoader modLoader) {
		Main.LOGGER.info("Start mod loader setup for {}", modLoader.getName());

		// Download and install the mod loader
		List<File> modLoaderFiles = modLoader.downloadAndInstall(settings);

		// Add mod loader files to classpath
		addModLoaderToClasspath(settings, modLoaderFiles);

		// Set mod loader variables
		setModLoaderVariables(settings, modLoader);

		Main.LOGGER.info("Finished mod loader setup");
	}

	private static void addModLoaderToClasspath(ProvidedSettings settings, List<File> modLoaderFiles) {
		Main.LOGGER.info("Adding mod loader files to classpath");

		final GeneratedSettings generatedSettings = settings.getGeneratedSettings();

		// Store mod loader files for later use
		Main.LOGGER.debug("Storing {} mod loader files for later classpath building", modLoaderFiles.size());
		for (File file : modLoaderFiles) {
			Main.LOGGER.debug("Will add to classpath: {}", file.getAbsolutePath());
		}

		// Store the mod loader files in the settings for later use
		settings.getGeneratedSettings().setModLoaderFiles(modLoaderFiles);
	}

	private static void setModLoaderVariables(ProvidedSettings settings, ModLoader modLoader) {
		Main.LOGGER.info("Setting mod loader variables");

		// Set mod loader type and version
		settings.addVariable(LauncherVariables.MOD_LOADER_TYPE, modLoader.getName());
		settings.addVariable(LauncherVariables.MOD_LOADER_VERSION, modLoader.getVersion());

		// Make sure the main class is set
		String mainClass = settings.getVariable(LauncherVariables.MOD_LOADER_MAIN_CLASS);
		if (mainClass == null || mainClass.isEmpty()) {
			Main.LOGGER.warn("Mod loader main class is not set, mod loader may not work properly");
		} else {
			Main.LOGGER.info("Mod loader main class: {}", mainClass);
		}

		// Get additional JVM arguments
		List<String> jvmArgs = modLoader.getAdditionalJvmArguments();
		if (!jvmArgs.isEmpty()) {
			Main.LOGGER.debug("Mod loader requires additional JVM arguments: {}", String.join(" ", jvmArgs));
			// These will be added in the ArgumentsParser
		}

		// Get additional game arguments
		List<String> gameArgs = modLoader.getAdditionalGameArguments();
		if (!gameArgs.isEmpty()) {
			Main.LOGGER.debug("Mod loader requires additional game arguments: {}", String.join(" ", gameArgs));
			// These will be added in the ArgumentsParser
		}
	}
}
