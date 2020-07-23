@echo off
rem
echo Lab: Configuring the management gateway and creating the multi-tenant repository
rem
echo Step 3 - Create the multi-tenant repository
rem
cd C:\OpenText\Exstream\16.6\Server\bin
rem
ss_tenantadmin -action create_multitenant_repository -multitenantdbprofile "C:\ConnectionProfiles\multitenant_repository_profile.xml" -dbadminusername sa -dbadminpassword opentext
rem
pause
