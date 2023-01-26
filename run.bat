echo off

REM set ConnectionList=OCIDemoQueue-localSolace
set ConnectionList=Synthetic1-localSolace,localSolace-Synthetic2
REM set ConnectionList=Synthetic1-Synthetic2
REM set ConnectionList=localSolace-OCIDemoQueue, OCIDemoQueue-localSolace

set localSolace.Type=Solace
set OCIDemoQueue.Type=OCIQ
set Synthetic1.Type=Synthetic
set Synthetic2.Type=Synthetic

set OCIDemoQueue.OCI_QUEUEID=ocid1.queue.oc1.iad.amaaaaaanlc5nbyasnl7zfi5athwbdnu2a7yfifsabiqntjnf5gnyhfpklza
set OCIDemoQueue.OCI_FINGERPRINT=XX
set OCIDemoQueue.OCI_TENNANT_ID=XX
set OCIDemoQueue.OCI_REGION=us-ashburn-1
set OCIDemoQueue.OCI_USERID=XX
set OCIDemoQueue.OCI_AUTHFILE=oci.properties


REM set localSolace.SOLACE_ADDR=127.0.0.1
set localSolace.SOLACE_ADDR=192.168.1.130
set localSolace.SOLACE_PORT=55555
set localSolace.solace.messaging.authentication.scheme=AUTHENTICATION_SCHEME_BASIC
set localSolace.solace.messaging.authentication.basic.username=admin
set localSolace.solace.messaging.authentication.basic.password=admin
set localSolace.SOLACE_TOPICNAME=test123
set localSolace.SOLACE_VPN=default

echo %1
if [%1]==[reset] goto :reset
if [%1]==[build] goto :build
if [%1]==[run] goto :run
if [%1]==[compile] goto :compile

:reset
REM reset all the environment variables
set ConnectionList=

set localSolace.Type=
set OCIDemoQueue.Type=

set OCIDemoQueue.OCI_QUEUEID=
set OCIDemoQueue.OCI_FINGERPRINT=
set OCIDemoQueue.OCI_TENNANT_ID=
set OCIDemoQueue.OCI_REGION=
set OCIDemoQueue.OCI_USERID=
set OCIDemoQueue.OCI_AUTHFILE=


set localSolace_SOLACE_ADDR=
set localSolace.SOLACE_PORT=
set localSolace_solace.messaging.authentication.basic.username=
set localSolace_solace.messaging.authentication.basic.password=
set localSolace_SOLACE_TOPICNAME=

goto :eof

:compile
call mvn compile
goto :eof

:build
echo building ...
call mvn package
call mvn assembly:assembly -DdescriptorId=jar-with-dependencies 

:run
java -jar target/oci-solace-1.0-SNAPSHOT-jar-with-dependencies.jar

:eof
