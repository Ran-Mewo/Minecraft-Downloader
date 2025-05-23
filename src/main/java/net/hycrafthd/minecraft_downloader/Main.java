package net.hycrafthd.minecraft_downloader;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.hycrafthd.minecraft_downloader.mod_loader.ModLoader;
import net.hycrafthd.minecraft_downloader.mod_loader.ModLoaderFactory;
import net.hycrafthd.minecraft_downloader.mod_loader.ModLoaderType;
import net.hycrafthd.minecraft_downloader.settings.LauncherFeatures;
import net.hycrafthd.minecraft_downloader.settings.LauncherVariables;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.hycrafthd.minecraft_downloader.util.FileUtil;

public class Main {

	public static final Logger LOGGER = LogManager.getLogger("Minecraft Downloader");

	public static void main(String[] args) throws IOException {
		final OptionParser parser = new OptionParser();

		// Default specs
		final OptionSpec<Void> helpSpec = parser.accepts("help", "Show the help menu").forHelp();
		final OptionSpec<String> versionSpec = parser.accepts("version", "Minecraft version to download").withRequiredArg();
		final OptionSpec<File> outputSpec = parser.accepts("output", "Output directory for the downloaded files").withRequiredArg().ofType(File.class);

		// Launch specs
		final OptionSpec<Void> launchSpec = parser.accepts("launch", "Launch minecraft after downloading the files");
		final OptionSpec<File> runSpec = parser.accepts("run", "Run directory for the game").availableIf(launchSpec).requiredIf(launchSpec).withRequiredArg().ofType(File.class);

		final OptionSpec<Void> defaultJavaSpec = parser.accepts("default-java-exec", "Download and use the vanilla supplied java runtime for that version. If not specified the current java runtime will be used for launching minecraft").availableIf(launchSpec);
		final OptionSpec<File> javaExecSpec = parser.accepts("java-exec", "Which java executable should be used to launch minecraft").availableIf(launchSpec).availableUnless(defaultJavaSpec).withRequiredArg().ofType(File.class);

		final OptionSpec<Void> skipClasspathShorteningSpec = parser.accepts("skip-classpath-shortening", "Skip classpath shortening").availableIf(launchSpec);

		final OptionSpec<Void> defaultLogSpec = parser.accepts("default-log-config", "Use vanilla supplied log4j configuration").availableIf(launchSpec);
		final OptionSpec<File> logFileSpec = parser.accepts("log-config", "Use the specified file as log4j configuration").availableIf(launchSpec).availableUnless(defaultLogSpec).withRequiredArg().ofType(File.class);

		final OptionSpec<Void> demoSpec = parser.accepts("demo", "Start the demo mode").availableIf(launchSpec);

		final OptionSpec<Integer> widthSpec = parser.accepts("width", "Width of the window").availableIf(launchSpec).withRequiredArg().ofType(Integer.class);
		final OptionSpec<Integer> heightSpec = parser.accepts("height", "Height of the window").availableIf(launchSpec).withRequiredArg().ofType(Integer.class);

		final OptionSpec<String> standardJvmArgumentsSpec = parser.accepts("standard-jvm-args", "Standard jvm arguments for launching minecraft").availableIf(launchSpec).withRequiredArg().defaultsTo(Constants.STANDARD_JVM_ARGS);

		// Login specs
		final OptionSpec<File> authFileSpec = parser.accepts("auth-file", "Authentication file for reading, writing and updating authentication data. If file does not exist, or is not usable, then the user will be prompted to login with the selected authentication method").withRequiredArg().ofType(File.class);
		final OptionSpec<String> authMethodSpec = parser.accepts("auth-method", "Authentication method that should be used when file does not exists. Currently 'web' and 'console' is supported").availableIf(authFileSpec).withRequiredArg().defaultsTo("console");
		final OptionSpec<Void> headlessAuthSpec = parser.accepts("headless-auth", "Force headless authentication").availableIf(authFileSpec, authMethodSpec);

		// Mod loader specs
		final OptionSpec<String> modLoaderTypeSpec = parser.accepts("mod-loader", "Mod loader type to use (fabric, forge, neoforge)").availableIf(launchSpec).withRequiredArg();
		final OptionSpec<String> modLoaderVersionSpec = parser.accepts("mod-loader-version", "Mod loader version to use (default: latest)").availableIf(modLoaderTypeSpec).withRequiredArg();

		// Special specs
		final OptionSpec<Void> skipNativesSpec = parser.accepts("skip-natives", "Skip extracting natives").availableUnless(launchSpec);
		final OptionSpec<Void> skipAssetsSpec = parser.accepts("skip-assets", "Skip the assets downloader").availableUnless(launchSpec);

		// Information specs
		final OptionSpec<Void> informationSpec = parser.accepts("extra-information", "Should extra information be extracted");
		final OptionSpec<File> userDataSpec = parser.accepts("user-data", "Create a file with the user login information").availableIf(informationSpec).availableIf(authFileSpec).withRequiredArg().ofType(File.class);
		final OptionSpec<File> libraryListSpec = parser.accepts("library-list", "Create a library list file with all library excluding natives").availableIf(informationSpec).withRequiredArg().ofType(File.class);
		final OptionSpec<File> libraryListNativesSpec = parser.accepts("library-list-natives", "Create a library list file with only native libraries").availableIf(informationSpec).withRequiredArg().ofType(File.class);

		final OptionSet set = parser.parse(args);

		if (set.has(helpSpec) || set.specs().size() < 2) {
			try (final OutputStream outputStream = IoBuilder.forLogger(LOGGER).setLevel(Level.ERROR).buildOutputStream()) {
				parser.printHelpOn(outputStream);
			} catch (final IOException ex) {
				LOGGER.error("Cannot print help on console", ex);
			}
			return;
		}

		LOGGER.info("Starting Minecraft Downloader");

		// Get arguments
		final String version = set.valueOf(versionSpec);
		final File output = set.valueOf(outputSpec);

		final boolean launch = set.has(launchSpec);
		final File run = set.valueOf(runSpec);

		final boolean defaultJava = set.has(defaultJavaSpec);
		final File javaExec = set.valueOf(javaExecSpec);

		final boolean skipClasspathShortening = set.has(skipClasspathShorteningSpec);

		final boolean defaultLog = set.has(defaultLogSpec);
		final File logFile = set.valueOf(logFileSpec);

		final boolean demo = set.has(demoSpec);

		final boolean customResolution = set.has(widthSpec) && set.has(heightSpec);
		final Integer width = set.valueOf(widthSpec);
		final Integer height = set.valueOf(heightSpec);

		final String standardJvmArguments = set.valueOf(standardJvmArgumentsSpec);

		final File authFile = set.valueOf(authFileSpec);
		final String authMethod = set.valueOf(authMethodSpec);
		final boolean headlessAuth = set.has(headlessAuthSpec);

		// Mod loader arguments
		final String modLoaderTypeStr = set.valueOf(modLoaderTypeSpec);
		final ModLoaderType modLoaderType = ModLoaderType.fromString(modLoaderTypeStr);
		final String modLoaderVersion = set.valueOf(modLoaderVersionSpec);

		final boolean skipNatives = set.has(skipNativesSpec);
		final boolean skipAssets = set.has(skipAssetsSpec);

		final boolean information = set.has(informationSpec);
		final File userData = set.valueOf(userDataSpec);
		final File libraryList = set.valueOf(libraryListSpec);
		final File libraryListNatives = set.valueOf(libraryListNativesSpec);

		// Create output folder
		if (FileUtil.createFolders(output)) {
			LOGGER.debug("Created output folder " + output.getAbsolutePath());
		}

		// Create provided settings
		final ProvidedSettings settings = new ProvidedSettings(version, output, run);

		MinecraftParser.launch(settings);
		MinecraftDownloader.launch(settings, defaultLog, logFile, skipNatives, skipAssets);

		if ((launch || userData != null) && authFile != null) {
			MinecraftAuthenticator.launch(settings, authFile, authMethod, headlessAuth);
		}

		if (information) {
			MinecraftInformation.launch(settings, userData, libraryList, libraryListNatives);
		}

		if (launch) {
			if (demo) {
				settings.addFeature(LauncherFeatures.DEMO_USER);
			}

			if (settings.getVariable(LauncherVariables.AUTH_ACCESS_TOKEN) == null) {
				LOGGER.info("User authentication was not found. Set game into demo mode");
				settings.addFeature(LauncherFeatures.DEMO_USER);
			}

			if (customResolution) {
				settings.addFeature(LauncherFeatures.HAS_CUSTOM_RESOLUTION);
				settings.addVariable(LauncherVariables.RESOLUTION_WIDTH, width.toString());
				settings.addVariable(LauncherVariables.RESOLUTION_HEIGHT, height.toString());
			}

			// Setup Java runtime first
			MinecraftJavaRuntimeSetup.launch(settings, defaultJava, javaExec);

			// Setup mod loader if specified
			if (modLoaderType != null) {
				LOGGER.info("Setting up mod loader: {}", modLoaderType.getName());
				settings.addFeature(LauncherFeatures.USE_MOD_LOADER);

				// Create mod loader instance
				ModLoader modLoader = ModLoaderFactory.createModLoader(modLoaderType, version, modLoaderVersion);

				// Setup mod loader
				MinecraftModLoader.launch(settings, modLoader);
			}

			// Build classpath and launch
			MinecraftClasspathBuilder.launch(settings, skipClasspathShortening);
			MinecraftLauncher.launch(settings, standardJvmArguments);
		}
	}
}
