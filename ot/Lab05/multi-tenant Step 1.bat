@echo off
rem
echo Lab: Configuring the management gateway and creating the multi-tenant repository
rem
echo Step 1 - Create the connection profile to the multi-tenant repository
rem
md C:\ConnectionProfiles
rem
cd C:\OpenText\Exstream\16.6\Server\bin
rem
ss_tenantadmin -action configure_multitenant_repository -dbhost thecompany.com -dbport 1433 -dbvendor sqlserver -dbname DB_MultiTenant -dbusername MultiTenUsr -dbpassword opentext -output "C:\ConnectionProfiles\multitenant_repository_profile.xml"
rem
pause
