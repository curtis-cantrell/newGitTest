@echo off
color 7


REM ******** SET THE Tenant OTD and tenant1 resource IDs and secret keys below ***********

SET OTDSRESOURCE=
SET OTDSRESOURCEKEY=

SET TENANTRESOURCE=
SET TENANTRESOURCEKEY=

rem SET OTDSRESOURCE= SET OTDSRESOURCEKEY= SET TENANTRESOURCE=   SET TENANTRESOURCEKEY=


if "%OTDSRESOURCE%"=="" goto OTDSRESOURCE_missing
if "%OTDSRESOURCEKEY%"=="" goto OTDSRESOURCEKEY_missing
if "%TENANTRESOURCE%"=="" goto TENANTRESOURCE_missing
if "%TENANTRESOURCEKEY%"=="" goto TENANTRESOURCEKEY_missing


cls
set _ServiceName="StreamServe Management Gateway 16.6"

sc query %_ServiceName% | find "does not exist" >nul
if %ERRORLEVEL% == 0 goto service_error
if %ERRORLEVEL% == 1 goto process_continue

:process_continue
echo.
echo.
IF EXIST "C:\ConnectionProfiles" (
	echo **** Clearing existing C:\ConnectionProfiles directory
	rd C:\ConnectionProfiles /s /q
	echo ...... done!
	)
echo.
echo **** Creating C:\ConnectionProfiles directory
md "C:\ConnectionProfiles"
echo ...... done!
echo.
echo.

cd C:\OpenText\Exstream\16.6\Server\bin


echo Running command (1): 
echo ss_tenantadmin -action configure_multitenant_repository -dbhost server -dbport 1433 -dbvendor sqlserver -dbname DB_MultiTenant -dbusername MultiTenUsr -dbpassword opentext -output C:\ConnectionProfiles\multitenant_repository_profile.xml
ss_tenantadmin -action configure_multitenant_repository -dbhost server -dbport 1433 -dbvendor sqlserver -dbname DB_MultiTenant -dbusername MultiTenUsr -dbpassword opentext -output C:\ConnectionProfiles\multitenant_repository_profile.xml
echo.
echo ****** REVIEW RESULTS FROM PREVIOUS COMMAND AND MAKE SURE THERE IS NO ERROR *****
echo.
pause
echo ================================================================
echo.
echo Running command (2): 
echo ss_tenantadmin -action configure_multitenant_otds -otdsURL thecompany.com -otdsport 8443 -otdsusername exbrowser -otdspassword opentext -otdsresource %OTDSRESOURCE% -otdsresourcepassword %OTDSRESOURCEKEY% -output C:\ConnectionProfiles\multitenant_otds_profile.xml 
ss_tenantadmin -action configure_multitenant_otds -otdsURL thecompany.com -otdsport 8443 -otdsusername exbrowser -otdspassword opentext -otdsresource %OTDSRESOURCE% -otdsresourcepassword %OTDSRESOURCEKEY% -output C:\ConnectionProfiles\multitenant_otds_profile.xml 
echo.
echo ****** REVIEW RESULTS FROM PREVIOUS COMMAND AND MAKE SURE THERE IS NO ERROR *****
echo.
pause
echo ================================================================

echo.
echo Running command (3): 
echo ss_tenantadmin -action create_multitenant_repository -multitenantdbprofile C:\ConnectionProfiles\multitenant_repository_profile.xml -dbadminusername sa -dbadminpassword opentext
ss_tenantadmin -action create_multitenant_repository -multitenantdbprofile C:\ConnectionProfiles\multitenant_repository_profile.xml -dbadminusername sa -dbadminpassword opentext
echo.
echo ****** REVIEW RESULTS FROM PREVIOUS COMMAND AND MAKE SURE THERE IS NO ERROR *****
echo.
pause
echo ================================================================

echo.
echo Running command (4): 
echo ss_tenantadmin -action configure_mgw -multitenantdbprofile C:\ConnectionProfiles\multitenant_repository_profile.xml -multitenantotdsprofile C:\ConnectionProfiles\multitenant_otds_profile.xml
ss_tenantadmin -action configure_mgw -multitenantdbprofile C:\ConnectionProfiles\multitenant_repository_profile.xml -multitenantotdsprofile C:\ConnectionProfiles\multitenant_otds_profile.xml
echo.
echo ****** REVIEW RESULTS FROM PREVIOUS COMMAND AND MAKE SURE THERE IS NO ERROR *****
echo.
echo *********************************************************
echo *********************************************************
echo **** STOP HERE, DO NOT PRESS ANY KEY ON THE KEYBOARD ****
echo *********************************************************
echo *********************************************************
echo ******                                              *****
echo ******     START THE FOLLOWING WINDOWS SERVICE      *****
echo ******                                              *****
echo ******   StreamServe Management Gateway 16.6        *****
echo ******                                              *****
echo ******                                              *****
echo ******   AFTER SERVICE IS STARTED CONTINUE HERE     *****
echo ******                                              *****
echo *********************************************************
echo *********************************************************
echo.
echo.
pause
echo ================================================================

echo.
echo Running command (5): 
echo ss_tenantadmin -action configure_tenant_repository -dbhost server -dbport 1433 -dbvendor sqlserver -dbname DB_DEVTenant -dbusername DEVTenantUsr -dbpassword opentext -output C:\ConnectionProfiles\tenant_repository_profile.xml
ss_tenantadmin -action configure_tenant_repository -dbhost server -dbport 1433 -dbvendor sqlserver -dbname DB_DEVTenant -dbusername DEVTenantUsr -dbpassword opentext -output C:\ConnectionProfiles\tenant_repository_profile.xml
echo.
echo ****** REVIEW RESULTS FROM PREVIOUS COMMAND AND MAKE SURE THERE IS NO ERROR *****
echo.
pause
echo ================================================================

echo.
echo Running command (6): 
echo ss_tenantadmin -action configure_tenant_otds -otdsURL thecompany.com/otdstenant/tenant1 -otdsport 8443 -otdsusername exbrowser -otdspassword opentext -otdsresource %TENANTRESOURCE% -otdsresourcepassword %TENANTRESOURCEKEY% -output C:\ConnectionProfiles\tenant_otds_profile.xml
ss_tenantadmin -action configure_tenant_otds -otdsURL thecompany.com/otdstenant/tenant1 -otdsport 8443 -otdsusername exbrowser -otdspassword opentext -otdsresource %TENANTRESOURCE% -otdsresourcepassword %TENANTRESOURCEKEY% -output C:\ConnectionProfiles\tenant_otds_profile.xml
echo.
echo ****** REVIEW RESULTS FROM PREVIOUS COMMAND AND MAKE SURE THERE IS NO ERROR *****
echo.
pause
echo ================================================================

echo.
echo Running command (7): 
echo ss_tenantadmin -action create_tenant_repository -mtauser exadmin -mtapassword opentext -tenantdbprofile C:\ConnectionProfiles\tenant_repository_profile.xml -dbadminusername sa -dbadminpassword opentext
ss_tenantadmin -action create_tenant_repository -mtauser exadmin -mtapassword opentext -tenantdbprofile C:\ConnectionProfiles\tenant_repository_profile.xml -dbadminusername sa -dbadminpassword opentext
echo.
echo ****** REVIEW RESULTS FROM PREVIOUS COMMAND AND MAKE SURE THERE IS NO ERROR *****
echo.
pause
echo ================================================================

echo.
echo Running command (8): 
echo ss_tenantadmin -action create_tenant -mtauser exadmin -mtapassword opentext -tenantdbprofile "C:\ConnectionProfiles\tenant_repository_profile.xml" -tenantotdsprofile "C:\ConnectionProfiles\tenant_otds_profile.xml" -tenantname tenant1
ss_tenantadmin -action create_tenant -mtauser exadmin -mtapassword opentext -tenantdbprofile C:\ConnectionProfiles\tenant_repository_profile.xml -tenantotdsprofile C:\ConnectionProfiles\tenant_otds_profile.xml -tenantname tenant1
echo.
echo ****** REVIEW RESULTS FROM PREVIOUS COMMAND AND MAKE SURE THERE IS NO ERROR *****
echo.
pause
echo ================================================================
pause
goto success


:service_error
cls
echo.
echo.
echo ERROR: There is an error in the installation, the service %_ServiceName% does not exist. 
echo        The process cannot continue. 
echo        Check Management Gateway installation.
goto end

:success
cls
echo.
echo The tenant configuration is completed.
goto end


:OTDSRESOURCE_missing
cls
echo.
echo ERROR: OTDSRESOURCE value missing.
echo        Set the value for the OTDSRESOURCE parameter at the beginning of this file and try again.
goto end

:OTDSRESOURCEKEY_missing
cls
echo.
echo ERROR: OTDSRESOURCEKEY value missing.
echo        Set the value for the OTDSRESOURCEKEY parameter at the beginning of this file and try again.
goto end

:TENANTRESOURCE_missing
cls
echo.
echo ERROR: TENANTRESOURCE value missing.
echo        Set the value for the TENANTRESOURCE parameter at the beginning of this file and try again.
goto end

:TENANTRESOURCEKEY_missing
cls
echo.
echo ERROR: TENANTRESOURCEKEY value missing.
echo        Set the value for the TENANTRESOURCEKEY parameter at the beginning of this file and try again.
goto end


:end







rem ss_tenantadmin -action configure_multitenant_repository -dbhost thecompany.com -dbport 1433 -dbvendor sqlserver -dbname DB_MultiTenant -dbusername MultiTenUsr -dbpassword opentext -output C:\ConnectionProfiles\multitenant_repository_profile.xml
rem ss_tenantadmin -action configure_multitenant_otds -otdsURL thecompany.com -otdsport 8443 -otdsusername exbrowser -otdspassword opentext -otdsresource a7de289b-3eaf-425d-9998-1cb8de3c13bc -otdsresourcepassword s7kaZh+NAMNe6YdiJRY+yQ== -output C:\ConnectionProfiles\multitenant_otds_profile.xml 
rem ss_tenantadmin -action create_multitenant_repository -multitenantdbprofile C:\ConnectionProfiles\multitenant_repository_profile.xml -dbadminusername sa -dbadminpassword opentext
rem ss_tenantadmin -action configure_mgw -multitenantdbprofile C:\ConnectionProfiles\multitenant_repository_profile.xml -multitenantotdsprofile C:\ConnectionProfiles\multitenant_otds_profile.xml

rem ss_tenantadmin -action configure_tenant_repository -dbhost thecompany.com -dbport 1433 -dbvendor sqlserver -dbname DB_DEVTenant -dbusername DEVTenantUsr -dbpassword opentext -output C:\ConnectionProfiles\tenant_repository_profile.xml
rem ss_tenantadmin -action configure_tenant_otds -otdsURL thecompany.com/otdstenant/tenant1 -otdsport 8443 -otdsusername exbrowser -otdspassword opentext -otdsresource 00b1b446-ab51-4249-9064-ecd20923841e -otdsresourcepassword jVr+9XfJCsyZYf/Sgu0qsg== -output C:\ConnectionProfiles\tenant_otds_profile.xml
rem ss_tenantadmin -action create_tenant_repository -mtauser exadmin -mtapassword opentext -tenantdbprofile C:\ConnectionProfiles\tenant_repository_profile.xml -dbadminusername sa -dbadminpassword opentext
rem ss_tenantadmin -action create_tenant -mtauser exadmin -mtapassword opentext -tenantdbprofile C:\ConnectionProfiles\tenant_repository_profile.xml -tenantotdsprofile C:\ConnectionProfiles\tenant_otds_profile.xml -tenantname tenant1

