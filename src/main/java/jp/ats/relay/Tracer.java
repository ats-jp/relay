package jp.ats.relay;

import org.apache.logging.log4j.Logger;

public class Tracer {

	private final Logger logger;

	private final long start = System.nanoTime();

	private long now = start;

	public Tracer(Logger logger) {
		this.logger = logger;
	}

	public void trace(String message) {
		long now = System.nanoTime();
		long step = (now - this.now) / 1000000;
		logger.trace(message + " :" + step);
		this.now = now;
	}

	public void traceTotal(String message) {
		long now = System.nanoTime();
		long erapse = (now - start) / 1000000;
		logger.trace(message + " total:" + erapse);
		this.now = now;
	}
}
