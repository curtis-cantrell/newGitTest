package com.bkfs.farm.leaders;

public interface LeaderProcessListener extends SessionListener {

	
    /**
     * Called when your class has been granted leadership. 
     * 
     * This method should not return until you wish to release leadership. 
     *
     * @throws Exception any errors
     */
	public void takeLeadership() throws Exception;

}
