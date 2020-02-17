package com.bkfs.farm.leaders;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.base.Preconditions;
import com.lps.mg.db.DAOException;
import com.lps.mg.log.DeferLogger;


/**
 * LeaderProcess is a class that will select a Leader from among multiple contenders in a group of leaders connected to the same database. 
 * 
 * If a group of N processes contends for leadership, one will be assigned the leader until it releases leadership.  
 * 
 * Once it releases leadership, another one from the group will be chosen as the leader. 
 * 
 * Note that this class uses an underlying {@link SessionManager}
 */
public class LeaderProcess implements Closeable {
	
	
	private static DeferLogger 	logger   = DeferLogger.getLogger(LeaderProcess.class);
	
	private SessionManager sessionManager;
	
	private LeaderProcessListener listener;
	
	private CloseableExecutorService executorService;
	
	private final AtomicReference<State> state = new AtomicReference<State>(State.LATENT);
	
	private final AtomicBoolean autoRequeue = new AtomicBoolean(false);
	
	private final AtomicReference<Future<?>> leadershipTask = new AtomicReference<Future<?>>(null);
	
	private volatile boolean hasLeadership;
	
    private boolean isQueued = false;
	
    private static final ThreadFactory defaultThreadFactory = ThreadUtils.newThreadFactory("LeaderProcess");
	
	private enum State
	 {
		 LATENT,
	     STARTED,
	     CLOSED
	 } 
	
	 
	
	
	     
	 /**
	  * Constructor
	  * 
	  * @param timeConfiguration The implementation that provides session timeouts. 
	  * @param processName  The name of the Process.  This name must be the same for every process that is contending for leadership amoung the group. 
	  * @param id   An optional id that can be used to identify this particular participant 
	  * @param owningServer  The server that that is hosting this process 
	  * @param preferedServer An optional preferred  server where the process should be the leader. 
	  * @param listener The listener that will listen for session and leadership events. 
	  */
	 public LeaderProcess(SessionTimeConfiguration timeConfiguration, String processName, String id, String owningServer, String preferedServer, LeaderProcessListener leaderProcessListener)
	 {
		 Preconditions.checkNotNull(processName, "The process name cannot be null");
		 Preconditions.checkNotNull(owningServer, "The owning Server name cannot be null");
		 Preconditions.checkNotNull(leaderProcessListener, "The listener cannot be null");
		 
		 this.listener = new WrappedListener(this, leaderProcessListener);
		 this.sessionManager = new SessionManager(timeConfiguration, processName, id, owningServer, preferedServer);
		 this.sessionManager.addListener((SessionListener)listener);
		 
		 this.executorService = new CloseableExecutorService(Executors.newSingleThreadExecutor(defaultThreadFactory), true);
		 
		 hasLeadership = false;
	 }	
	 
	 
	 
	 /**
	  * Constructor
	  * 
	  * @param timeConfiguration The implementation that provides session timeouts. 
	  * @param processName  The name of the Process.  This name must be the same for every process that is contending for leadership amoung the group. 
	  * @param id   An id that can be used to identify this particular participant 
	  * @param owningServer  The server that that is hosting this process 
	  * @param preferedServer An optional preferred  server where the process should be the leader.
	  * @param executorService The executor server to use to execute the internal task. 
	  * @param listener The listener that will listen for session and leadership events. 
	  */
	
	 public LeaderProcess(SessionTimeConfiguration timeConfiguration, String processName, String id, String owningServer, String preferedServer, CloseableExecutorService executorService, LeaderProcessListener listener)
	 {
		 
		 Preconditions.checkNotNull(processName, "The process name cannot be null");
		 Preconditions.checkNotNull(owningServer, "The owning Server name cannot be null");
		 Preconditions.checkNotNull(listener, "The listener cannot be null");
		 
		 this.listener = new WrappedListener(this, listener);
		 this.sessionManager = new SessionManager(timeConfiguration, processName, id, owningServer, preferedServer);
		 this.sessionManager.addListener(this.listener);
		 
		 this.executorService = executorService;
		 
		 hasLeadership = false;
	 }
	 

	 
	 /**
	  * Adds a Session Listener so that changes to the Session state can be handled
	  * @param listener The session Listener
	  */
	 public void addListener(SessionListener sessionListener) 
	 {
	 	sessionManager.addListener(sessionListener);
	 }
		 
	
	/**
	  * By default, when {@link LeaderProcessListener#takeLeadership} returns, the leader will not be auto requeued. 
	  * Calling this method puts the leader process into a mode where it will always requeue itself.
	  */
	public void autoRequeue()
	{
		autoRequeue.set(true);
	}
	
	/**
     * Return the ID that was set in the Consturctor
     * @return The id
     */
    public String getId()
    {
        return sessionManager.getId();
    }
    
    /**
	 * Updates the Preferred Server Name for this Leader Process
	 * @param preferedServer The new name 
	 */
	public void setPerferredServer(String preferredServer)
	{
		sessionManager.setPerferredServer(preferredServer);
	}
    
    
    /**
     * Attempt to acquire leadership. This attempt is done in the background - i.e. this method returns immediately.<br><br>
     */
    public void start()
    {
        Preconditions.checkState(state.compareAndSet(State.LATENT, State.STARTED), "Cannot be started more than once");
        Preconditions.checkState(!executorService.isShutdown(), "Already started");
        Preconditions.checkState(!hasLeadership, "Already has leadership");
        requeue();
    }
    
    
    /**
     * Requeue an attempt for leadership.  If this instance is already queued, nothing happens and false is returned. 
     * If the instance was not queued, it is reqeued and true is returned. 
     *
     * @return true if requeue is successful
     */
    public boolean requeue()
    {
        Preconditions.checkState(state.get() == State.STARTED, "Should have been Started Already before Requeing");
        return internalRequeue();
    }
    
    /**
     * If I am already queued, this does nothing.
     * @return true if I was queued, if I was already queued, returns false. 
     */
    private synchronized boolean internalRequeue()
    {
    	logger.debug("internalRequeue() Invoked");
        if ( !isQueued && (state.get() == State.STARTED) )
        {
        	
            isQueued = true;
            Future<Void> task = executorService.submit(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    try
                    {
                        doWorkLoop();   
                    }
                    finally
                    {
                        clearIsQueued();  
                        
                        if ( autoRequeue.get() )
                        {
                            internalRequeue();
                        }
                    }
                    return null;
                }
            });
            leadershipTask.set(task);
            logger.debug("internalRequeue() - A New InQueue");
            return true;
        }
        else
        {	
        	logger.debug("internalRequeue() - Already Queued");
        	return false;
        }	
    }
    
    /**
     * Shutdown this Leadership Process and remove yourself from the Leadership Group
     */
	@Override
	public void close() throws IOException {
		
		logger.debug("Closing the Leader Process");
        Preconditions.checkState(state.compareAndSet(State.STARTED, State.CLOSED), "Already closed or has not been started");
        
        executorService.close();
        leadershipTask.set(null);
        sessionManager.releaseLeadership(); 
	}
	
	
	
	/**
     * Returns the set of current participants in the leadership group
     *
     * @return participants The list of Participants or an empty list if there are none
     * @throws Exception if there a problems
     */
    public List<Participant> getParticipants() throws Exception
    {
        return sessionManager.getParticipants();
    }
    
    
    /**
     * Return the current leader. If for some reason there is no current leader, null is returned. 
     *
     * @return leader The Leader or null if there is no leaders 
     * @throws Exception if there was problems getting the Participants
     */
    public Participant getLeader() throws Exception
    {
        List<Participant> participants = sessionManager.getParticipants();
        if (participants.size() > 0)
        {
        	return participants.get(0);
        }
        else
        {
        	return null;
        }
    }
	
    
    /**
     * Am I currently the leader?
     * @return true/false
     */
    public boolean hasLeadership()
    {
        return hasLeadership;
    }
    
    
    private synchronized void clearIsQueued()
    {
        isQueued = false;
    }
    
    
	/**
     * Attempt to cancel the current leadership if this instance has leadership
     */
    private synchronized void cancelLeadership()
    {
    	logger.debug("CancelLeadership Called so I am going to cancel the leadership task");
        Future<?> task = leadershipTask.get();
        if ( task != null )
        {
            task.cancel(true);
        }
    }
    
    
    private void doWorkLoop() throws Exception
    {
    	logger.debug("doWorkLoop called..");
        Exception exception = null;
        try
        {
            doWork();
        }
        catch (DAOException daoe)
        {
        	logger.debug("doWorkLoop - got DAOException " + daoe.getMessage());
        	exception = daoe;
        }
        catch (SessionVanishedException sve)
        {
        	logger.debug("doWorkLoop - got SessionVanishedException " + sve.getMessage());
        	exception = sve;
        }
        catch ( InterruptedException ie )
        {
        	logger.debug("doWorkLoop - got InterrupedException " + ie.getMessage());
            Thread.currentThread().interrupt();
        }
        
        
        if ( (exception != null) && !autoRequeue.get() )   // autoRequeue should ignore interuption when connection loss or session expired and just keep trying
        {
        	logger.debug("GOING TO THROWS ... " + exception.getClass().getName());
            throw exception;
        } 
        
    } 
    
    
    
    
    protected void doWork() throws Exception
    {
        hasLeadership = false;
        try
        {
            sessionManager.acquireLeadership();

            hasLeadership = true;
            try
            {
            	//the client will block inside this method as long as they want to maintain leadership
            	listener.takeLeadership();
            	
            }
            catch ( InterruptedException e )
            {
                Thread.currentThread().interrupt();
                throw e;
            }
            catch ( Throwable e )
            {
            	logger.error("Caught Throwable in doWork from client's takeLeadership()",e);
                ThreadUtils.checkInterrupted(e);
            }
            finally
            {
               clearIsQueued();  
            }
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw e;
        }
        catch (Exception e)
        {
        	logger.debug("doWork() caught Exception when trying to acquire Leadership ",e);
        }
        finally
        {
            hasLeadership = false;
            try
            {
                sessionManager.releaseLeadership();
            }
            catch ( Exception e )
            {
                ThreadUtils.checkInterrupted(e);
                
                if (e instanceof IllegalStateException)
                {
                	if (e.getMessage().contains("No session has yet been created"))
                	{
                		logger.debug("The Manager's Session has already been removed");
                	}
                }
                else
                {	
                	logger.error("The Session Manager threw an exception while releasing leadership", e);
                }	
            }
        }
    }
    
    
	
	/**
	 * A Listener that wraps the client's listener so that I can listen for CancelLeadershipExceptions being issued. 
	 * 
	 * When a CancelLeadereshipException is caught, this listen will interrupt the Leadership. 
	 */
	private static class WrappedListener implements LeaderProcessListener	{
		private static DeferLogger 	logger   = DeferLogger.getLogger(WrappedListener.class);
        private final LeaderProcess leaderProcesss;
        private final LeaderProcessListener listener;

        public WrappedListener(LeaderProcess leaderProcesss, LeaderProcessListener listener)
        {
            this.leaderProcesss = leaderProcesss;
            this.listener = listener;
        }

        @Override
        public void sessionExtablished() 
        {
        	listener.sessionExtablished();
        }
        
        @Override
        public void sessionInvalidated() 
        {
        	logger.debug("Session Invalidate Called.  Delegating to external Listener");
        	try
            {
                listener.sessionInvalidated();
            }
            catch ( CancelLeadershipException cle )
            {
            	leaderProcesss.cancelLeadership();
            }
        }
        
        @Override
        public void takeLeadership() throws Exception 
        {
        	logger.debug("Take Leadership Called.. Delegating to external Listener");
        	listener.takeLeadership();
        }
    }

}
