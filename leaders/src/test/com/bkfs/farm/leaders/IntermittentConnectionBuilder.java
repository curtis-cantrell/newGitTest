package com.bkfs.farm.leaders;

import java.sql.Connection;
import java.sql.SQLException;



import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.PeriodType;

import com.ibm.websphere.ce.cm.ConnectionWaitTimeoutException;
import com.lps.mg.db.ConnectionBuilder;
import com.lps.mg.db.ConnectionManager;
import com.lps.mg.log.DeferLogger;



public class IntermittentConnectionBuilder implements ConnectionBuilder {
	
	private static DeferLogger 	logger   = DeferLogger.getLogger(IntermittentConnectionBuilder.class);	
	
	
	
	private long start = -1; 
	private int good = -1;
	private int timeout = -1;
	
	/** who this connection was given to */
	private String owner = null;
	
			

	/**
	 * Constructor
	 * @param runTime  How long in seconds to run ok
	 * @param timeout  How long in seconds to have an outage until the connection returns 
	 */
	public IntermittentConnectionBuilder(int good, int timeout, String owner)
	{
		this.owner = owner;
		this.start = System.currentTimeMillis();
		this.good = good;
		this.timeout = timeout;
	}	
	
	@Override
	public Connection getLeaderProcessConnection() throws SQLException 
	{
		long now = System.currentTimeMillis();
		
		Duration d = new Duration(start, now);
		Period p = d.toPeriod(PeriodType.seconds());
		int timeLapse = p.getSeconds();
		
		//still in the first period of good time...
		if (timeLapse < good)
		{
			//logger.debug("returning Good Connection to " + owner);
			return ConnectionManager.getMGConnection();
		}
		
		
		//this is the black out time when there is a connection time out... 
		if (timeLapse <= (good + timeout)) 
		{
			int secondsWaiting = 1;
			//the connnection is frozen for a period of time up to the timeout, but if I reach 30 seconds, throw the timeout.
			while (true)
			{
				try 
				{
					Thread.sleep(1000);
				} 
				catch (InterruptedException ie)
				{
					//empty
				}
				//If I waited too long, throw the wait exception... every 30 seconds
				if (secondsWaiting++ > 30)
				{
					logger.error("Wait Timeout for " + owner + "... Thowing com.ibm.websphere.ce.cm.ConnectionWaitTimeoutException");
					ConnectionWaitTimeoutException cwte = new com.ibm.websphere.ce.cm.ConnectionWaitTimeoutException("Problem updating the Heart beat");
					cwte.setNextException(new SQLException());
					throw cwte;
				}
				else
				{
					now = System.currentTimeMillis();
					d = new Duration(start, now);
					p = d.toPeriod(PeriodType.seconds());
					timeLapse = p.getSeconds();
					logger.debug("                                      HUNG CONNECTION for " + owner + "- timeLapse = " + timeLapse + ", good+timeout = " + (good + timeout));
					if (timeLapse > (good + timeout))
					{
						break;
					}
				}
			}
		}
		//timeLapse > (good + timeout)) which means the good connection has returned  
		return ConnectionManager.getMGConnection();
	}

	
	@Override
	public Connection getLWDConnection() throws SQLException {
		return null;
	}


	@Override
	public Connection getMGInletConnection() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Connection getMGConnection() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}
	

}
