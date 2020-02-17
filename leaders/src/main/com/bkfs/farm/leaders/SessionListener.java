package com.bkfs.farm.leaders;

public interface SessionListener {
	
	
	/**
	 * Called when a new Session is Established
	 */
	public void sessionExtablished();
	
	
	
	/**
	 * Called when the session has expired or has been unexpectedly removed
	 */
	public void sessionInvalidated(); 

}
