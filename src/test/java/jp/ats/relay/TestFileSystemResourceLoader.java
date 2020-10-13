package jp.ats.relay;

import java.nio.file.Paths;

public class TestFileSystemResourceLoader extends FileSystemResourceManager {

	public TestFileSystemResourceLoader() {
		super(Paths.get("path-to-relay-home"));
	}
}
