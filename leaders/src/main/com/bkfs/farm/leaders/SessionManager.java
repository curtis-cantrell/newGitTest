/**
 * 
 */
package com.bkfs.farm.leaders;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.PeriodType;

import com.google.common.base.Preconditions;
import com.lps.mg.db.ConnectionBuilder;
import com.lps.mg.db.ConnectionManager;
import com.lps.mg.db.ConstraintViolationException;
import com.lps.mg.db.DAOException;
import com.lps.mg.log.DeferLogger;

/**
 * A Database Implementation of the Leader Process Session 
 * 
 * This class represents a session between the Leader Process and the database.   Therefore, a separate instance of this class is required for every Leadership process. 
 */
public class SessionManager  {
	private static DeferLogger 	logger   = DeferLogger.getLogger(SessionManager.class);	


	/** The name of the leadership process.  All processes participating in the same group will have the same name */
	private String processName;
	
	/** The Sequence of this leader process if there is a session already established */
	private Integer sequence;
	
	/** An optional id for this leadership process */
	private String id; 
	
	/** A list of listener that is interested in Session events */
	private List<SessionListener> listeners = new ArrayList<SessionListener>();
	
	/** The name of the sever that this thread is executing on */
	private String owningServer; 
	
	/** An optional server name where it is desired the leadership process migrate to */
	private String preferredServer;
	
	/** The last heat beat this Session had */
	private Date heatbeat;
	
	/** Flag to indicated that a Session is written to the database, at least as far as I know */
	private boolean sessionEstablished = false;
	
	/** Am I currently the leader */
	private boolean isLeading = false;
	
	
	private static final ThreadFactory defaultThreadFactory = ThreadUtils.newThreadFactory("SessionManager");
	
	private CloseableExecutorService executorService = null; 
	
	private AtomicReference<Future<?>> heartBeatTask = new AtomicReference<Future<?>>(null);
	
	
	private SessionTimeConfiguration sessionTimeConfiguration = null;
	
	// injecting for unit testing
	private ConnectionBuilder connectionBuilder = null;

	
	
	/** 
	 * Constructor 
	 * @param processName The name of the leadership process
	 */
	public SessionManager(SessionTimeConfiguration timeConfiguration, String processName, String id, String owningServer, String preferedServer)
	{
		
		logger.debug("constucted - Process: " + processName + ", Seq: " + sequence + ", id: " + id);
		
		Preconditions.checkNotNull(processName, "process name cannot be null");
		Preconditions.checkNotNull(owningServer, "owning server cannot be null");
		Preconditions.checkNotNull(timeConfiguration, "Time Configuration cannot be null");
		
		this.sessionTimeConfiguration = timeConfiguration;
		this.processName = processName;
		this.id = id;
		this.owningServer = owningServer;
		this.preferredServer = preferedServer;
		
		this.executorService = new CloseableExecutorService(Executors.newSingleThreadExecutor(defaultThreadFactory), true);
	}

	
	/**
	 * Adds a Session Listener so that changes to the Session state can be handled
	 * @param listener The session Listener
	 */
	public void addListener(SessionListener sessionListener) 
	{
		listeners.add(sessionListener);

	}

    /**
     * Return a sorted list of all current processes participating in the leadership
     * 
     * @return list of Participants 
     * @throws Exception
     */
	public List<Participant> getParticipants() throws Exception 
	{
		
		return findParticipants();
		
	}
	
	/**
	 * Updates the Perfered Server Name
	 * @param preferredServer The new name 
	 */
	public void setPerferredServer(String preferredServer)
	{
		try 
		{
			recordNewPreferedServer(preferredServer);
			this.preferredServer = preferredServer;
		} 
		catch (DAOException | SessionVanishedException e) 
		{
			logger.debug("setPerferedServer() - Problem updating the Prefered Server Name - Process: " + processName + ", Seq: " + sequence + ", id: " + id + ", Trying AGAIN!");
		}
		
	}
	

    /**
     * Creates a Session and then waits to acquires leadership. 
     * 
     * Blocks until leadership is available
     *
     * @throws DAOException if there was a database problem creating the session 
     * @throws SessionVanishedException if the session was removed while waiting for leadership. 
     * @throws InterruptedException if the thread was interrupted
     */
	public void acquireLeadership() throws DAOException, SessionVanishedException, InterruptedException
	{
		
		logger.debug("acquireLeadership() - Process: " + processName + ", Seq: " + sequence + ", id: " + id);
		//while(sequence == null)
		while(!sessionEstablished)
		{	
			try {
				sequence = createSession();
				sessionEstablished = true;
			} 
			catch (ConstraintViolationException cve)
			{
				//two threads tried to aquire leadership at the exact same time and got the same sequences 
				logger.debug("acquireLeadership() Constraint Violation - Process: " + processName + ", Seq: " + sequence + ", id: " + id + ", Trying AGAIN!");
			} 
			catch (DAOException daoe)
			{
				//there was a database problem inserting the new session
				throw daoe;
			}
		} //repeat until sessionEstablished
		
		for (SessionListener sessionListener : listeners) {
			sessionListener.sessionExtablished();
		}

 		//while I am not the leader, state in this loop
		boolean acquiredLeadership = false;
		try
		{
			do 
			{
				logger.debug("acquireLeadership() - Waiting to acquireLeadership " + processName + "-" + id);
				acquiredLeadership = isLeader();
				Thread.sleep(sessionTimeConfiguration.getTickTime());
				
			} while (!acquiredLeadership);
			
			logger.debug("acquireLeadership() - TOOK LEADERSHIP " + processName + ", sequence = " + sequence);
			isLeading = true;
		}
		catch (SessionVanishedException svs)
		{
			logger.debug("SessionVanishedException while waiting to acquireLeadership, " + svs.getMessage());
			InvalidateSession();
			throw svs;
		}
		catch (InterruptedException ie) {
			
			Thread.currentThread().interrupt();
			throw ie;
			
		}
		catch (DAOException daoe)
		{
			logger.error("DAOException while waiting to acquireLeadership, " + daoe.getMessage());
			throw daoe;
		}
		

		//leadership has been acquired now.. so set up and start the heart beat monitor thread before I return
		Future<Void> task = executorService.submit(new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                try
                {
                    doHeatBeat();
                }
                finally
                {
                	//is there any clean up that I need.. if so put it here. 
                }
                return null;
            }
        });
		heartBeatTask.set(task);
	}
	
	/**
	 * Is there a Session established right now
	 * @return true if there is a session, false otherwise
	 */
	public boolean sessionEstablished() 
	{
		return sessionEstablished;
	}
	
	/**
	 * Am I currently the Leader of the group
	 * @return true if I am leading, false otherwise. 
	 */
	public boolean isLeading() 
	{
		return isLeading;
	}

	
	
	/** 
	 * Every tick time, it updates the heat beat that is recorded on the session.
	 * 
	 * If the heat beat cannot be updated for for Session Timeout.. then the Session Removed Event will be triggered 
	 * @throws InterruptedException 
	 */
	private void doHeatBeat() 
	{
		do {
			try 
			{
				Thread.sleep(sessionTimeConfiguration.getTickTime());
				recordHeatBeat();
				//logger.debug("doHeatBeat() - " + processName + "-" + sequence + "-" + id + ", Sent heart Beat - HeatBeat: " + heatbeat);
			}
			catch (SessionVanishedException sre)
			{
				InvalidateSession();
				
			}
			catch (InterruptedException ie)
			{
				logger.debug("doHeatBeat() - " + processName + "-" + sequence + "-" + id + ", do HeatBeat Interupted");				
			}
			catch (DAOException dao)
			{
				logger.debug("doHeatBeat() - " + processName + "-" + sequence + "-" + id + ", Caught " + dao.getClass().getName());	
				if (isSessionExpired())
				{
					logger.debug("doHeatBeat() - " + processName + "-" + sequence + "-" + id + ", Session has Expired so I'm invalidating the the Session");		
					InvalidateSession();
				}
			}
		} while (sessionEstablished());
		
		logger.debug("doHeatBeat() - " + processName + "-" + sequence + "-" + id + ", Stopped sending heart Beat");
	}
	
	
		
	/** 
	 * Called when the Session vanished from the database.. I was alive, but it vanished
	 * Called when I have not been able to update the heat beat is such a long time that I know my session has expired.
	 * 
	 * The Session Listener will be notified that the Session is now invalid
	 * Internal State will be reset so this can be used again
	 */
	private void InvalidateSession() 
	{
		logger.debug("Calling all Listerners to Invalidate this Session");
		for (SessionListener sessionListener : listeners) {
			sessionListener.sessionInvalidated();
		}
		
		if (heartBeatTask.get() != null)
		{
			heartBeatTask.get().cancel(true);	
		}
		
		//reset everything
		//sequence = null;
		heatbeat = null;
		sessionEstablished = false;
		isLeading = false;

	}
	
	
    /**
     * Removes the Session if one exist. 
     * 
     */
	public void releaseLeadership()  
	{
		logger.debug("releaseLeadership() - Process: " + processName + ", Seq: " + sequence + ", id: " + id + ", Released Called");
		do 
		{	
			try {
				removeSession();
				
				if (heartBeatTask.get() != null)
				{
					heartBeatTask.get().cancel(true);	
				}
				
				//sequence = null;
				heatbeat = null;
				sessionEstablished = false;
				isLeading = false;
				
				
			} 
			catch (DAOException e)
			{
				if (isSessionExpired())
				{
					InvalidateSession();
				}
			}
		} while (sessionEstablished());
		
		logger.debug("releaseLeadership() - Process: " + processName + ", id: " + id + ", Leadership Released and internals reset");
	}
	
	
	
	
	
	/**
	 * Determines if the duration since the last heart beat is greater than the configured session time out.  
	 * @return true if the session is times out. 
	 */
	private boolean isSessionExpired()
	{
		if (!sessionEstablished())
		{
			return true;
		}
		
		int sessionTimeout = sessionTimeConfiguration.getSessionTimeout();
		
		long lastHeatBeat = heatbeat.getTime();
		long now = System.currentTimeMillis();
		
		Duration d = new Duration(lastHeatBeat, now);
		Period p = d.toPeriod(PeriodType.seconds());
		
		int timeLapsed = p.getSeconds();
		logger.debug("isSessionExpired() Session Time out: " + sessionTimeout + ", time lapsed since last heat beat: " + timeLapsed);
		
		return p.getSeconds() > sessionTimeout;
	}
	
	
	/**
	 * Gets all the Participates that have the same Process Name 
	 * @return The List<Participant>
	 * @throws DAOException if there is a database error
	 */
	private List<Participant> findParticipants() throws DAOException 
	{
		
		Connection connection = null;
		PreparedStatement preparedStatement = null;
		ResultSet resultSet = null;
		String SELECT_SQL = "select * from LEADER_PROCESS where LEADER_NAME = ? order by SEQUENCE";
		
		try 
		{
			connection = connectionBuilder == null ? ConnectionManager.getLeaderProcessConnection() : connectionBuilder.getLeaderProcessConnection();
			preparedStatement = connection.prepareStatement(SELECT_SQL);
			preparedStatement.setString(1, this.processName);
			resultSet = preparedStatement.executeQuery();
			
			ArrayList<Participant> participants = new ArrayList<Participant>();

			boolean first = true;
			//just get the first one to see if it is me
			while (resultSet.next())
			{
				
				participants.add(new Participant(resultSet.getString("LEADER_NAME"), 
											     resultSet.getInt("SEQUENCE"), 
											     resultSet.getString("ID"), 
											     resultSet.getString("OWNING_SERVER"), 
											     resultSet.getString("PERFERED_SERVER"), 
											     first));
				
					first = false;  //only the first one is the leader
			}
			return participants;
			
		}
		catch (Exception e) 
		{
			logger.error("Problem querying the Participants in the leadership group", e);
			throw new DAOException("Problem querying the Participants in the leadership group",e);
		}
		finally 
		{
			ConnectionManager.close(resultSet, preparedStatement, connection);
		}
	}
	
	
	private boolean isLeader() throws DAOException, SessionVanishedException
	{
		Participant me = new Participant(processName, sequence);
		try 
		{
			List<Participant> participants = findParticipants();
			
			//see if I am in the list of participants
			if (!participants.contains(me))
			{
				//somehow my session has been removed from the list of processes queue for leadership...
				throw new SessionVanishedException("Discovered Session was removed while checking if I am the Leader");
			} 
			else
			{
				//update my heatbeat
				recordHeatBeat();
			}
			return participants.get(0).equals(me);
		} 
		catch (SessionVanishedException svs)
		{
			throw svs;
		}
		catch (Exception e) 
		{
			logger.error("Problem checking if I am the leader: Name = " + this.processName + ", Sequence = " + this.sequence, e);
			throw new DAOException(processName + "-" + sequence + "-" + id + ", Problem Checking for Leadership. " + e.getMessage(),e);
		}
	}
	
	
	
	
	

	
	/**
	 * Removes a row in the LEADER_PROCESS table. 
	 * 
	 *  
	 * @throws DAOException
	 * @throws {@link IllegalStateException} The session must have already been created. 
	 */
	protected void removeSession() throws DAOException
	{
		Preconditions.checkState(sessionEstablished,  "removeSession() - No session has yet been created");
		Connection connection = null;
		PreparedStatement preparedStatement = null;
		String REMOVE_SQL = "delete from LEADER_PROCESS where LEADER_NAME = ? and SEQUENCE = ?";
		
		try 
		{
			connection = connectionBuilder == null ? ConnectionManager.getLeaderProcessConnection() : connectionBuilder.getLeaderProcessConnection();
			preparedStatement = connection.prepareStatement(REMOVE_SQL);
			preparedStatement.setString(1, this.processName);
			preparedStatement.setInt(2, sequence);
			preparedStatement.execute();
			
			logger.debug("removeSession() - " + processName + "-" + sequence + "-" + id + ", Removed Session From database");
			
		} 
		catch (Exception e) 
		{
			logger.error("Problem removing Session Leadership Process: Name = " + this.processName + ", Sequence = " + this.sequence, e);
			throw new DAOException("Problem removing Session - Process: Name = " + this.processName + ", Sequence = " + this.sequence);
		}
		finally 
		{
			ConnectionManager.close(preparedStatement, connection);
		}
		
	}
	
	
	protected void recordNewPreferedServer(String preferedServer) throws DAOException, SessionVanishedException 
	{
		Preconditions.checkState(sessionEstablished,  "recordNewPreferedServer() - No session has yet been created");
		Connection connection = null;
		PreparedStatement preparedStatement = null;
		String UPDATE_SQL = "update LEADER_PROCESS set PERFERED_SERVER = ? where LEADER_NAME = ?";
		try 
		{
			connection = connectionBuilder == null ? ConnectionManager.getLeaderProcessConnection() : connectionBuilder.getLeaderProcessConnection();
			preparedStatement = connection.prepareStatement(UPDATE_SQL);
			preparedStatement.setString(1, preferedServer);
			preparedStatement.setString(2, processName);
			int retval = preparedStatement.executeUpdate();
			
			if (retval == 0)
			{
				logger.debug("recordNewPreferedServer() - " + processName + "-" + sequence + "-" + id +": PreferedServer could not be updated.  Session had been removed");
				throw new SessionVanishedException();
			}
			else
			{
				logger.debug("recordNewPreferedServer() - " + processName + "-" + sequence + "-" + id +": Updated Prefered Server");
			}
		}
		catch (SessionVanishedException svs)
		{
			throw svs;
		}
		
		catch (Exception e) 
		{
			logger.error("Problem updating the Prefered Server Name for Leadership Process: Name = " + this.processName + ", Sequence = " + this.sequence + ",id = " + id, e);
			throw new DAOException("Problem recording the Prefered Server Name - Process: Name = " + this.processName + ", Sequence = " + this.sequence + ",id = " + id, e);
		}
		finally 
		{
			ConnectionManager.close(preparedStatement, connection);
		}
	}
	
	
	
	/**
	 * There are really two dates that are being maintained.
	 * There is the system time stamp that is recored in the database and there is a internal time stamp that is held locally.  
	 * 
	 * Each time, the remote time stamp is updated, the local time stamp is likewise update.
	 * This is done so I can locally monitor my session time outs.
	 * 
	 * We are only updating a record that was created my me, meaning we look for a session that has my name, my server, and my sequence. 
	 * I have added the server so that it is impossible for me to update another server's session, even if it has the same sequence as I do. 
	 * 
	 * This was a problem when I created a session, but then was blocked from 30 seconds waiting from a database connection.  Once I experienced a 
	 * WaitTimeoutException, only 30 seconds had passed. Therefore, I requested a connection again and was again blocked.  However, 16 seconds later
	 * (totaling 46 second) the Oracle job removed my session from the database.  A few seconds later another server started and a leader with the same
	 * name created a session.  Since my session had been removed, the other serve obtained a session with the same sequence that I originally had.  
	 * About 5 seconds after that, I got a database connection and started to update the heart beat on my session. The trouble is that it was not my
	 * session.  It was the session created by the other server.  Now there are two servers updating the heart beat on the same session.  Both servers
	 * believe that everything is ok, but we have two leaders doing work in two separate servers at the same time.
	 * 
	 * This is why I am added the server to the heart beat.  No leader should be able to update the heart beat for a session that was created by another server. 
	 * This is a design defect that we are correcting.  RTC #203495
	 */
	protected void recordHeatBeat() throws DAOException, SessionVanishedException 
	{
			
		Preconditions.checkState(sessionEstablished,  "recordHeatBeat() - No session has yet been created");
		Connection connection = null;
		PreparedStatement preparedStatement = null;
		String UPDATE_SQL = "update LEADER_PROCESS set HEART_BEAT = SYSTIMESTAMP where LEADER_NAME = ? and  OWNING_SERVER = ? and SEQUENCE = ?";
		
		try 
		{
			connection = connectionBuilder == null ? ConnectionManager.getLeaderProcessConnection() : connectionBuilder.getLeaderProcessConnection();
			preparedStatement = connection.prepareStatement(UPDATE_SQL);
			preparedStatement.setString(1, this.processName);
			preparedStatement.setString(2, owningServer);
			preparedStatement.setInt(3, sequence);
			
			int retval = preparedStatement.executeUpdate();
			
			if (retval == 0)
			{
				logger.debug("recordHeatBeat() - " + processName + "-" + sequence + "-" + id +": Heatbeat could not be updated.  Session had been removed");
				throw new SessionVanishedException();
			}
			else
			{
				logger.debug("recordHeatBeat() - " + processName + "-" + sequence + "-" + id +": Updated the Heatbeat");
			}
			this.heatbeat = new Date();
		}
		catch (SessionVanishedException svs)
		{
			throw svs;
		}
		
		catch (Exception e) 
		{
			logger.error("Problem updating the Heat beat Leadership Process: Name = " + this.processName + ", Sequence = " + this.sequence + ",id = " + id, e);
			throw new DAOException("Problem recording Heat Beat - Process: Name = " + this.processName + ", Sequence = " + this.sequence + ",id = " + id, e);
		}
		finally 
		{
			ConnectionManager.close(preparedStatement, connection);
		}
	}
	
	
	
	
	
	
	
	/**
	 * Inserts a new Row in the LEADER_PROCESS table 
	 * Once this call is made, if the heart beat is not maintained, the session will be removed the the EPHEMERATOR in the database.
	 * @return the Sequence of the Leader Process 
	 */
	protected Integer createSession() throws DAOException
	{
		String INSERT_SQL = "insert into LEADER_PROCESS (LEADER_NAME, SEQUENCE, ID, OWNING_SERVER, PERFERED_SERVER, HEART_BEAT) values (?, ?, ?, ?, ?, SYSTIMESTAMP)";
		Connection connection = null;
		PreparedStatement preparedStatement = null;
		ResultSet resultSet = null;
		Integer sequence = null; 
		
		try 
		{
			connection = connectionBuilder == null ? ConnectionManager.getLeaderProcessConnection() : connectionBuilder.getLeaderProcessConnection();
			preparedStatement = connection.prepareStatement("select case when max(sequence) is null then 1 else (max(sequence) + 1) end \"SEQUENCE\"from leader_process where leader_name = ?");
			preparedStatement.setString(1, this.processName);
			resultSet = preparedStatement.executeQuery();
			if (resultSet.next())
			{
				sequence = resultSet.getInt("SEQUENCE");
			}
			else
			{
				throw new DAOException("Problem getting a sequence from the LEADER_PROCESS table");
			}
			ConnectionManager.close(preparedStatement);
			
			//-------------------------------------------------------------------------
			preparedStatement = connection.prepareStatement(INSERT_SQL);
			preparedStatement.setString(1, this.processName);
			preparedStatement.setInt(2, sequence);
			preparedStatement.setString(3, this.id);
			preparedStatement.setString(4, this.owningServer);
			preparedStatement.setString(5, this.preferredServer);
			preparedStatement.execute();
			
			
			logger.debug("createSession() - Process: " + processName + ", Seq: " + sequence + ", id: " + id + ", id: " + id + ", New Session Created");
			return sequence;
		} 
		
		catch (Throwable ex) 
		{
			if (ex.getMessage() != null && ex.getMessage().contains("ORA-00001") && ex.getMessage().contains("LEADER_PROCESS_PK")) 
			{
				logger.error("SessionManager.createSession() Duplicate Leader - Process: " + processName + ", Seq: " + sequence + ", id: " + id + ", id: " + id + ", throwing ConstraintViolationException");
				//two processes tryed to create themselves at the same time... try again.
				throw new ConstraintViolationException(ex.getMessage());
			}
			else
			{	
				logger.error("SessionManager.createSession() Error Inserting - Process: " + processName + ", Seq: " + sequence + ", id: " + id + ", id: " + id, ex);
				throw new DAOException("Problem inserting Session, ProcessName: " + processName + ", Seq: " + sequence + ", id: " + id + ", id: " + id + ", " + ex.getMessage());
			}	
		}
		finally 
		{
			ConnectionManager.close(resultSet, preparedStatement, connection);
			
		}
	}


	/**
	 * Gets the Id that was set
	 * @return The id
	 */
	public String getId() {
		return id;
	}
	
	
	
	
	
	
	
	

}
