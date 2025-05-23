package net.hycrafthd.minecraft_downloader;

import java.io.File;
import java.util.UUID;
import java.util.stream.Collectors;

import net.hycrafthd.minecraft_downloader.launch.ProcessLaunch;
import net.hycrafthd.minecraft_downloader.mojang_api.CurrentClientJson;
import net.hycrafthd.minecraft_downloader.settings.GeneratedSettings;
import net.hycrafthd.minecraft_downloader.settings.LauncherVariables;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;

public class MinecraftLauncher {

	public static void launch(ProvidedSettings settings, String standardJvmArguments) {
		Main.LOGGER.info("Start minecraft");

		setVariables(settings);
		ProcessLaunch.launch(settings, standardJvmArguments);
	}

	private static void setVariables(ProvidedSettings settings) {
		Main.LOGGER.info("Set variables for start");

		final GeneratedSettings generatedSettings = settings.getGeneratedSettings();

		final CurrentClientJson client = generatedSettings.getClientJson();

		// Add default auth variables if some are not set in authentication
		settings.addDefaultVariable(LauncherVariables.AUTH_PLAYER_NAME, "NotAuthUser");
		settings.addDefaultVariable(LauncherVariables.AUTH_UUID, UUID.randomUUID().toString());
		settings.addDefaultVariable(LauncherVariables.AUTH_ACCESS_TOKEN, "-");
		settings.addDefaultVariable(LauncherVariables.USER_TYPE, "legacy");

		settings.addDefaultVariable(LauncherVariables.AUTH_XUID, "-");
		settings.addDefaultVariable(LauncherVariables.CLIENT_ID, "-");

		settings.addDefaultVariable(LauncherVariables.AUTH_SESSION, "-");
		settings.addDefaultVariable(LauncherVariables.USER_PROPERTIES, "{}");
		settings.addDefaultVariable(LauncherVariables.USER_PROPERTY_MAP, "{}");

		// Add variables for version
		settings.addDefaultVariable(LauncherVariables.VERSION_NAME, client.getId());
		settings.addDefaultVariable(LauncherVariables.VERSION_TYPE, client.getType());

		settings.addDefaultVariable(LauncherVariables.GAME_DIRECTORY, settings.getRunDirectory());

		settings.addDefaultVariable(LauncherVariables.GAME_ASSETS, generatedSettings.getVirtualAssets() != null ? generatedSettings.getVirtualAssets() : new File(settings.getAssetsDirectory(), "dummy"));

		settings.addDefaultVariable(LauncherVariables.ASSET_ROOT, settings.getAssetsDirectory());
		settings.addDefaultVariable(LauncherVariables.ASSET_INDEX_NAME, client.getAssetIndex().getId());

		settings.addDefaultVariable(LauncherVariables.LAUNCHER_NAME, Constants.NAME);
		settings.addDefaultVariable(LauncherVariables.LAUNCHER_VERSION, Constants.VERSION);

		settings.addDefaultVariable(LauncherVariables.NATIVE_DIRECTORY, settings.getNativesDirectory());
		settings.addDefaultVariable(LauncherVariables.CLASSPATH, generatedSettings.getClassPath().stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)));

		settings.addDefaultVariable(LauncherVariables.PRIMARY_JAR, settings.getClientJarFile());

		// Add library directory for mod loaders
		settings.addDefaultVariable(LauncherVariables.LIBRARY_DIRECTORY, settings.getLibrariesDirectory());
	}
}
