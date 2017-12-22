package jp.ats.relay;

import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NextCommandStub implements NextCommand {

	private static final Logger logger = LogManager.getLogger("console-logger");

	@Override
	public boolean canExecute(Path lockDir) {
		return true;
	}

	@Override
	public void execute(String command) {
		logger.info(NextCommandStub.class.getSimpleName() + "next command is [" + command + "]");
	}
}
