package com.cavirin.arap.workflow.prescan.utils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIntegerUtil {
	
	// increment count in atomicInteger map
	public static int incrementAtomic (Map<Long, AtomicInteger> map, Long assetId) {
		AtomicInteger ia = map.get(assetId);
		if (ia != null) {
			return ia.incrementAndGet();
		}
		return 0;
	}

	// decrement count in atomicInteger map
	public static int decrementAtomic (Map<Long, AtomicInteger> map,Long assetId) {
		AtomicInteger ia = map.get(assetId);
		if (ia != null) {
			return ia.decrementAndGet();
		}
		return 0;
	}

}
