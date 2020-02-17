package com.bkfs.farm.leaders;

import java.util.Random;

import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.PeriodType;

import com.fnf.test.BaseDAOTest;
import com.fnf.test.TestConnectionBuilder;
import com.fnf.test.extensions.PA;
import com.lps.mg.db.ConnectionManager;
import com.lps.mg.log.DeferLogger;


public class LeadereProcessTest extends BaseDAOTest {
	
	String processName = "Name1";
	
	int loopCount = 0;
	
	LeaderProcess leader1 = null;
	LeaderProcess leader2 = null;
	
	private static DeferLogger 	logger   = DeferLogger.getLogger(LeadereProcessTest.class);	
	
	SessionTimeConfiguration timeConfig = new SessionTimeConfiguration() {
		
		@Override
		public int getTickTime() {
			return 2;
		}
		
		@Override
		public int getSessionTimeout() {
			return 10;
		}
	};
	

	
	public void hold(long delay) throws Exception 
	{
		long start = System.currentTimeMillis();
		long now = 0;
		do {
			Thread.sleep(2000);
			now = System.currentTimeMillis();
			//logger.debug("Still testing Holding..." + (now - (start + delay * 1000)));
		} while (now < (start + (delay * 1000)));
	}
	
	
	
	
	boolean keepGoing = true;
	boolean server1KeepGooing = true;
	boolean server2KeepGooing = true;
	
	
	
	//set the session time out to 10 seconds.. 
	SessionTimeConfiguration myTimeConfig = new SessionTimeConfiguration() {
				
		@Override
		public int getTickTime() {
			return 2;
		}
				
		@Override
		public int getSessionTimeout() {
			return 5;
		}
	};
	
	
	public class ProductionTimer implements SessionTimeConfiguration {

		private int timeout = 45;
		private int tickTime = 2;

		public ProductionTimer(int timeout, int tickTime)
		{
			this.timeout = timeout;
			this.tickTime = tickTime;
		}
		
		public int getSessionTimeout() {
			return timeout;
		}
		
		public int getTickTime() {
			return tickTime;
		};
	}
	
	
	
	
	/**
	 * Listens to Session Events to let the takeLeadership if it should release Leadership or not
	 */
	private  class InternalSessionListener implements SessionListener
	{
		 @Override
		public void sessionExtablished() {
			logger.debug("SESSION ESTABLISHED");
			server1KeepGooing = true;
		}
		 
		 @Override
		public void sessionInvalidated() {
			logger.debug("SESSION INVALIDATED");
			server1KeepGooing = false;
		}
	}
	
	
	public void testLeaderShipCollision()
	{
		
		leader1 = new LeaderProcess(myTimeConfig, processName, "id-1", "eg_app01", "eg_app01", 
				
				new LeaderProcessListener() {
			
					boolean keepGoing1 = true;
					
					@Override
					public void sessionExtablished() {
						loopCount++;
						logger.info("ESTABLISHED SESSION *****************");
					}
					@Override
					public void sessionInvalidated() {
		
						keepGoing1 = false;
						logger.info("SESSION INVALIDATEED ******************. Signaling the Cancel Leadership");
						throw new CancelLeadershipException();
					}
					
					@Override
					public void takeLeadership() throws Exception {
						
						logger.debug("GOT LEADERSHIP**************");
						do {
							logger.debug("Still working in takeLeadership()");
							
							
						} while (keepGoing1);
						//reset the flag
						keepGoing1 = true;
					}
				});
		leader1.autoRequeue();
		
		
		Thread thread1 = new Thread(new Runnable() {
			@Override
			public void run() {
				try 
				{
					leader1.start();
					
				} catch (Exception e) 
				{
					e.printStackTrace();
				}
			}
		});
		
		
		
		 leader2 = new LeaderProcess(myTimeConfig, processName, "id-2", "eg_app02", "eg_app02", 
					
					new LeaderProcessListener() {
			 
			 			boolean keepGoing2 = true;
			 			
						@Override
						public void sessionExtablished() {
							loopCount++;
							logger.info("ESTABLISHED SESSION *****************");
								
						}
						@Override
						public void sessionInvalidated() {
			
							keepGoing2 = false;
							logger.info("SESSION INVALIDATEED ******************. Signaling the Cancel Leadership");
							throw new CancelLeadershipException();
						}
						
						@Override
						public void takeLeadership() throws Exception {
							
							logger.debug("GOT LEADERSHIP**************");
							do {
								logger.debug("Still working in takeLeadership()");
								Thread.sleep(2000);
								
							} while (keepGoing2);
							//reset the flag
							keepGoing2 = true;
						}
					});
		 leader2.autoRequeue();
				
				
		Thread thread2 = new Thread(new Runnable() {
			@Override
			public void run() {
				try 
				{
					leader2.start();
					
				} catch (Exception e) 
				{
					e.printStackTrace();
				}
			}
		});
		
		
		thread1.start();
		thread2.start();
		
		
		try {
			Thread.sleep(8000);
		} catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
		finally 
		{
			try {
				logger.info("Test Cleanup. Deleted Sessions from Database");
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "'");
			}
			catch (Throwable t)
			{
				logger.error("Problem removing the test leaders", t);
			}
		}
		
		
	}
	
	
	private int getTimeLapse(long start)
	{
		Duration d = new Duration(start, System.currentTimeMillis());
		Period p = d.toPeriod(PeriodType.seconds());
		int timeLapse = p.getSeconds();
		return timeLapse;
	}
	
	
	
	/**
	 * This test is for the fix to the known issue where server 1 creates a session, but then was blocked from 30 seconds waiting from a database connection.  
	 * When server1 experiences a WaitTimeoutException, only 30 seconds had passed. Therefore, server1 requested a connection again and is again blocked.  
	 * However, 16 seconds later (totaling 46 second) the Oracle job removes server1's session from the database.  
	 * 
	 * A few seconds later server2 starts up and a leader with the same name creates a session.  Since server1's session had been removed, server2 obtaines 
	 * a session with the same sequence that server1 originally had.
	 * 
	 * About 5 seconds after that, server1 gets a good database connection and starts to update the heart beat on its session. The trouble is that it is not server1's 
	 * session.  It is now updating the session created by server2. 
	 * 
	 * Now there are two servers updating the heart beat on the same session.  Both servers believe that everything is ok, but there are two leaders doing work 
	 * in two separate servers at the same time.
	 *  
	 * This test will ensure that with the new change server1 will lose leadership becuase it can no longer update the session created by server2. 
	 */
	public void testFixforMultipleLeadersUpdatingSameSession()
	{
		int goodConnectionTime = 10;
		int outageConnectionTime = 55;
		
		Boolean[] testFailed = {false};
		
		long start = System.currentTimeMillis();
		
		Thread server1 = new Thread(new Runnable() {
			
			@Override
			public void run() 
			{
				try 
				{
					
					leader1 = new LeaderProcess(new ProductionTimer(45, 2), "SAME_SESSION", "server1", "server1", "server1",
							
						new LeaderProcessListener() 
						{
						
							//this is simulating the InletLeader extends DefaultSessionListener
							@Override
							/**
							 * When a Session is Established, we will log the event
							 */
							public void sessionExtablished() 
							{
								
								logger.info("A New Session is Established at time lapse: " + getTimeLapse(start));
							}
							
							@Override
							/**
							 * When a Session is Invalidated, we will log the event and tell the Leader Process to Cancel it's Leadership
							 */
							public void sessionInvalidated() 
							{
								server1KeepGooing = false;
								logger.info("The Session was Invalidated.. at time lapse" + getTimeLapse(start));
								throw new CancelLeadershipException();
							}
							

							@Override
							public void takeLeadership() throws Exception 
							{
								logger.debug("Leader 1 - GOT LEADERSHIP **************");
								do 
								{
									logger.debug("                      SERVER 1 STILL LEADING..... " + getTimeLapse(start));
									Thread.sleep(2000);
									
									if (getTimeLapse(start) > 70)
									{
										testFailed[0] = true;
									}
									
								} while (server1KeepGooing);
							}
					});
					
					//this is simulating the InternalSessionListener sessionListener = new InternalSessionListener() added in Inlet Leader
					//leader1.addListener(new InternalSessionListener());
					
					
					//I want to get server1 a connection that will hang in Websphere, timeout, and then come back after a peiod of time 
					SessionManager sessionManager = (SessionManager)PA.getValue(leader1, "sessionManager");
					PA.setValue(sessionManager, "connectionBuilder", new IntermittentConnectionBuilder(goodConnectionTime, outageConnectionTime, "Server1"));
					
					//I am purposefully not setting this leader to auto requeue..
					//leader1.autoRequeue();
					leader1.start();
					
				}
				catch (Throwable t) 
				{
					logger.error("Error in Sever1", t);
				}
			}
		});
		
		
		
			Thread server2 = new Thread(new Runnable() {
			
			@Override
			public void run() 
			{
				try 
				{
					
					leader2 = new LeaderProcess(new ProductionTimer(45, 2), "SAME_SESSION", "server2", "server2", "server2",
							
						new LeaderProcessListener() 
						{
						
							//this is simulating the InletLeader extends DefaultSessionListener
							@Override
							/**
							 * When a Session is Established, we will log the event
							 */
							public void sessionExtablished() 
							{
								
								logger.info("Server2: A New Session is Established at time lapse: " + getTimeLapse(start));
							}
							
							@Override
							/**
							 * When a Session is Invalidated, we will log the event and tell the Leader Process to Cancel it's Leadership
							 */
							public void sessionInvalidated() 
							{
								server2KeepGooing = false;
								logger.info("Server2: The Session was Invalidated.. at time lapse" + getTimeLapse(start));
								throw new CancelLeadershipException();
							}
							

							@Override
							public void takeLeadership() throws Exception 
							{
								logger.debug("Leader 2 - GOT LEADERSHIP **************");
								do 
								{
									logger.debug("                      SERVER 2 STILL LEADING..... " + getTimeLapse(start));
									Thread.sleep(2000);
									
								} while (server2KeepGooing);
							}
					});
					
					//I am purposefully not setting this leader to auto requeue..
					//leader1.autoRequeue();
					leader2.start();
					
				}
				catch (Throwable t) 
				{
					logger.error("Error in Sever1", t);
				}
			}
		});

		
		try 
		{
			
			server1.start();
			
			do 
			{
				Thread.sleep(1000);
			} 
			while (getTimeLapse(start) < 60);
			
			server2.start();
			
			
			do 
			{
				Thread.sleep(1000);
			} 
			while (getTimeLapse(start) < 75);

			
			if (testFailed[0])
			{	
				fail("Server1 should have reliquished Leadership at time lapse 66 seconds, but did not");
			}	
			
		}
		catch (Exception e)
		{
			logger.error("Error Running Test Harness", e);
		}
		finally 
		{
			try {
				logger.info("Test Cleanup. Deleted Sessions from Database at time lapse: " + getTimeLapse(start));
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = 'SAME_SESSION'");
			}
			catch (Throwable t)
			{
				logger.error("Problem removing the test leaders", t);
			}
		}
		
		
		
		
	}
	
	
	
	/*
	public void testResearchingMultipleLeadersAtSameTimeBug()
	{
		
		int goodConnectionTime = 10;
		int badConnectionTime = 50;
		
		Thread server1 = new Thread(new Runnable() {
			
			@Override
			public void run() 
			{
				try 
				{
					
					leader1 = new LeaderProcess(new ProductionTimer(45, 2), "TEST-BUG", "server1", "server1", "server1",
							
						new LeaderProcessListener() 
						{
						
							//this is simulating the InletLeader extends DefaultSessionListener
							@Override
							
							// * When a Session is Established, we will log the event
							public void sessionExtablished() {
								logger.info("A New Session is Established");
							}
							
							@Override
							// * When a Session is Invalidated, we will log the event and tell the Leader Process to Cancel it's Leadership
							public void sessionInvalidated() {
	
								logger.info("The Session was Invalidated.. Signaling the Cancel Leadership");
								throw new CancelLeadershipException();
							}
							

							@Override
							public void takeLeadership() throws Exception 
							{
								logger.debug("Leader 1 - GOT LEADERSHIP **************");
								do 
								{
									logger.debug("                      SERVER 1 STILL LEADING.....");
									Thread.sleep(2000);
									
								} while (server1KeepGooing);
							}
					});
					
					//this is simulating the InternalSessionListener sessionListener = new InternalSessionListener() added in Inlet Leader
					leader1.addListener(new InternalSessionListener());
					
					
					//I want to get server1 a connection that will hang in Websphere, timeout, and then come back after a peiod of time 
					SessionManager sessionManager = (SessionManager)PA.getValue(leader1, "sessionManager");
					PA.setValue(sessionManager, "connectionBuilder", new IntermittentConnectionBuilder(goodConnectionTime, badConnectionTime, "Server1"));
					
					
					//I am purposefully not setting this leader to auto requeue..
					leader1.autoRequeue();
					leader1.start();
					
				}
				catch (Throwable t) 
				{
					logger.error("Error in Sever1", t);
				}
			}
		});
		

		
		Thread server2 = new Thread(new Runnable() 
		{
			@Override
			public void run() 
			{
				try 
				{
					leader2 = new LeaderProcess(new ProductionTimer(45, 2), "TEST-BUG", "server2", "server2", "server1",
							
						new LeaderProcessListener() 
						{
							@Override
							public void sessionExtablished() 
							{
								server2KeepGooing = true;
								logger.info("Leader 2 - ESTABLISHED SESSION *****************");
							}
							
							@Override
							public void sessionInvalidated() 
							{
								server2KeepGooing = false;
								logger.info("Leader 2 - SESSION INVALIDATEED ******************");
								//throw new CancelLeadershipException();
							}
							
							@Override
							public void takeLeadership() throws Exception 
							{
								logger.debug("Leader 2 - GOT LEADERSHIP **************");
								do 
								{
									logger.debug("                      SERVER 2 STILL LEADING.....");
									Thread.sleep(2000);
								} while (server2KeepGooing);
							}
					});
					
					//I am purposefully not setting this leader to auto requeue..
					leader2.autoRequeue();
					leader2.start();
				}
				catch (Throwable t)
				{
					logger.error("Error in Sever2", t);
				}
			}
		});
		
		
		try 
		{
			
			server1.start();
			hold(1);
			server2.start();
			
			hold(goodConnectionTime + badConnectionTime + 10);
			
		}
		catch (Exception e)
		{
			logger.error("Error Running Test Harness", e);
		}
		finally 
		{
			try {
				logger.info("Test Cleanup. Deleted Sessions from Database");
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = 'TEST-BUG'");
			}
			catch (Throwable t)
			{
				logger.error("Problem removing the test leaders", t);
			}
		}
		

	}
	
	*/
	
	
	
	
	
	public void testSessionIsInvalidated()
	{
		
		loopCount = 0;
		
		Thread thread1 = new Thread(new Runnable() {
			@Override
			public void run() {
				try 
				{
			        leader1 = new LeaderProcess(myTimeConfig, processName, "id-1", "eg_app01", "eg_app01", 
					
					new LeaderProcessListener() {
						@Override
						public void sessionExtablished() {
							loopCount++;
							logger.info("ESTABLISHED SESSION *****************");
							
							//second time the Session is established I will go back and removed the session with sequence 1.
							//this is because I do not have the ephemoator turned on
							if (loopCount == 2)
							{	
								try 
								{
									logger.debug("Deleting the orginal stuck Session...");
									getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "' and SEQUENCE = '1'");
								} catch (Exception e) {}
							}	
						}
						@Override
						public void sessionInvalidated() {
			
							keepGoing = false;
							logger.info("SESSION INVALIDATEED ******************. Signaling the Cancel Leadership");
							throw new CancelLeadershipException();
						}
						
						@Override
						public void takeLeadership() throws Exception {
							
							logger.debug("GOT LEADERSHIP**************");
							do {
								logger.debug("Still working in takeLeadership()");
								Thread.sleep(2000);
								
							} while (keepGoing);
							//reset the flag
							keepGoing = true;
						}
					});
				leader1.autoRequeue();
				leader1.start();
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		
		//main test
		thread1.start();
		
		try {
			
			hold(4);
			
			//insert another leader after the one created above.  I will use sequence 5
			String insert = "Insert into LEADER_PROCESS (LEADER_NAME,SEQUENCE,ID,OWNING_SERVER,PERFERED_SERVER,HEART_BEAT) values ('" + processName + "',5,'static','eg_app01',null,to_timestamp('03-AUG-2017 10.56.47.257296000','DD-MON-YYYY HH.MI.SS.FF'))";
			getDatabaseConnector().add(insert);
			
			//wait about 5 seconds then create bad database connection
			ConnectionManager.setConnectionBuilder(new BadConnectionBuilder(-1));
			
			hold(myTimeConfig.getSessionTimeout() + 5);
			
			logger.debug("I am going to fix the Database Connection Now");
			
			ConnectionManager.setConnectionBuilder(new TestConnectionBuilder());
			
			hold(10);
			
			for (Participant participant : leader1.getParticipants()) {
				logger.debug(participant.toString());
			}
			
			
			logger.debug("Main Test:  The other Session is being removed");
			getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "' and SEQUENCE = '5'");
			
			hold(5);
			
			for (Participant participant : leader1.getParticipants()) {
				logger.debug(participant.toString());
			}
			
			hold(15);
			
			logger.debug("Test is Over");
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally 
		{
			try {
				ConnectionManager.setConnectionBuilder(new TestConnectionBuilder());
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "'");
			} catch (Exception e) {
				e.printStackTrace();
			}	
		}
		
		
	}
	
	

	
	
	
	
	

	
	public void testTwoLeadersPassingLeadership() 
	{
		
		Thread thread1 = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					        leader1 = new LeaderProcess(timeConfig, processName, "id-1", "eg_app01", "eg_app01", 
							
							new LeaderProcessListener() {
								@Override
								public void sessionExtablished() {
									logger.info("A New Session is Established");
								}
								@Override
								public void sessionInvalidated() {
					
									logger.info("The Session was Invalidated.. Signaling the Cancel Leadership");
									throw new CancelLeadershipException();
								}
								
								@Override
								public void takeLeadership() throws Exception {
									
									Random r = new Random();
									int randomInt = r.nextInt(5) + 1;
									logger.debug("I have the Leadership Now...  waiting " + randomInt + " seconds...");
									Thread.sleep(randomInt * 1000);
									logger.debug("Releasing Leadership");
								}
							});
					leader1.autoRequeue();
					leader1.start();
					
				} catch (Exception e) {
					
				}
			}
		});

		
		Thread thread2 = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					  leader2 = new LeaderProcess(timeConfig, processName, "id-2", "eg_app02", "eg_app02", 
							
							new LeaderProcessListener() {
								@Override
								public void sessionExtablished() {
									logger.info("A New Session is Established");
								}
								@Override
								public void sessionInvalidated() {
					
									logger.info("The Session was Invalidated.. Signaling the Cancel Leadership");
									throw new CancelLeadershipException();
								}
								
								@Override
								public void takeLeadership() throws Exception {
									Random r = new Random();
									int randomInt = r.nextInt(5) + 1;
									logger.debug("I have the Leadership Now...  waiting " + randomInt + " seconds...");
									Thread.sleep(randomInt * 1000);
									logger.debug("Releasing Leadership");
								}
							});
					leader2.autoRequeue();
					leader2.start();
					
				} catch (Exception e) {
					
				}
			}
		});
		thread1.start();   //start the thread
		thread2.start();   //start the thread
		
		try {
			
			//let this test go for 20 seonds
			Thread.sleep(30000);
			logger.debug("Canceling Leadership for both Leaders");
			leader1.close();
			leader2.close();
			
			long start = System.currentTimeMillis();
			long now = 0;
			do {
				Thread.sleep(2000);
				now = System.currentTimeMillis();
				logger.debug("Still testing...");
			} while (now < (start + 10000));
			
		} catch (Exception e) {
			// TODO: handle exception
		}
		finally
		{
			try {
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "'");
			} catch (Exception e) {
				e.printStackTrace();
			}	
		}
		
	}
	
	
	
	
	
	
	
	
	
	long releaseLeadershipTime = 0l;
		
	public void testCanTakeLeadershipOneTime()
	{
		try 
		{
			LeaderProcess leader = new LeaderProcess(timeConfig, processName, "ped1d", "eg_app01", "eg_app02", 
					
					new LeaderProcessListener() {
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
						
						@Override
						public void takeLeadership() throws Exception {
							logger.debug("I have the Leadership Now...  waiting 5 seconds...");
							Thread.sleep(5000);
							logger.debug("Releasing Leadership");
							releaseLeadershipTime = System.currentTimeMillis();
							
						}
					});
			leader.start();
			boolean quitTest = false;
			do {
				
				Thread.sleep(1000);
				long now = System.currentTimeMillis();
				if (releaseLeadershipTime != 0 && ( now > (releaseLeadershipTime + 10000)))
				{
					quitTest = true; 
				} else {
					logger.debug("Still testing...");
				}
				
			} while (!quitTest);
		
		} 
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			try {
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "'");
			} catch (Exception e) {
				e.printStackTrace();
			}	
		}
	}
		
	
	public void testDoManualReque()
	{
		boolean requeued = false;
		try 
		{
			LeaderProcess leader = new LeaderProcess(timeConfig, processName, "ped1d", "eg_app01", "eg_app02", 
					
					new LeaderProcessListener() {
				
						private boolean firstTime = true;
				
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
						
						@Override
						public void takeLeadership() throws Exception {
							logger.debug("I have the Leadership Now...  waiting 5 seconds...");
							Thread.sleep(5000);
							logger.debug("Releasing Leadership");
							if (firstTime)
							{
								releaseLeadershipTime = System.currentTimeMillis();
							}
							firstTime = false;
						}
					});
			leader.start();
			boolean quitTest = false;
			do {
				
				Thread.sleep(1000);
				long now = System.currentTimeMillis();
				if (releaseLeadershipTime != 0 && ( now > (releaseLeadershipTime + 10000)))
				{
					quitTest = true; 
					
				} else {
					logger.debug("Still testing...");
					if (releaseLeadershipTime != 0)
					{
						if (!requeued)
						{	
							logger.debug("Going to Requeu now..");
							leader.requeue();
							requeued = true;
						}	
					}
				}
				
			} while (!quitTest);
		
		} 
		catch (Exception e)
		{
			
		}
		finally
		{
			try {
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "'");
			} catch (Exception e) {
				e.printStackTrace();
			}	
		}
	}
	
	

}
