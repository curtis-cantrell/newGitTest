@echo off
rem
echo Lab: Adding a tenant
rem
echo Step 4 - Create a tenant that uses the connection profiles
rem
cd C:\OpenText\Exstream\16.6\Server\bin
rem
ss_tenantadmin -action create_tenant -mtauser exadmin -mtapassword opentext -tenantdbprofile "C:\ConnectionProfiles\tenant_repository_profile.xml" -tenantotdsprofile "C:\ConnectionProfiles\tenant_otds_profile.xml" -tenantname tenant1
rem
pause
