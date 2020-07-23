@echo off
rem
echo Secure the SGW
rem
echo Step 6 - Copy the following file to the indicated location:
echo C:\OpenText\Exstream\16.6\Server\solutions\management\web\cc-webapp-config.xml to C:\Tomcats\OTDS\conf
echo C:\OpenText\Exstream\16.6\Server\solutions\management\web\cc-webapp-config.xsd to C:\Tomcats\OTDS\conf
rem
copy C:\OpenText\Exstream\16.6\Server\solutions\management\web\cc-webapp-config.xml C:\Tomcats\OTDS\conf
copy C:\OpenText\Exstream\16.6\Server\solutions\management\web\cc-webapp-config.xsd C:\Tomcats\OTDS\conf
rem
pause
