package jp.ats.relay;

import jp.ats.relay.ShellClient;

public class TestShellClient implements ShellClient {

	@Override
	public void execute() {}

	@Override
	public boolean usesDatabase() {
		return false;
	}
}
