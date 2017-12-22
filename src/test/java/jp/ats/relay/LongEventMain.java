package jp.ats.relay;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadFactory;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

public class LongEventMain {

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		// Specify the size of the ring buffer, must be power of 2.
		int bufferSize = 8;

		ThreadFactory tf = r -> new Thread(r, "name");

		// Construct the Disruptor
		Disruptor<LongEvent> disruptor = new Disruptor<>(LongEvent::new, bufferSize, tf);

		// Connect the handler
		disruptor.handleEventsWith(
			(event, sequence, endOfBatch) -> {
				System.out.println("Event: " + event.value + ", seq: " + sequence);
				Thread.sleep(100);
			},
			(event, sequence, endOfBatch) -> {
				System.out.println("Event: " + event.value + ", seq: " + sequence);
				Thread.sleep(100);
			});

		// Start the Disruptor, starts all threads running
		disruptor.start();

		// Get the ring buffer from the Disruptor to be used for publishing.
		RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

		ByteBuffer bb = ByteBuffer.allocate(8);
		for (long l = 0; true; l++) {
			System.out.println("######: " + l);
			bb.putLong(0, l);
			ringBuffer.publishEvent((event, sequence, buffer) -> event.set(buffer.getLong(0)), bb);
		}
	}
}
