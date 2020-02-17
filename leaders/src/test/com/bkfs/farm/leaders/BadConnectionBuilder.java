package com.bkfs.farm.leaders;

import java.sql.Connection;
import java.sql.SQLException;

import com.lps.mg.db.ConnectionBuilder;

/**
 * A replacement Connection. 
 * This will throw an SQLException after the provided timeout
 * Used for testing bad database connections. 
 */
public class BadConnectionBuilder implements ConnectionBuilder {
	
	private int timeout = -1;

	/**
	 * Constructor
	 * @param timeout How long to hold the connection before throwing exception. 
	 *                If < 100 then it is interpreted as seconds.  If 100 or more, it is interpreted as ms. 
	 */
	public BadConnectionBuilder(int timeout)
	{
		if (timeout != -1)
		{	
			if (timeout < 100)
			{
				timeout = timeout * 1000;
			}
		}	
		this.timeout = timeout;
	}	

	
	@Override
	public Connection getLeaderProcessConnection() throws SQLException {
		
		long start = System.currentTimeMillis();
		
		try 
		{
			if (timeout != -1)
			{	
			
				int i = 1;
				while ((start + timeout) > System.currentTimeMillis()) {
				
					System.out.println("BadConnection.  Stucking waiting...." + i++);
					Thread.sleep(1000);
				}
			}
			else
			{
				Thread.sleep(1000);
			}
		} 
		catch (Exception e)
		{
			e.printStackTrace();
		}
		throw new SQLException("Simulated... Lost Connection to server during query");
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
