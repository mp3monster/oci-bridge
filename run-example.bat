echo off
cls

set isMultiPass=true
set ConnectionList=Synthetic1-cloudSolace
REM set ConnectionList=OCIDemoQueue-localSolace
REM set ConnectionList=Synthetic1-localSolace,localSolace-Synthetic2
REM set ConnectionList=Synthetic1-localSolace
REM set ConnectionList=Synthetic1-Synthetic2
REM set ConnectionList=localSolace-OCIDemoQueue, OCIDemoQueue-localSolace

set Synthetic1.Type=Synthetic
set Synthetic2.Type=Synthetic

set OCIDemoQueue.Type=OCIQ
set OCIDemoQueue.OCI_QUEUEID=<add your OCI Queue OCID>
set OCIDemoQueue.OCI_REGION=us-ashburn-1
set OCIDemoQueue.OCI_AUTHFILE=oci.properties
REM we can also provide the identity info as env vars rather than via a props file
REM set OCIDemoQueue.OCI_FINGERPRINT=XX
REM set OCIDemoQueue.OCI_TENNANT_ID=XX
REM set OCIDemoQueue.OCI_USERID=XX

REM a local deployment of Solace broker e.g. using Docker
set localSolace.Type=Solace
set localSolace.SOLACE_ADDR=tcp:127.0.0.1
set localSolace.SOLACE_PORT=55555
set localSolace.solace.messaging.authentication.scheme=AUTHENTICATION_SCHEME_BASIC
set localSolace.solace.messaging.authentication.basic.username=<provided user name>
set localSolace.solace.messaging.authentication.basic.password=<provided password>
set localSolace.SOLACE_TOPICNAME=testTopic
set localSolace.SOLACE_VPN=default

REM example of using Solace cloud
set cloudSolace.Type=Solace
set cloudSolace.SOLACE_ADDR=tcps://dns address of cloud instance
set cloudSolace.SOLACE_PORT=55443
set cloudSolace.solace.messaging.authentication.scheme=AUTHENTICATION_SCHEME_BASIC
set cloudSolace.solace.messaging.authentication.basic.username=<provided user name>
set cloudSolace.solace.messaging.authentication.basic.password=<provided password>
set cloudSolace.SOLACE_TOPICNAME=testTopic
set cloudSolace.SOLACE_VPN=demo

set Synthetic1.Type=
set Synthetic2.Type=

cls

echo %1
if [%1]==[reset] goto :reset
if [%1]==[build] goto :build
if [%1]==[run] goto :run
if [%1]==[help] goto :help
if [%1]==[compile] goto :compile

:help
echo Options are:
echo help: this info
echo reset: clears the env vars setup as params
echo build: compiles, packages and executes the cloudSolace
echo run: just runs the existing jar
echo compile: only compiles the code
goto :eof


:reset
REM reset all the environment variables
set ConnectionList=
set isMultiPass=

set OCIDemoQueue.Type=
set OCIDemoQueue.OCI_QUEUEID=
set OCIDemoQueue.OCI_FINGERPRINT=
set OCIDemoQueue.OCI_TENNANT_ID=
set OCIDemoQueue.OCI_REGION=
set OCIDemoQueue.OCI_USERID=
set OCIDemoQueue.OCI_AUTHFILE=

set localSolace.Type=
set localSolace.SOLACE_ADDR=
set localSolace.SOLACE_PORT=
set localSolace.solace.messaging.authentication.scheme=
set localSolace.solace.messaging.authentication.basic.username=
set localSolace.solace.messaging.authentication.basic.password=
set localSolace.SOLACE_TOPICNAME=
set localSolace.SOLACE_VPN=

set cloudSolace.Type=
set cloudSolace.SOLACE_ADDR=
set cloudSolace.SOLACE_PORT=
set cloudSolace.solace.messaging.authentication.scheme=
set cloudSolace.solace.messaging.authentication.basic.username=
set cloudSolace.solace.messaging.authentication.basic.password=
set cloudSolace.SOLACE_TOPICNAME=
set cloudSolace.SOLACE_VPN=


goto :eof

:compile
call mvn compile
goto :eof


:build
echo building ...
call mvn package
call mvn assembly:assembly -DdescriptorId=jar-with-dependencies 

:run
java -Dorg.slf4j.simpleLogger.defaultLogLevel=info -jar target/oci-solace-1.0-SNAPSHOT-jar-with-dependencies.jar

:eof
