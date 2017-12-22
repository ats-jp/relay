package jp.ats.relay;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;

public class TestConcurrentExecutor {

	private static final int BUFFER_SIZE = 16;

	private final RingBuffer<PathEvent> ringBuffer;

	private final ExecutorService executor;

	private final WorkerPool<PathEvent> workerPool;

	public TestConcurrentExecutor(int concurrency, PathConsumer consumer) {
		ringBuffer = RingBuffer.createSingleProducer(PathEvent::new, BUFFER_SIZE);
		executor = Executors.newCachedThreadPool();

		WorkHandler<PathEvent> workHandler = event -> {
			consumer.consume(event.get());
		};

		List<WorkHandler<PathEvent>> workHandlers = IntStream.range(0, concurrency).mapToObj(i -> workHandler).collect(Collectors.toList());
		@SuppressWarnings("unchecked")
		WorkHandler<PathEvent>[] workHandlerArray = workHandlers.toArray(new WorkHandler[workHandlers.size()]);

		ExceptionHandler<PathEvent> exceptionHandler = new ExceptionHandler<PathEvent>() {

			@Override
			public void handleEventException(Throwable ex, long sequence, PathEvent event) {
				ex.printStackTrace();
			}

			@Override
			public void handleOnStartException(Throwable ex) {
				ex.printStackTrace();
			}

			@Override
			public void handleOnShutdownException(Throwable ex) {
				ex.printStackTrace();
			}
		};

		workerPool = new WorkerPool<PathEvent>(ringBuffer, ringBuffer.newBarrier(), exceptionHandler, workHandlerArray);

		ringBuffer.addGatingSequences(workerPool.getWorkerSequences());

		workerPool.start(executor);
	}

	public void execute(List<Path> paths) {
		paths.forEach(path -> {
			long seq = ringBuffer.next();
			ringBuffer.get(seq).set(path);
			ringBuffer.publish(seq);
		});
	}

	public void shutdown() {
		workerPool.drainAndHalt();
		executor.shutdown();
	}

	public interface PathConsumer {

		void consume(Path path);
	}

	private static class PathEvent {

		private Path path;

		private void set(Path path) {
			this.path = path;
		}

		private Path get() {
			return path;
		}
	}

}
