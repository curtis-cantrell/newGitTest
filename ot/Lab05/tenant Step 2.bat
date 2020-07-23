@echo off
rem
echo Lab: Adding a tenant
rem
echo Step 2 - Create the connection profile to the tenant OTDS
rem
cd C:\OpenText\Exstream\16.6\Server\bin
rem
ss_tenantadmin -action configure_tenant_otds -otdsURL thecompany.com/otdstenant/tenant1 -otdsport 8443 -otdsusername exbrowser -otdspassword opentext -otdsresource 3b30c0a3-e9c9-4540-9cfb-424fcdca12da -otdsresourcepassword "5eAxV+HX79ukGwSFDDMJvg==" -output "C:\ConnectionProfiles\tenant_otds_profile.xml"
rem
pause
