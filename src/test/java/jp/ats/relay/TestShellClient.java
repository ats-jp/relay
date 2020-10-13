package jp.ats.relay;

public class TestShellClient implements ShellClient {

	@Override
	public void execute() {}

	@Override
	public boolean usesDatabase() {
		return false;
	}
}
