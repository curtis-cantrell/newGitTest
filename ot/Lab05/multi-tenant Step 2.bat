@echo off
rem
echo Lab: Configuring the management gateway and creating the multi-tenant repository
rem
echo Step 2 - Create the connection profile to the multi-tenant OTDS
rem
cd C:\OpenText\Exstream\16.6\Server\bin
rem
ss_tenantadmin -action configure_multitenant_otds -otdsURL thecompany.com -otdsport 8443 -otdsusername exbrowser -otdspassword opentext -otdsresource 4af75d5a-8475-44c1-88ed-889fc6c83836 -otdsresourcepassword "21WnK+Rjx9dD2QkoBCve8g==" -output C:\ConnectionProfiles\multitenant_otds_profile.xml
rem
pause
