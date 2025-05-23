package net.hycrafthd.minecraft_downloader.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.hycrafthd.minecraft_downloader.Main;
import net.hycrafthd.minecraft_downloader.mod_loader.ModLoader;
import net.hycrafthd.minecraft_downloader.mod_loader.ModLoaderFactory;
import net.hycrafthd.minecraft_downloader.mod_loader.ModLoaderType;
import net.hycrafthd.minecraft_downloader.mojang_api.CurrentClientJson;
import net.hycrafthd.minecraft_downloader.mojang_api.CurrentClientJson.ArgumentsJson;
import net.hycrafthd.minecraft_downloader.mojang_api.CurrentClientJson.ArgumentsJson.ConditionalGameArgumentJson;
import net.hycrafthd.minecraft_downloader.mojang_api.CurrentClientJson.ArgumentsJson.ConditionalJvmArgumentJson;
import net.hycrafthd.minecraft_downloader.mojang_api.CurrentClientJson.BaseOsRuleJson.OSJson;
import net.hycrafthd.minecraft_downloader.settings.GeneratedSettings;
import net.hycrafthd.minecraft_downloader.settings.LauncherFeatures;
import net.hycrafthd.minecraft_downloader.settings.LauncherVariables;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;
import net.hycrafthd.minecraft_downloader.util.OSUtil;
import net.hycrafthd.minecraft_downloader.util.StringUtil;

public class ArgumentsParser {

	private final List<String> gameArgs;
	private final List<String> jvmArgs;

	public ArgumentsParser(ProvidedSettings settings, String standardJvmArgs) {
		this.gameArgs = new ArrayList<>();
		this.jvmArgs = new ArrayList<>();

		final GeneratedSettings generatedSettings = settings.getGeneratedSettings();
		final CurrentClientJson clientJson = generatedSettings.getClientJson();

		final ArgumentsJson argumentsJson = clientJson.getArguments();
		final String minecraftArguments = clientJson.getMinecraftArguments();

		if (argumentsJson != null) {
			buildArguments(settings, argumentsJson);
		} else if (minecraftArguments != null) {
			buildLegacyArguments(settings, minecraftArguments);
		} else {
			throw new IllegalStateException("Client json does not contains arguments on how to launch the game");
		}

		// Add standard jvm args
		Stream.of(standardJvmArgs.split(" ")).forEach(jvmArgs::add);

		// Add log4j config file
		if (generatedSettings.getLogFile() != null && clientJson.getLogging() != null) {
			jvmArgs.add(StringUtil.replaceVariable("path", clientJson.getLogging().getClient().getArgument(), generatedSettings.getLogFile().getAbsolutePath()));
		}

		// Add mod loader arguments if needed
		if (settings.hasFeature(LauncherFeatures.USE_MOD_LOADER)) {
			Main.LOGGER.info("Adding mod loader arguments");
			addModLoaderArguments(settings);
		}
	}

	private final Stream<String> replaceVariables(Stream<String> arguments, ProvidedSettings settings) {
		return arguments.map(argument -> settings.replaceVariable(argument));
	}

	private final Stream<String> conditionalGameArg(Stream<ConditionalGameArgumentJson> arguments, ProvidedSettings settings) {
		return arguments.filter(argument -> {
			return argument.getRules().stream().allMatch(rule -> {
				final boolean value = rule.getAction().equals("allow");
				if (rule.getFeatures().isIsDemoUser() && settings.hasFeature(LauncherFeatures.DEMO_USER)) {
					return value;
				}
				if (rule.getFeatures().isHasCustomResolution() && settings.hasFeature(LauncherFeatures.HAS_CUSTOM_RESOLUTION)) {
					return value;
				}
				return !value;
			});
		}).flatMap(argument -> argument.getValue().getValue().stream());
	}

	private final Stream<String> conditionalJvmArg(Stream<ConditionalJvmArgumentJson> arguments, ProvidedSettings settings) {
		return arguments.filter(argument -> {
			return argument.getRules().stream().allMatch(rule -> {
				final boolean value = rule.getAction().equals("allow");

				final OSJson os = rule.getOs();

				final String name = os.getName();
				final String version = os.getVersion();
				final String arch = os.getArch();

				boolean returnValue = !value;

				if (name != null) {
					if (name.equals(OSUtil.CURRENT_OS.getName())) {
						returnValue = value;
					} else {
						returnValue = !value;
					}
				}
				if (version != null) {
					if (Pattern.compile(version).matcher(OSUtil.CURRENT_VERSION).find()) {
						returnValue = value;
					} else {
						returnValue = !value;
					}
				}
				if (arch != null) {
					if (Pattern.compile(arch).matcher(OSUtil.CURRENT_ARCH.getName()).find()) {
						returnValue = value;
					} else {
						returnValue = !value;
					}
				}

				return returnValue;
			});
		}).flatMap(argument -> argument.getValue().getValue().stream());
	}

	private void buildArguments(ProvidedSettings settings, ArgumentsJson argumentsJson) {
		// Add game args
		replaceVariables(Stream.concat(argumentsJson.getGameArguments().stream(), conditionalGameArg(argumentsJson.getConditionalGameArguments().stream(), settings)), settings).forEach(gameArgs::add);

		// Add jvm args
		replaceVariables(Stream.concat(conditionalJvmArg(argumentsJson.getConditionalJvmArguments().stream(), settings), argumentsJson.getJvmArguments().stream()), settings).forEach(jvmArgs::add);
	}

	private void buildLegacyArguments(ProvidedSettings settings, String minecraftArguments) {
		// Add game args
		final List<String> preGameArgs = new ArrayList<String>();

		Stream.of(minecraftArguments.split(" ")).forEach(preGameArgs::add);
		if (settings.hasFeature(LauncherFeatures.DEMO_USER)) {
			gameArgs.add("--demo");
		}
		if (settings.hasFeature(LauncherFeatures.HAS_CUSTOM_RESOLUTION)) {
			gameArgs.add("--width");
			gameArgs.add("${resolution_width}");
			gameArgs.add("${height}");
			gameArgs.add("${resolution_height}");
		}

		replaceVariables(preGameArgs.stream(), settings).forEach(gameArgs::add);

		// Add jvm args
		final ArrayList<String> preJvmArgs = new ArrayList<>();

		preJvmArgs.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
		preJvmArgs.add("-Dos.name=Windows 10");
		preJvmArgs.add("-Dos.version=10.0");
		preJvmArgs.add("-Djava.library.path=${natives_directory}");
		preJvmArgs.add("-Dminecraft.launcher.brand=${launcher_name}");
		preJvmArgs.add("-Dminecraft.launcher.version=${launcher_version}");
		preJvmArgs.add("-Dminecraft.client.jar=${primary_jar}");
		preJvmArgs.add("-cp");
		preJvmArgs.add("${classpath}");

		replaceVariables(preJvmArgs.stream(), settings).forEach(jvmArgs::add);
	}

	public List<String> getGameArgs() {
		return gameArgs;
	}

	public List<String> getJvmArgs() {
		return jvmArgs;
	}

	private void addModLoaderArguments(ProvidedSettings settings) {
		// Get mod loader type and version
		String modLoaderType = settings.getVariable(LauncherVariables.MOD_LOADER_TYPE);
		String modLoaderVersion = settings.getVariable(LauncherVariables.MOD_LOADER_VERSION);
		String minecraftVersion = settings.getVersion();

		// Create mod loader instance
		ModLoaderType type = ModLoaderType.fromString(modLoaderType);
		if (type == null) {
			throw new IllegalStateException("Unknown mod loader type: " + modLoaderType);
		}

		ModLoader modLoader = ModLoaderFactory.createModLoader(type, minecraftVersion, modLoaderVersion);
		Main.LOGGER.info("Creating {} mod loader for Minecraft {}, loader version: {}",
				type.getName(), minecraftVersion, modLoaderVersion);

		// Add mod loader JVM arguments
		List<String> modLoaderJvmArgs = modLoader.getAdditionalJvmArguments();
		if (!modLoaderJvmArgs.isEmpty()) {
			Main.LOGGER.info("Adding mod loader JVM arguments: {}", String.join(" ", modLoaderJvmArgs));

			// Replace variables in the JVM arguments
			List<String> processedArgs = new ArrayList<>();
			for (String arg : modLoaderJvmArgs) {
				// Special handling for module path arguments
				if (arg.startsWith("--add-opens=") || arg.startsWith("--add-exports=") ||
					arg.startsWith("--add-reads=") || arg.startsWith("--add-modules=")) {
					// These arguments should be passed as is, with variable replacement
					processedArgs.add(settings.replaceVariable(arg));
				} else if (arg.startsWith("--add-opens ") || arg.startsWith("--add-exports ") ||
					arg.startsWith("--add-reads ") || arg.startsWith("--add-modules ")) {
					// Convert space-separated format to equals format for better compatibility
					String[] parts = arg.split(" ", 2);
					if (parts.length == 2) {
						String newArg = parts[0] + "=" + parts[1];
						processedArgs.add(settings.replaceVariable(newArg));
					} else {
						processedArgs.add(settings.replaceVariable(arg));
					}
				} else {
					processedArgs.add(settings.replaceVariable(arg));
				}
			}

			jvmArgs.addAll(processedArgs);
		}

		// Add mod loader game arguments
		List<String> modLoaderGameArgs = modLoader.getAdditionalGameArguments();
		if (!modLoaderGameArgs.isEmpty()) {
			Main.LOGGER.info("Adding mod loader game arguments: {}", String.join(" ", modLoaderGameArgs));

			// Replace variables in the game arguments
			List<String> processedArgs = new ArrayList<>();
			for (String arg : modLoaderGameArgs) {
				processedArgs.add(settings.replaceVariable(arg));
			}

			gameArgs.addAll(processedArgs);
		}
	}

}
