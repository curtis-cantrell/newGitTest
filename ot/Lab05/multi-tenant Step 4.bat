@echo off
rem
echo Lab: Configuring the management gateway and creating the multi-tenant repository
rem
echo Step 4 - Configure the management gateway to use the connection profiles
rem
cd C:\OpenText\Exstream\16.6\Server\bin
rem
ss_tenantadmin -action configure_mgw -multitenantdbprofile "C:\ConnectionProfiles\multitenant_repository_profile.xml" -multitenantotdsprofile "C:\ConnectionProfiles\multitenant_otds_profile.xml"
rem
pause
