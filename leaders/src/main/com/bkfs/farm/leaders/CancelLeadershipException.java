package com.bkfs.farm.leaders;


/**
 * When thrown from {@link SessionListener#sessionInvalidated()}, it will cause {@link LeaderProcess#cancelLeadership()} to get called. 
 */
public class CancelLeadershipException extends RuntimeException
{

	private static final long serialVersionUID = 1L;

	public CancelLeadershipException()
    {
    }
   
}
