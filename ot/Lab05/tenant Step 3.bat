@echo off
rem
echo Lab: Adding a tenant
rem
echo Step 3 - Create a tenant repository
rem
cd C:\OpenText\Exstream\16.6\Server\bin
rem
ss_tenantadmin -action create_tenant_repository -mtauser exadmin -mtapassword opentext -tenantdbprofile "C:\ConnectionProfiles\tenant_repository_profile.xml" -dbadminusername sa -dbadminpassword opentext
rem
pause
