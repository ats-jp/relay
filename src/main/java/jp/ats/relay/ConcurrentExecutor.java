package jp.ats.relay;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;
import com.lmax.disruptor.util.Util;

/**
 * 複数のworkerスレッドで、複数の処理対象を捌く
 * @param <T> 処理対象の型
 */
public class ConcurrentExecutor<T> {

	/**
	 * リングバッファのサイズ（2^nであること）
	 */
	private static final int BUFFER_SIZE = 1024;

	/**
	 * mainがはけるのをwaitするときの待ちミリ秒
	 */
	private static final int WAIT_MILLIS = 500;

	private final RingBuffer<Event<T>> ringBuffer;

	private final ExecutorService executor;

	private final WorkerPool<Event<T>> workerPool;

	private final Object parking;

	/**
	 * @param concurrency 処理worker数
	 * @param consumer workerが行う処理
	 * @param threadFactory 任意のThread生成
	 * @param disposer 例外処理
	 * @return ConcurrentExecutor
	 */
	public static <T> ConcurrentExecutor<T> getInstance(
		int concurrency,
		Consumer<T> consumer,
		ThreadFactory threadFactory,
		Disposer<T> disposer) {
		Object parking = new Object();
		WorkHandler<Event<T>> workHandler = toWorker(consumer, parking);
		List<WorkHandler<Event<T>>> workers = IntStream.range(0, concurrency).mapToObj(i -> workHandler).collect(Collectors.toList());
		return new ConcurrentExecutor<>(threadFactory, disposer, workers, parking);
	}

	/**
	 * @param threadFactory 任意のThread生成
	 * @param disposer 例外処理
	 * @param consumers workerが行う処理
	 * @return ConcurrentExecutor
	 */
	@SafeVarargs
	public static <T> ConcurrentExecutor<T> getInstance(
		ThreadFactory threadFactory,
		Disposer<T> disposer,
		Consumer<T>... consumers) {
		Object parking = new Object();
		List<WorkHandler<Event<T>>> workers = Arrays.asList(consumers).stream().map(consumer -> toWorker(consumer, parking)).collect(Collectors.toList());
		return new ConcurrentExecutor<>(threadFactory, disposer, workers, parking);
	}

	private static <T> WorkHandler<Event<T>> toWorker(Consumer<T> consumer, Object parking) {
		return event -> {
			try {
				consumer.accept(event.value);
			} finally {
				synchronized (parking) {
					parking.notify();
				}
			}
		};
	}

	private ConcurrentExecutor(
		ThreadFactory threadFactory,
		Disposer<T> disposer,
		List<WorkHandler<Event<T>>> workers,
		Object parking) {
		this.parking = parking;
		ringBuffer = RingBuffer.createSingleProducer(Event::new, BUFFER_SIZE);
		executor = Executors.newCachedThreadPool(threadFactory);

		@SuppressWarnings("unchecked")
		WorkHandler<Event<T>>[] workHandlerArray = workers.toArray(new WorkHandler[workers.size()]);

		ExceptionHandler<Event<T>> exceptionHandler = new ExceptionHandler<Event<T>>() {

			@Override
			public void handleEventException(Throwable t, long sequence, Event<T> event) {
				disposer.onEvent(t, sequence, event.value);
			}

			@Override
			public void handleOnStartException(Throwable t) {
				disposer.onStart(t);
			}

			@Override
			public void handleOnShutdownException(Throwable t) {
				disposer.onShutdown(t);
			}
		};

		workerPool = new WorkerPool<Event<T>>(ringBuffer, ringBuffer.newBarrier(), exceptionHandler, workHandlerArray);

		ringBuffer.addGatingSequences(workerPool.getWorkerSequences());
	}

	/**
	 * workerを開始する
	 */
	public void start() {
		workerPool.start(executor);
	}

	/**
	 * 処理対象をworkerに処理させる
	 * @param values 処理対象
	 */
	public void execute(List<T> values) {
		execute(values.stream());
	}

	/**
	 * 処理対象をworkerに処理させる
	 * @param values 処理対象
	 */
	public void execute(Stream<T> values) {
		values.forEach(value -> {
			long seq = ringBuffer.next();
			ringBuffer.get(seq).set(value);
			ringBuffer.publish(seq);
		});
	}

	/**
	 * 処理対象をworkerに処理させる<br>
	 * chunkで指定した数を処理し終わるとintervalを実行する
	 * @param values 処理対象
	 * @param chunk 処理の一塊の数
	 * @param interval インターバル処理
	 * @throws InterruptedException
	 */
	public void execute(Stream<T> values, int chunk, Runnable interval) throws InterruptedException {
		int[] counter = { 0 };
		try {
			values.forEach(value -> {
				long seq = ringBuffer.next();
				ringBuffer.get(seq).set(value);
				ringBuffer.publish(seq);

				if (++counter[0] % chunk == 0) {
					try {
						waitUntilDrained();
					} catch (InterruptedException e) {
						throw new Interrupted(e);
					}

					interval.run();
				}
			});
		} catch (Interrupted e) {
			throw e.original;
		}
	}

	/**
	 * 処理対象が全てはけるまでmainスレッドを停止する
	 * @throws InterruptedException
	 */
	public void waitUntilDrained() throws InterruptedException {
		Sequence[] workerSequences = workerPool.getWorkerSequences();
		while (ringBuffer.getCursor() > Util.getMinimumSequence(workerSequences)) {
			synchronized (parking) {
				//メインがここに到達する前にWorkerがすべて処理したときの場合に備えてtimeoutを設定
				parking.wait(WAIT_MILLIS);
			}
		}
	}

	/**
	 * 処理対象が全てはけるまでmainスレッドを待たせ、workerスレッドを停止させる
	 */
	public void shutdown() {
		workerPool.drainAndHalt();
		executor.shutdown();
	}

	/**
	 * worker実行時に発生した例外を処理する
	 *
	 * @param <T> 処理対象の型
	 */
	public interface Disposer<T> {

		void onEvent(Throwable t, long sequence, T value);

		void onStart(Throwable t);

		void onShutdown(Throwable t);
	}

	@SuppressWarnings("serial")
	private static class Interrupted extends RuntimeException {

		private final InterruptedException original;

		private Interrupted(InterruptedException original) {
			this.original = original;
		}
	}

	private static class Event<T> {

		private T value;

		private void set(T value) {
			this.value = value;
		}
	}
}
