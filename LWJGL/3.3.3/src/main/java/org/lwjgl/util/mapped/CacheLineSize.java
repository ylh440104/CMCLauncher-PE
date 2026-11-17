package org.lwjgl.util.mapped;

import static org.lwjgl.util.mapped.MappedHelper.ivget;
import static org.lwjgl.util.mapped.MappedHelper.ivput;

import org.lwjgl.LWJGLUtil;
import org.lwjgl.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CacheLineSize {

	private CacheLineSize() {
	}

	static int getCacheLineSize() {
		final int THREADS = 2;
		final int REPEATS = 100000 * THREADS;
		final int LOCAL_REPEATS = REPEATS / THREADS;

		final int MAX_SIZE = LWJGLUtil.getPrivilegedInteger("org.lwjgl.util.mapped.CacheLineMaxSize", 1024) / 4;

		final double TIME_THRESHOLD = 1.0 + LWJGLUtil.getPrivilegedInteger("org.lwjgl.util.mapped.CacheLineTimeThreshold", 50) / 100.0;

		final ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
		final ExecutorCompletionService<Long> completionService = new ExecutorCompletionService<Long>(executorService);

		try {

			final IntBuffer memory = getMemory(MAX_SIZE);

			final int WARMUP = 10;
			for ( int i = 0; i < WARMUP; i++ )
				doTest(THREADS, LOCAL_REPEATS, 0, memory, completionService);

			long totalTime = 0;
			int count = 0;
			int cacheLineSize = 64;
			boolean found = false;
			for ( int i = MAX_SIZE; i >= 1; i >>= 1 ) {
				final long time = doTest(THREADS, LOCAL_REPEATS, i, memory, completionService);
				if ( totalTime > 0 ) {
					final long avgTime = totalTime / count;
					if ( (double)time / (double)avgTime > TIME_THRESHOLD ) {
						cacheLineSize = (i << 1) * 4;
						found = true;
						break;
					}
				}
				totalTime += time;
				count++;
			}

			if ( LWJGLUtil.DEBUG ) {
				if ( found )
					LWJGLUtil.log("Cache line size detected: " + cacheLineSize + " bytes");
				else
					LWJGLUtil.log("Failed to detect cache line size, assuming " + cacheLineSize + " bytes");
			}

			return cacheLineSize;
		} finally {
			executorService.shutdown();
		}
	}

	public static void main(String[] args) {
		CacheUtil.getCacheLineSize();
	}

	static long memoryLoop(final int index, final int repeats, final IntBuffer memory, final int padding) {
		final long address = MemoryUtil.getAddress(memory) + (index * padding * 4);

		final long time = System.nanoTime();
		for ( int i = 0; i < repeats; i++ ) {

			ivput(ivget(address) + 1, address);
		}

		return System.nanoTime() - time;
	}

	private static IntBuffer getMemory(final int START_SIZE) {
		final int PAGE_SIZE = MappedObjectUnsafe.INSTANCE.pageSize();

		final ByteBuffer buffer = ByteBuffer.allocateDirect((START_SIZE * 4) + PAGE_SIZE).order(ByteOrder.nativeOrder());

		if ( MemoryUtil.getAddress(buffer) % PAGE_SIZE != 0 ) {

			buffer.position(PAGE_SIZE - (int)(MemoryUtil.getAddress(buffer) & (PAGE_SIZE - 1)));
		}

		return buffer.asIntBuffer();
	}

	private static long doTest(final int threads, final int repeats, final int padding, final IntBuffer memory, final ExecutorCompletionService<Long> completionService) {
		for ( int i = 0; i < threads; i++ )
			submitTest(completionService, i, repeats, memory, padding);
		return waitForResults(threads, completionService);
	}

	private static void submitTest(final ExecutorCompletionService<Long> completionService, final int index, final int repeats, final IntBuffer memory, final int padding) {
		completionService.submit(new Callable<Long>() {
			public Long call() throws Exception {
				return memoryLoop(index, repeats, memory, padding);
			}
		});
	}

	private static long waitForResults(final int count, final ExecutorCompletionService<Long> completionService) {
		try {
			long totalTime = 0;
			for ( int i = 0; i < count; i++ )
				totalTime += completionService.take().get();
			return totalTime;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
