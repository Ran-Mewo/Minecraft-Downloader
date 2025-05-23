package net.hycrafthd.minecraft_downloader.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.hycrafthd.minecraft_downloader.library.DownloadableFile;
import net.hycrafthd.minecraft_downloader.mojang_api.CurrentClientJson;

public class GeneratedSettings {

	private CurrentClientJson clientJson;

	private List<DownloadableFile> downloadableFiles;

	private File virtualAssets;

	private File logFile;

	private File javaExec;

	private Set<File> classPath;

	private List<File> modLoaderFiles = new ArrayList<>();

	public void setClientJson(CurrentClientJson clientJson) {
		if (this.clientJson != null) {
			throw new IllegalStateException("Client json was already set");
		}
		this.clientJson = clientJson;
	}

	public CurrentClientJson getClientJson() {
		if (clientJson == null) {
			throw new IllegalStateException("Client json is not set");
		}
		return clientJson;
	}

	public void setDownloadableFiles(List<DownloadableFile> downloadableFiles) {
		if (this.downloadableFiles != null) {
			throw new IllegalStateException("Downloadable files list was already set");
		}
		this.downloadableFiles = downloadableFiles;
	}

	public List<DownloadableFile> getDownloadableFiles() {
		if (downloadableFiles == null) {
			throw new IllegalStateException("Downloadable files list is not set");
		}
		return downloadableFiles;
	}

	public void setVirtualAssets(File virtualAssets) {
		if (this.virtualAssets != null) {
			throw new IllegalStateException("Virtual assets was already set");
		}
		this.virtualAssets = virtualAssets;
	}

	public File getVirtualAssets() {
		return virtualAssets;
	}

	public void setLogFile(File logFile) {
		if (this.logFile != null) {
			throw new IllegalStateException("Logfile was already set");
		}
		this.logFile = logFile;
	}

	public File getLogFile() {
		return logFile;
	}

	public void setJavaExec(File javaExec) {
		if (this.javaExec != null) {
			throw new IllegalStateException("Java exec was already set");
		}
		this.javaExec = javaExec;
	}

	public File getJavaExec() {
		if (javaExec == null) {
			throw new IllegalStateException("Java exec is not set");
		}
		return javaExec;
	}

	public void setClassPath(Set<File> classPath) {
		if (this.classPath != null) {
			throw new IllegalStateException("Classpath was already set");
		}
		this.classPath = classPath;
	}

	public Set<File> getClassPath() {
		if (classPath == null) {
			throw new IllegalStateException("Classpath is not set");
		}
		return Collections.unmodifiableSet(classPath);
	}

	public void setModLoaderFiles(List<File> modLoaderFiles) {
		this.modLoaderFiles.clear();
		if (modLoaderFiles != null) {
			this.modLoaderFiles.addAll(modLoaderFiles);
		}
	}

	public List<File> getModLoaderFiles() {
		return Collections.unmodifiableList(modLoaderFiles);
	}
}
