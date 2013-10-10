// Copyright 2013 RobustNet Lab, University of Michigan. All Rights Reserved.

package com.mobiperf;

import java.util.concurrent.locks.ReentrantLock;

public class RRCTrafficControl {
	private static ReentrantLock traffic_lock;
	private static boolean is_initialized = false;
	
	private synchronized static void initialize() {
		if (!is_initialized) {
			traffic_lock = new ReentrantLock();
			is_initialized = true;
		}
	}

	/**
	 * Attempt to get a lock on 
	 * @return
	 */
	public static synchronized boolean PauseTraffic () {
		initialize();
		if (traffic_lock.isLocked()) {
			return false;
		} 
		traffic_lock.lock();
		return true;
		
	}
	
	public static boolean UnPauseTraffic() {
		initialize();
		if (traffic_lock.isHeldByCurrentThread()) {
			traffic_lock.unlock();
			return true;			
		}
		return false;			
	}
	
	public static boolean checkIfPaused() {
		initialize();
		return traffic_lock.isLocked();
	}
}
