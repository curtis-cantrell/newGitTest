package com.bkfs.farm.leaders;
import com.lps.mg.log.DeferLogger;


/**
 * A Default Session Listen with the desired behavior built in so when a Session is Invalidated, the Leader Process with surrender Leadership. 
 * 
 * By extending this class, you get the needed behavior by default. 
 */
public abstract class DefaultSessionListener implements LeaderProcessListener	{
	
	private static DeferLogger 		logger   			= DeferLogger.getLogger(DefaultSessionListener.class);
	
	@Override
	/**
	 * When a Session is Established, we will log the event
	 */
	public void sessionExtablished() {
		logger.info("A New Session is Established");
	}
	
	@Override
	/**
	 * When a Session is Invalidated, we will log the event and tell the Leader Process to Cancel it's Leadership
	 */
	public void sessionInvalidated() {

		logger.info("The Session was Invalidated.. Signaling the Cancel Leadership");
		throw new CancelLeadershipException();
	}

}
