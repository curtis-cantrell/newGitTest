-- Runs SP_LEADER_PROCESS every 2 seconds to removed any sessions that are too old.

SET ECHO ON
SET SERVEROUTPUT ON SIZE unlimited
SPOOL &&ORASPOOL/Add_LEADER_PROCESS_Job.log

DECLARE 
	v_count NUMBER;


BEGIN  

	Select count(*) into v_count from user_scheduler_jobs WHERE job_name = 'LEADER_PROCESS_JOB';
  	IF (v_count > 0) THEN
		DBMS_SCHEDULER.DROP_JOB (
		job_name => 'LEADER_PROCESS_JOB');

	END IF;
	
	dbms_scheduler.create_job  (
	  job_name        => 'LEADER_PROCESS_JOB',  
	  job_type        => 'PLSQL_BLOCK',
	  job_action      => 'begin SP_LEADER_PROCESS; END;',
	  start_date      =>  SYSTIMESTAMP,
	  repeat_interval => 'SYSTIMESTAMP + INTERVAL ''2'' SECOND',
	  end_date        => NULL,
	  enabled         => TRUE,  
	  auto_drop       => FALSE,  
	  comments        => 'Job to Remove stall sessions every 2 second');  
	  
	  DBMS_OUTPUT.put_line ('LEADER_PROCESS_JOB created successfully!');
	  
	EXCEPTION
	WHEN OTHERS
	THEN
		DBMS_OUTPUT.put_line ('***Some Exception occurs on the Add_LEADER_PROCESS_Job!');
		DBMS_OUTPUT.put_line ('The Error Code is: ' || SQLCODE);
	 	DBMS_OUTPUT.put_line ('The Error Message is: ' || SQLERRM);
END; 
/
--Disable Scheduler JOB
--BEGIN
--dbms_scheduler.disable('LEADER_PROCESS_JOB', TRUE);
--END;
--/

--Enable Scheduler JOB
--BEGIN
--dbms_scheduler.enable('LEADER_PROCESS_JOB');
--END;
--/

-- Run the job immediate
--begin  
--dbms_scheduler.run_job('LEADER_PROCESS_JOB',TRUE);  
--end; 

--Stop the running job
--begin
--dbms_scheduler.stop_job ('LEADER_PROCESS_JOB', TRUE);
--end;

-- Drop the job
--BEGIN
--DBMS_SCHEDULER.DROP_JOB (
--job_name => 'LEADER_PROCESS_JOB');
--END;
--/


-- select job_name, run_count, next_run_date, state, last_run_duration  from user_scheduler_jobs;

-- OR 

-- select * from user_scheduler_jobs;  


SPOOL OFF	
SET ECHO OFF
