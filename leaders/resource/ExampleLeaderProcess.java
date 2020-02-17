import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.bkfs.farm.leaders.CloseableExecutorService;
import com.bkfs.farm.leaders.DefaultSessionListener;
import com.bkfs.farm.leaders.LeaderProcess;
import com.bkfs.farm.leaders.SessionTimeConfiguration;
import com.bkfs.farm.leaders.ThreadUtils;



public class ExampleLeaderProcess extends DefaultSessionListener {
	
	
	private static Logger logger = Logger.getLogger(ExampleLeaderProcess.class);

	/** The leadership Process */
	private LeaderProcess leaderProcess = null;
	
	
	/** flag to indicate the leader has been closed */
	boolean isClosed = false;
	
	/** The thread that invoked takeLeadership */ 
	Thread leadershipThread = null;
	
	/** Flag to indicated there is a session with the database right now */
	private boolean hasGoodSession = false;
	
	
	/** A named Thread Factory so I can name the Threads */
	private static final ThreadFactory threadFactory = ThreadUtils.newThreadFactory("ExampleLeaderProcess");
		

	/**
	 * Empty Constructor 
	 */
	public ExampleLeaderProcess() {  }
	

	/**
	 * Called to Start this QueueMonitor 
	 * @throws Exception if there is a timeout connecting
	 */
	public void start() 
	{
		try {
			
			String myServer = System.getProperty("server.name");
			
			//this will allow me to name the Threads
			CloseableExecutorService executor = new CloseableExecutorService(Executors.newSingleThreadExecutor(threadFactory), true);

			//provide an application specifice SessionTimeConfiguation 
			SessionTimeConfiguration timeConfig = new SessionTimeConfiguration() {
				
				@Override
				public int getTickTime() {
					return 2;
				}
				
				@Override
				public int getSessionTimeout() {
					return 45;
				}
			}; 
					
			leaderProcess = new LeaderProcess(timeConfig, "ExampleLeaderProcess", null, myServer, null, executor, this);
			leaderProcess.autoRequeue();
			leaderProcess.start();
			
		} catch (Throwable t) {
			throw new RuntimeException("Problem Starting ExampleLeaderProcess", t);
		}
	}
	
	
	@Override
	public void sessionExtablished() 
	{
		logger.debug("SESSION ESTABLISHED");
		hasGoodSession = true;
		super.sessionExtablished();
		
	}
	
	
	@Override
	public void sessionInvalidated() 
	{
		
		hasGoodSession = false;
		if (leadershipThread != null) {
			leadershipThread.interrupt();      
		}
		super.sessionInvalidated();
	}
	
	
	/**
	 * The method that is called when I get Leadership.  
	 */
	@Override
	public void takeLeadership() throws Exception 
	{
		logger.debug("Recieved Leadership");
		
		leadershipThread = Thread.currentThread();
		
		while (hasGoodSession && !this.isClosed) 
		{
			try 
			{
				
				//do the job that you need to be done right here....
				//If you ever want to release leadership, just return from this method.
				
				//you should put some pause here between doing the job aganin. 
				leadershipThread.sleep(1000);
				
			} catch (InterruptedException ie) 
			{
				//the leadership thread was interupted.. so let go of the loop
			} 
			catch (Exception e) 
			{
				logger.error("Problem while Leading!", e);
				throw e;
			}
		}
		logger.debug("Relinquished Leadership");
	}
	
	
	
	
	/**
	 * Should be called when the server is being shut down or the J2EE applicaiton is being removed.
	 */
	public void close() 
	{
		//let the leadership loop know that we are closed. 
		this.isClosed = true;     
			
		//interupt the leadership thread so it will wake up if it is sleeping. 
		if (leadershipThread != null) 
		{
			leadershipThread.interrupt();      
		}
		
		//close the leaderSelector
		try 
		{
			leaderProcess.close();
		} catch (Exception e) {}
		
		leaderProcess = null;
	}
	
}
