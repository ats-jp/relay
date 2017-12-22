package jp.ats.relay;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemResourceManager implements ResourceManager {

	private final Path homePath;

	public FileSystemResourceManager(Path homePath) {
		this.homePath = homePath;
	}

	@Override
	public InputStream load(String resourceName) throws IOException {
		return new BufferedInputStream(
			Files.newInputStream(resolvePath(resourceName)));
	}

	@Override
	public Path home() {
		return homePath;
	}
}
