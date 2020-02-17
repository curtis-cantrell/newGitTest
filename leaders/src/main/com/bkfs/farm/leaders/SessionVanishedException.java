/**
 * 
 */
package com.bkfs.farm.leaders;

/**
 * This Exception is used to indicate that the Session was unexpectedly removed.  Either by the Ephemerator or something unexpected. 
 *
 */
public class SessionVanishedException extends Exception {
	
	private static final long serialVersionUID = 1L;

	
	public SessionVanishedException() {
		super();
	}

	
	public SessionVanishedException(String message)
	{
		super(message);
	}
}
