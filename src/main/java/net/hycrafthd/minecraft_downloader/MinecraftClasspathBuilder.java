package net.hycrafthd.minecraft_downloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import net.hycrafthd.minecraft_downloader.library.DownloadableFile;
import net.hycrafthd.minecraft_downloader.mod_loader.ModLoaderType;
import net.hycrafthd.minecraft_downloader.settings.GeneratedSettings;
import net.hycrafthd.minecraft_downloader.settings.ProvidedSettings;

public class MinecraftClasspathBuilder {

	// Pattern to extract artifact ID and version from a JAR file name
	private static final Pattern LIBRARY_PATTERN = Pattern.compile("([a-zA-Z0-9._-]+)-(\\d+(?:\\.\\d+)*(?:[^.]+)?)(?:-[^.]+)?\\.jar");

	public static void launch(ProvidedSettings settings, boolean skipClasspathShortening) {
		Main.LOGGER.info("Start the classpath builder");

		final GeneratedSettings generatedSettings = settings.getGeneratedSettings();

		// Get mod loader files
		List<File> modLoaderFiles = generatedSettings.getModLoaderFiles();

		// Log all mod loader files
		Main.LOGGER.debug("Mod loader files:");
		modLoaderFiles.forEach(file -> Main.LOGGER.debug(" - {}", file.getName()));

		// Get vanilla libraries
		List<File> vanillaLibraries = generatedSettings.getDownloadableFiles().stream()
				.filter(downloadableFile -> !downloadableFile.isNative())
				.filter(DownloadableFile::hasDownloadedFile)
				.map(DownloadableFile::getDownloadedFile)
				.collect(Collectors.toList());

		// Create a map to track libraries by their artifact ID
		Map<String, List<LibraryInfo>> libraryMap = new HashMap<>();

		// Process vanilla libraries
		for (File file : vanillaLibraries) {
			LibraryInfo info = parseLibraryFileName(file);
			if (info != null) {
				libraryMap.computeIfAbsent(info.artifactId, k -> new ArrayList<>()).add(info);
			}
		}

		// Process mod loader libraries
		for (File file : modLoaderFiles) {
			LibraryInfo info = parseLibraryFileName(file);
			if (info != null) {
				libraryMap.computeIfAbsent(info.artifactId, k -> new ArrayList<>()).add(info);
			}
		}

		// Find conflicting libraries (same artifact ID but different files)
		Set<String> conflictingArtifacts = new HashSet<>();
		for (Map.Entry<String, List<LibraryInfo>> entry : libraryMap.entrySet()) {
			String artifactId = entry.getKey();
			List<LibraryInfo> libraries = entry.getValue();

			if (libraries.size() > 1) {
				// Check if there are both vanilla and mod loader libraries
				boolean hasVanilla = libraries.stream().anyMatch(lib -> !lib.isModLoader);
				boolean hasModLoader = libraries.stream().anyMatch(lib -> lib.isModLoader);

				if (hasVanilla && hasModLoader) {
					conflictingArtifacts.add(artifactId);
					Main.LOGGER.info("Detected conflicting library: {}", artifactId);
					libraries.forEach(lib -> Main.LOGGER.debug(" - {} ({})", lib.file.getName(), lib.isModLoader ? "mod loader" : "vanilla"));
				}
			}
		}

		// Create the final classpath
		Set<File> classPath = new LinkedHashSet<>();

		// Start with the client jar
		File clientJarFile = settings.getClientJarFile();
		Main.LOGGER.info("Adding client jar to classpath: {}", clientJarFile.getAbsolutePath());
		classPath.add(clientJarFile);

		// Add non-conflicting vanilla libraries
		for (File file : vanillaLibraries) {
			LibraryInfo info = parseLibraryFileName(file);
			if (info != null && conflictingArtifacts.contains(info.artifactId)) {
				Main.LOGGER.debug("Excluding conflicting vanilla library: {}", file.getName());
			} else {
				Main.LOGGER.debug("Adding vanilla library to classpath: {}", file.getName());
				classPath.add(file);
			}
		}

		// Add all mod loader libraries
		for (File file : modLoaderFiles) {
			Main.LOGGER.debug("Adding mod loader library to classpath: {}", file.getName());
			classPath.add(file);
		}

		Main.LOGGER.debug("The classpath entries are: ");
		classPath.forEach(file -> {
			Main.LOGGER.debug(" " + file);
		});

		// For mod loaders, we need to use the full classpath to ensure all libraries are properly included
		if (!skipClasspathShortening && modLoaderFiles.isEmpty()) {
			generateShortClasspathJar(settings, classPath);
		} else {
			Main.LOGGER.info("Using full classpath for mod loader");
			generatedSettings.setClassPath(classPath);
		}

		Main.LOGGER.info("Finished the classpath builder");
	}

	private static void generateShortClasspathJar(ProvidedSettings settings, Set<File> classpath) {
		Main.LOGGER.info("Shortening classpath");

		final Manifest manifest = new Manifest();

		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classpath.stream().map(File::toURI).map(URI::toString).collect(Collectors.joining(" ")));

		try (final JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(settings.getClientClasspathJarFile()), manifest)) {
			outputStream.putNextEntry(new ZipEntry("META-INF/"));
		} catch (final IOException ex) {
			throw new IllegalStateException("Cannot create short class path jar", ex);
		}

		settings.getGeneratedSettings().setClassPath(Set.of(settings.getClientClasspathJarFile()));
	}

	private static LibraryInfo parseLibraryFileName(File file) {
		String fileName = file.getName();
		Matcher matcher = LIBRARY_PATTERN.matcher(fileName);
		if (matcher.matches()) {
			String artifactId = matcher.group(1);
			String version = matcher.group(2);

			// Check if this is a mod loader library based on its path
			boolean isModLoader = isModLoaderLibrary(file);

			return new LibraryInfo(file, artifactId, version, isModLoader);
		}
		return null;
	}

	private static boolean isModLoaderLibrary(File file) {
		// Get the file path
		String path = file.getPath();

		// Check if the file is in a mod loader directory
		// This is determined by checking if the path contains a mod loader directory
		// We use the output directory structure to determine this

		// Get the parent directory name
		File parentDir = file.getParentFile();
		while (parentDir != null) {
			String dirName = parentDir.getName().toLowerCase();

			// Check if this is a mod loader directory by comparing with all mod loader types
			for (ModLoaderType type : ModLoaderType.values()) {
				if (dirName.equals(type.name().toLowerCase())) {
					return true;
				}
			}

			// Move up to the parent directory
			parentDir = parentDir.getParentFile();
		}

		return false;
	}

	private static class LibraryInfo {
		final File file;
		final String artifactId;
		final String version;
		final boolean isModLoader;

		LibraryInfo(File file, String artifactId, String version, boolean isModLoader) {
			this.file = file;
			this.artifactId = artifactId;
			this.version = version;
			this.isModLoader = isModLoader;
		}
	}
}
