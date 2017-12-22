package jp.ats.relay;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandExecutorStub extends CommandExecutor {

	private static final Logger logger = LogManager.getLogger("console-logger");

	@Override
	@SuppressWarnings("unchecked")
	public List<String> execute(
		Consumer<ProcessBuilder> configFunction,
		InputStream in,
		Charset charset) {
		logger.info(CommandExecutorStub.class.getName() + " called.");
		return Collections.EMPTY_LIST;
	}
}
