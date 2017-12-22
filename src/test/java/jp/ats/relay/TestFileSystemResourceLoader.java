package jp.ats.relay;

import java.nio.file.Paths;

import jp.ats.relay.FileSystemResourceManager;

public class TestFileSystemResourceLoader extends FileSystemResourceManager {

	public TestFileSystemResourceLoader() {
		super(Paths.get("path-to-relay-home"));
	}
}
