package org.cubedb.offheap;

import java.nio.ByteBuffer;

import org.cubedb.core.Metric;

public class OffHeapMetric extends MultiBuffer implements Metric {

	public OffHeapMetric(int startPos) {
		super(startPos, Long.BYTES);
		//log.info("Creating a new metric with starting position {}", startPos);
	}

	public OffHeapMetric() {
		this(0);
	}

	@Override
	public synchronized void append(long value) {
		ensureBufferCapacity();
		lastBuffer.putLong(value);
		curPos++;
	}

	@Override
	public long get(int index) {
		if (index < startPos)
			return 0l;
		if (index >= curPos)
			return 0l;
		int pos = index - startPos;
		int positionWithinBuffer = pos % BUFFER_SIZE;
		int bufferIndex = pos / BUFFER_SIZE;
		long val = this.buffers.get(bufferIndex).getLong(positionWithinBuffer * Long.BYTES);
		return val;
	}
	
	@Override
	public void incrementBy(int index, long incr) {
		int pos = index - startPos;
		int positionWithinBuffer = pos % BUFFER_SIZE;
		int bufferIndex = pos / BUFFER_SIZE;
		int bytePosition = positionWithinBuffer * Long.BYTES;
		//log.debug("buffer #{}, position #{}", bufferIndex, bytePosition);
		ByteBuffer curBuffer = this.buffers.get(bufferIndex); 
		long val = curBuffer.getLong(bytePosition);
		curBuffer.putLong(bytePosition, val + incr);
		
	}
}
