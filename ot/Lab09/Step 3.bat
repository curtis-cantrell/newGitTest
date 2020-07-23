@echo off
rem
echo Enable the SGW secure communications
rem
echo Step 3 - Copy the following file to the indicated location:
echo C:\Training\Resources\Tomcat\training.cer to C:\OpenText\Exstream\16.6\Server\global\security\certificatestore\trusted\authorities
rem
copy C:\Training\Resources\Tomcat\training.cer C:\OpenText\Exstream\16.6\Server\global\security\certificatestore\trusted\authorities
rem
pause
