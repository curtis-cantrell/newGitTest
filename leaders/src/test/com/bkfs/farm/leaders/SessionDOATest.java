package com.bkfs.farm.leaders;

import java.util.List;

import org.apache.log4j.BasicConfigurator;

import com.fnf.test.BaseDAOTest;
import com.fnf.test.TestConnectionBuilder;
import com.fnf.test.extensions.PA;
import com.lps.mg.db.ConnectionManager;
import com.lps.mg.log.DeferLogger;



public class SessionDOATest extends BaseDAOTest {
	
	String processName = "ProcessName";
	
	Integer sessionTimeout = 10;
	
	private static DeferLogger 	logger   = DeferLogger.getLogger(SessionDOATest.class);
			
	static {
		BasicConfigurator.configure();
	}
	
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
	

	
	public void testRespondsWellOnRelaseWithDatabaseProblems() 
	{
		final SessionManager dao = new SessionManager(timeConfig, processName, "p1", "eg_app01", "eg_app01");
		
		sessionTimeout = 2;
		
		try 
		{
			dao.acquireLeadership();
			
			Thread.sleep(3000);
			assertTrue("Should be the leader", dao.isLeading());

			dao.addListener(new SessionListener() {
				
				@Override
				public void sessionExtablished() {
					logger.info("** Main Test: SessionListener: SESSION ESTABLISHED");
				}
				
				@Override
				public void sessionInvalidated() {
					logger.info("** Main Test: SessionListener: SESSION VANISHED");
					logger.info("** Main Test: Test Passed.  SESSION VANISHED");
				}
			});
			
			
			logger.info("** Main Test: Releasing Leadership with a Base Database Connection");
			ConnectionManager.setConnectionBuilder(new BadConnectionBuilder(-1));
			dao.releaseLeadership();
			assertTrue("Should not be the leader", !dao.isLeading());
			
		} 
		catch (Exception ie)
		{
			ie.printStackTrace();
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
	

	public void testCanGetParticipants() 
	{
		try {
			String insert = "Insert into LEADER_PROCESS (LEADER_NAME,SEQUENCE,ID,OWNING_SERVER,PERFERED_SERVER,HEART_BEAT) values ('" + processName + "',2,'id1','eg_app01',null,to_timestamp('03-AUG-2017 10.56.50.257296000','DD-MON-YYYY HH.MI.SS.FF'))";
			getDatabaseConnector().add(insert);
			
			insert = "Insert into LEADER_PROCESS (LEADER_NAME,SEQUENCE,ID,OWNING_SERVER,PERFERED_SERVER,HEART_BEAT) values ('" + processName + "',1,'id2','eg_app02',null,to_timestamp('03-AUG-2017 10.56.47.257296000','DD-MON-YYYY HH.MI.SS.FF'))";
			getDatabaseConnector().add(insert);
			
			
			SessionManager client = new SessionManager(timeConfig, processName, "3", "03", "03");
			
			List<Participant> participants = client.getParticipants();
			
			logger.info(participants.get(0).toString());
			logger.info(participants.get(1).toString());
			
			assertTrue("There should have been 2 participants", participants.size() == 2);
			assertTrue("The first one in the list should be the leader", participants.get(0).isLeader());
			assertTrue("The second one in the list should NOT be the leader", !(participants.get(1).isLeader()));
			assertTrue("The Leader's Id should have been id2", participants.get(0).getId().equals("id2"));
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
	
	
	
	public void testCanReleaseLeadership() 
	{
		final SessionManager dao = new SessionManager(timeConfig, processName, "p1", "eg_app01", "eg_app01");
		
		try 
		{
			dao.acquireLeadership();
			
			Thread.sleep(3000);
			assertTrue("Should be the leader", dao.isLeading());
			logger.info("** Main Test: Releasing Leadership");
			dao.releaseLeadership();
			assertTrue("Should not be the leader", !dao.isLeading());
			
		} 
		catch (Exception ie)
		{
			ie.printStackTrace();
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
	
	
	
	
	public void testInternalRetryUntillSessionExpired() 
	{
		
		final SessionManager dao = new SessionManager(timeConfig, processName, "p1", "eg_app01", "eg_app01");
		
		try 
		{
			dao.addListener(new SessionListener() {
				
				@Override
				public void sessionExtablished() {
					logger.info("** Main Test: SessionListener: SESSION ESTABLISHED");
				}
				
				@Override
				public void sessionInvalidated() {
					logger.info("** Main Test: SessionListener: SESSION VANISHED");
					logger.info("** Main Test: Test Passed.  SESSION VANISHED");
				}
			});
			
			
			dao.acquireLeadership();
			
			//let main test have leadership for 2 seconds..
			Thread.sleep(6000);
			ConnectionManager.setConnectionBuilder(new BadConnectionBuilder(5)); 
			
			//now the heat beat is using a bad connection
			Thread.sleep(15000);
			
		} 
		catch (Exception ie)
		{
			ie.printStackTrace();
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
	
	
	
	
	
	
	public void testThrowsInteruptedExceptionAfterIsLeader() 
	{
		
		final SessionManager dao = new SessionManager(timeConfig, processName, "p1", "eg_app01", "eg_app01");
		
		try 
		{
			dao.addListener(new SessionListener() {
				
				
				@Override
				public void sessionExtablished() {
					logger.info("** Main Test: SessionListener: SESSION ESTABLISHED");
				}
				
				@Override
				public void sessionInvalidated() {
					logger.info("** Main Test: SessionListener: SESSION VANISHED");
					logger.info("** Main Test: Test Passed.  SESSION VANISHED");
				}
			});
			
			
			dao.acquireLeadership();
			
			//now that it has acquired Leadership.. create a new thread that will delete it from the database while it has leadership..
			Thread thread1 = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(4000);  //delay sub task execution 4 seconds
						String sql = "delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "' and SEQUENCE = " + 1;
						logger.info("Thread1 going to delete: " + sql);
						getDatabaseConnector().remove(sql);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			thread1.start();   //start the thread
			
			Thread.sleep(7000); //delay the main test 6 seconds.. 3 second past when the record was removed by the sub task
			                    //since the tick time is 2 seconds, it should have thrown an Interrupted Exception by now
			
			assertTrue("Should not still be leading", !dao.isLeading());
			
		} 
		catch (Exception ie)
		{
			ie.printStackTrace();
		}
	}
	
	
	
	
	private SessionListener defaultSessionListener = new SessionListener() {
		

		
		@Override
		public void sessionExtablished() {
			logger.info("** Main Test: SessionListener: SESSION ESTABLISHED");
		}
		
		@Override
		public void sessionInvalidated() {
			logger.info("** Main Test: SessionListener: SESSION VANISHED");
		}
	};
	
	
	public void testSequenceCollision()
	{
		
		final SessionManager leader1 = new SessionManager(timeConfig, processName, "p1", "eg_app02", null);
		final SessionManager leader2 = new SessionManager(timeConfig, processName, "p2", "eg_app02", null);
		
		try 
		{
			//create a runnable an have it wait for 10 seconds... and then removed the session of the leader that is waiting to acquire leadership. 
			Thread thread1 = new Thread(new Runnable() 
			{
				@Override
				public void run() 
				{
					try 
					{
						leader1.acquireLeadership();
					} 
					catch (Exception e) 
					{
						e.printStackTrace();
					}
				}
			});
			//create a runnable an have it wait for 10 seconds... and then removed the session of the leader that is waiting to acquire leadership. 
			Thread thread2 = new Thread(new Runnable() 
			{
				@Override
				public void run() 
				{
					try 
					{
						leader2.acquireLeadership();
						
					} 
					catch (Exception e) 
					{
						e.printStackTrace();
					}
				}
			});
			thread1.start();
			thread2.start();
		
			
			Thread.sleep(10000);
		
		}
		catch (Throwable t) 
		{
			t.printStackTrace();
		}
		finally 
		{
			try {
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "'");
			} catch (Exception e) {}	
		}
	}
	
	
	
	public void testAcquiringLeadershipThrowsInterruptedException()
	{
		try 
		{
			//insert an existing leader into the database
			String insert = "Insert into LEADER_PROCESS (LEADER_NAME,SEQUENCE,ID,OWNING_SERVER,PERFERED_SERVER,HEART_BEAT) values ('" + processName + "',1,'static','eg_app01',null, SYSTIMESTAMP)";
			getDatabaseConnector().add(insert);
			
			
			final SessionManager secondLeader = new SessionManager(timeConfig, processName, "p2", "eg_app02", null);
			
			//create a runnable an have it wait for 10 seconds... and then removed the session of the leader that is waiting to acquire leadership. 
			Thread thread1 = new Thread(new Runnable() 
			{
				@Override
				public void run() 
				{
					try 
					{
						Thread.sleep(10000);
						Integer sequence = (Integer)PA.getValue(secondLeader,"sequence");
						String sql = "delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "' and SEQUENCE = " + sequence;
						logger.debug("#########Thread1 going to delete: " + sql);
						getDatabaseConnector().remove(sql);
						
					} 
					catch (Exception e) 
					{
						e.printStackTrace();
					}
				}
			});
			thread1.start();   //start the thread
			
			try 
			{
				secondLeader.addListener(defaultSessionListener);
				secondLeader.acquireLeadership();
				//This guy should be BLOCK WAITING.... until he throws an InterupptedException because his database record is going to be removed by thread1 in a few seconds
				fail("Test Failed.  Should have thrown an interupted exception");
			} 
			catch (Exception ie)
			{
				logger.info("** Main Test: Test Passed. Caught " + ie.getClass().getName() + ", " + ie.getMessage());
				return;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally 
		{
			try {
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "'");
			} catch (Exception e) {}	
		}
	}
	
	

	
	
	
	
	public void testCanAquireLeadership() 
	{
		
		try 
		{
			//insert an existing leader into the database
			String insert = "Insert into LEADER_PROCESS (LEADER_NAME,SEQUENCE,ID,OWNING_SERVER,PERFERED_SERVER,HEART_BEAT) values ('" + processName + "',1,'static','eg_app01',null,to_timestamp('03-AUG-2017 10.56.47.257296000','DD-MON-YYYY HH.MI.SS.FF'))";
			getDatabaseConnector().add(insert);
			
			final SessionManager dao = new SessionManager(timeConfig, processName, "p2", "eg_app02", null);
			
			//create a runnable an have it wait for 10 seconds... and then removed the first leader after the second is waiting for leadership.  
			Thread thread1 = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(3000);
						String sql = "delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "' and SEQUENCE = 1";
						logger.info("Thread1 going to delete: " + sql);
						getDatabaseConnector().remove(sql);
						
						//now wait a 3 secconds and check if the other leader has accepted leadership.  4 is double the tick time
						Thread.sleep(4000);
						
						assertTrue("Should have accepted leadership", dao.isLeading());
						
						logger.info("** Main Test: Test Passed.  Leadership was acquired.");
						
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			thread1.start();   //start the thread
			
			try 
			{
				dao.addListener(new SessionListener() {
					
					
					@Override
					public void sessionExtablished() {
						logger.info("** Main Test: SessionListener: SESSION ESTABLISHED");
					}
					
					@Override
					public void sessionInvalidated() {
						logger.info("** Main Test: SessionListener: SESSION VANISHED");
					}
				});
				
				//This guy should be BLOCK WAITING.... until he the other leader is removed.
				dao.acquireLeadership();
				
				//prevent the test from exiting until the test in thread1 has a chance to run..
				Thread.sleep(7000);
			} 
			catch (Exception e)
			{
				fail("Should not throw an exception");
				
				return;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally 
		{
			try {
				getDatabaseConnector().remove("delete from LEADER_PROCESS where LEADER_NAME = '" + processName + "'");
			} catch (Exception e) {}	
		}
	}
	
	
	

}
