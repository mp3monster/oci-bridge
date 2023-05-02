rem # Copyright(c) 2022, Oracle and / or its affiliates.
rem # All rights reserved. The Universal Permissive License(UPL), Version 1.0 as shown at http: // oss.oracle.com/licenses/upl

rem build the docker image and set the tag

set uname=%1
REM if the username is an IDCS identity then the username needs to be prefixed with identitycloudservice/
set pword=%2
REM set pword=i#];AuTOz<O2NN+it5j}

set tenancy=%3
REM is the Tenancy name

set region=%4
REM is the OCI Region in short form e.g. iad

set name=%5
REM is the service name e.g. event-data-src or oci-solace-bridge

set deployname=%name%

set podname=%name%-pod
echo %podname%

call mvn clean package
REM call mvn clean assembly:assembly -DdescriptorId=jar-with-dependencies 


echo deploying %name% for %uname%
echo 

docker build -f ./Dockerfile -t %name% .
docker tag %name%:latest %region%.ocir.io/%tenancy%/%deployname%:latest

rem acces OCIR and upload the container
docker login -u %tenancy%/%uname% -p %pword%  %region%.ocir.io

docker push %region%.ocir.io/%tenancy%/%deployname%:latest

rem deploy the container and then the service wrapper
kubectl apply -f ./deployment.yaml
kubectl apply -f ./src/deploy/%name%.yaml

kubectl delete pod -l app=%podname%