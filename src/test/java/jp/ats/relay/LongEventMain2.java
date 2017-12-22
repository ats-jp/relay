package jp.ats.relay;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.lmax.disruptor.IgnoreExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;

public class LongEventMain2 {

	public static void main(String[] args) throws Exception {
		long start = System.nanoTime();
		RingBuffer<LongEvent> ringBuffer = RingBuffer.createSingleProducer(LongEvent::new, 1024);

		ExecutorService executor = Executors.newCachedThreadPool(
			r -> new Thread(() -> {
				try {
					Thread.sleep(10);
				} catch (Exception e) {}
				System.out.println("worker start " + r + " " + Thread.currentThread());
				r.run();
				System.out.println("worker end " + r + " " + Thread.currentThread());
			}));

		WorkHandler<LongEvent> wh = event -> {
			System.out.println("Event: " + event.value + ", thread: " + Thread.currentThread());
			Thread.sleep(10);
		};

		@SuppressWarnings("unchecked")
		WorkerPool<LongEvent> workerPool = new WorkerPool<LongEvent>(ringBuffer, ringBuffer.newBarrier(), new IgnoreExceptionHandler(), wh, wh, wh, wh, wh, wh);

		ringBuffer.addGatingSequences(workerPool.getWorkerSequences());

		workerPool.start(executor);

		System.out.println("publish start");
		for (long l = 0; l < 100; l++) {
			long seq = ringBuffer.next();
			ringBuffer.get(seq).set(l);
			ringBuffer.publish(seq);
			//Thread.sleep(100);
		}

		System.out.println("publish end");
		workerPool.drainAndHalt();
		executor.shutdown();

		System.out.println("nano: " + (System.nanoTime() - start));
	}
}
