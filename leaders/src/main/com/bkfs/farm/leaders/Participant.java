package com.bkfs.farm.leaders;

import com.google.common.base.Objects;

/**
 * This Class is a Participate in a Leadership Group 
 */
public class Participant {

	
	public String processName;
	public Integer sequence;
	
	
	public String id;
	public String ownerServer;
	public String preferedServer;
	
	private boolean isLeader;

	
	public Participant(String processName, Integer sequence)
	{
		this.processName = processName;
		this.sequence = sequence;
	}
	
	
	/**
	 * Constructor 
	 * @param id The id of this Participant
	 * @param isLeader true if this is the current leader
	 */
	public Participant(String processName, Integer sequence, String id, String ownerServer, String preferedServer, boolean isLeader)
	{
		this(processName, sequence);
		this.id = id;
		this.ownerServer = ownerServer;
		this.preferedServer = preferedServer;
		this.isLeader = isLeader;
	}

	
	public String getId() {
		return id;
	}

	public boolean isLeader() {
		return isLeader;
	}
	
	
	public String getProcessName() {
		return processName;
	}


	public Integer getSequence() {
		return sequence;
	}


	public String getOwnerServer() {
		return ownerServer;
	}


	public String getPreferedServer() {
		return preferedServer;
	}


	@Override
	public boolean equals(Object o) 
	{
		// self check
	    if (this == o)
	        return true;
	    // null check
	    if (o == null)
	        return false;
	    // type check and cast
	    if (getClass() != o.getClass())
	        return false;
	    Participant otherLeader  = (Participant) o;
	    // field comparison
	    return Objects.equal(processName, otherLeader.processName) && Objects.equal(sequence, otherLeader.sequence);
	}
	
	@Override
	public String toString() 
	{
		return new StringBuilder()
					.append("Particpate[name=").append(processName)
					.append(",sequence=").append(sequence)
					.append(",id=").append(id)
					.append(",ownerServer=").append(ownerServer)
					.append(",preferedServer=").append(preferedServer)
					.append(",isLeader=").append(isLeader)
					.append("]").toString();
	}
}

