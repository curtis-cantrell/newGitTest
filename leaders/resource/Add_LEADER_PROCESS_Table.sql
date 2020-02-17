
-- This is the backing Table that Lead processes user to interact with the database. 

SET ECHO ON
SET SERVEROUTPUT ON SIZE unlimited
SPOOL &&ORASPOOL/Add_LEADER_PROCESS_Table.log

DECLARE
	v_object  NUMBER;

BEGIN
	
	
  Select count(*) into v_object from user_tables WHERE table_name = 'LEADER_PROCESS';
  
  IF (v_object = 0) THEN
	
    execute immediate 
    'CREATE TABLE &&SCHEMA_NAME..LEADER_PROCESS	(
    
	LEADER_NAME       VARCHAR2(255 BYTE) NOT NULL ENABLE,
    SEQUENCE          NUMBER NOT NULL ENABLE,
    ID                VARCHAR2(255 BYTE),
    OWNING_SERVER     VARCHAR2(255 BYTE) NOT NULL ENABLE,
    PERFERED_SERVER   VARCHAR2(255 BYTE),
    HEART_BEAT        TIMESTAMP (6) NOT NULL ENABLE,
    CONSTRAINT LEADER_PROCESS_PK PRIMARY KEY (LEADER_NAME, SEQUENCE) USING INDEX TABLESPACE &&INDEX_NAME ENABLE 

    ) TABLESPACE &&TABLESPACE_NAME';
   
    execute immediate 'COMMENT ON COLUMN &&SCHEMA_NAME..LEADER_PROCESS.LEADER_NAME IS ''The name of the Leader.  Each leader in a single group will have the same name''';
    execute immediate 'COMMENT ON COLUMN &&SCHEMA_NAME..LEADER_PROCESS.SEQUENCE IS ''The order in the leadership queue.  1 is the leader''';
    execute immediate 'COMMENT ON COLUMN &&SCHEMA_NAME..LEADER_PROCESS.ID IS ''An identifier that can be assigned to a particular leader ''';
    execute immediate 'COMMENT ON COLUMN &&SCHEMA_NAME..LEADER_PROCESS.OWNING_SERVER IS ''The server name that deployed this leader''';
    execute immediate 'COMMENT ON COLUMN &&SCHEMA_NAME..LEADER_PROCESS.PERFERED_SERVER IS ''The name of the server, if there is a preference which server it should lead on''';
    execute immediate 'COMMENT ON COLUMN &&SCHEMA_NAME..LEADER_PROCESS.HEART_BEAT IS ''A heart beat that keeps the session alive between the leader and the database''';
    
 
    DBMS_OUTPUT.put_line('LEADER_PROCESS table got created');	
ELSE
		DBMS_OUTPUT.put_line('LEADER_PROCESS table already exists');	
	       
 END IF;
  
EXCEPTION
	
		WHEN OTHERS
		THEN
			DBMS_OUTPUT.put_line ('***Some Exception occurs on the Add_LEADER_PROCESS_Table.sql script.***');
	  	DBMS_OUTPUT.put_line ('The Error Code is: ' || SQLCODE);
	  	DBMS_OUTPUT.put_line ('The Error Message is: ' || SQLERRM);
	   	ROLLBACK;

END;
/

SPOOL OFF	
SET ECHO OFF