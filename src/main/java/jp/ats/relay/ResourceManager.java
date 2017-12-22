package jp.ats.relay;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public interface ResourceManager {

	InputStream load(String resourceName) throws IOException;

	Path home();

	default Path resolvePath(String pathString) {
		Objects.requireNonNull(pathString);
		return resolvePath(Paths.get(pathString));
	}

	default Path resolvePath(Path path) {
		Objects.requireNonNull(path);
		if (!path.isAbsolute()) path = home().resolve(path);
		return path;
	}
}
