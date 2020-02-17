package com.lps.mg.service.publishing;

import com.lps.mg.cache.MGCache;
import com.lps.mg.cache.MGCacheFactory;
import com.lps.mg.common.MGCommonConstants;
import com.lps.mg.log.DeferLogger;
import com.lps.mg.utils.MessageResources;

import commonj.work.WorkManager;

/**
 * A Timer that wakes up at absolute time interval and does not wait for the execution time of a previous Timed Processes. 
 * 
 * This timer wakes up at next scheduled time regardless of the status or the delay of previous execution thread. In other words, 
 * this timer wakes up and starts the next execution even if the previous process execution takes longer than the sleep interval time..
 * 
 * The purpose of this class is to give concurrent processing.  The normal commomj Timers will wait beyond the sleep interval if the 
 * previous execution is still running. 
 * 
 * When the TimerListener is invoked, a null Timer is passed.  TimerListeners cannot call any methods on the Timer interface. 
 * 
 * Also, this timer's sleep interval can be dynamically changed from the CONFIG_PROPERTY screen, and the new value will take effect 
 * in the very next cycle.  
 *  
 * @author Curtis Cantrell
 */
public class AbsoluteRateTimer implements Runnable {
	
	private static DeferLogger logger = DeferLogger.getLogger(AbsoluteRateTimer.class);
	
	/** My Thread that is going to run*/
	private Thread myThread = null;
	
	/** The Flag to stop the internal loop.*/
	private volatile boolean stopRequested = false;
	
	/** Reference to the System Cache */
	private static MGCache mgCache = MGCacheFactory.getMGCacheImpl();
	
	protected static MessageResources messages			= MessageResources.getInstance();
	protected static final String bundle 				= "mgmessages";
	private static final String ERR_CONFIG_PROP			= "640-0048-0001";
	private static final String ERR_TIMER_LOADING		= "640-0048-0002";
	private static final String ERR_SCHEDULING_WORK		= "640-0048-0003";
	private static final String ERR_UNKNOWN				= "640-0048-0004";
	
	/** My name */
	private String name = null;
	
	/** WorkManager that will schedule the main work */
	private WorkManager workManager = null;
	
	/** The type of the Work that will be passed to the WorkManager */
	private String workType = null;
	
	/** The CONFIG_PROPERTIES Group Name */
	private String propertGroup = null;
	
	/** The CONFIG_PROPERTIES Inital Delay Key */
	private String propertyInitialDelayKey = null;
	
	/** The CONFIG_PROPERTIES Interval Delay Key Key */
	private String propertyIntervalKey = null;
	
	public AbsoluteRateTimer(String name, WorkManager workManager, String workType, String propertyGroup, String propertyInitialDelayKey, String propertyIntervalKey ) {
		this.name = name;
		this.workManager = workManager;
		this.workType = workType;
		this.propertGroup = propertyGroup;
		this.propertyInitialDelayKey = propertyInitialDelayKey;
		this.propertyIntervalKey = propertyIntervalKey;
		
		this.stopRequested = false;
		this.myThread = new Thread(this);
		this.myThread.start();
	}
	
	public void run() {
		
		if (Thread.currentThread() != myThread) {
			throw new RuntimeException("Only my internal Thread can invoke run()");
		}

		//Get initial delay from Config Properties cache. Default value is 10 seconds.
		long initialDelay = 10000;
		try {
			initialDelay = Long.parseLong(mgCache.getConfigPropertyValue(this.propertGroup, propertyInitialDelayKey));
		}
		catch (Exception ex) {
			logger.error(messages.getMessage(ERR_CONFIG_PROP, bundle, name, ex.getMessage()), ex);
		}
		
		try {
			//initial delay before start up... 
			try {	Thread.sleep(initialDelay);	} catch (Exception e) {}
			
			//Run the time in an infinite loop
			do {
				try {	//Intention Hard coding to satisfy VeraCode FlawId: 50 (Unsafe Reflection).
					if (workType.equals(MGCommonConstants.PUBLISH))	{
						workManager.schedule(new PublishingWork());
					}
					else if	(workType.equals(MGCommonConstants.RETRY))	{
						workManager.schedule(new RetryWork());
					}
					else	{
						logger.error(messages.getMessage(ERR_TIMER_LOADING, bundle, name, workType, "Invalid WorkType"));
						return;
					}
				}
				catch (Exception e) {
					logger.error(messages.getMessage(ERR_SCHEDULING_WORK, bundle, workType, e.getMessage()), e);
				}
				
				//It is time to go to sleep for the intervalDelay
				long intervalPeriod = 1000;
				try {
					intervalPeriod = Long.parseLong(mgCache.getConfigPropertyValue(this.propertGroup, propertyIntervalKey));
					if (logger.isDebugEnabled()) logger.debug(name + ", going to sleep for " + intervalPeriod + " millis before invoking the timer again");
				}
				catch(Exception e) {
					logger.warn(name + ", The Config Property for the interval delay was not found or not in a numeric format: " + propertyIntervalKey + ".  Setting to defalut value of 2000 millies");
				}
				finally {
					try {	Thread.sleep(intervalPeriod);	} catch (Exception e) {}
				}
				
			} while (!stopRequested);
			
			if (logger.isDebugEnabled())	logger.debug(name + "Timer has stoped.");
		}
		catch (Exception ex) {  //some real strange error happened.
			logger.error(messages.getMessage(ERR_UNKNOWN, bundle, name, workType, ex.getMessage()), ex);
		} 
	}
	
	public void stop() {
		this.stopRequested = true;
		if (logger.isDebugEnabled()) logger.debug(name + "Timer stop requested");
	}
}

