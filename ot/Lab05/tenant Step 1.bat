@echo off
rem
echo Lab: Adding a tenant
rem
echo Step 1 - Create the connection profile to the tenant repository
rem
cd C:\OpenText\Exstream\16.6\Server\bin
rem
ss_tenantadmin -action configure_tenant_repository -dbhost server -dbport 1433 -dbvendor sqlserver -dbname DB_DEVTenant -dbusername DEVTenantUsr -dbpassword opentext -output C:\ConnectionProfiles\tenant_repository_profile.xml"
rem
pause
