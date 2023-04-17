# Copyright(c) 2023, Oracle and / or its affiliates.
# All rights reserved. The Universal Permissive License(UPL), Version 1.0 as shown at http: // oss.oracle.com/licenses/upl
FROM openjdk:8
WORKDIR /
ADD ./target/*.jar *.properties .
# EXPOSE 8080, 1943, 2222, 55555, 55003, 55443, 55556, 9000, 9443, 5672, 5671, 8883, 8000
# currently not exposing
# EXPOSE 8080, 1943, 2222, 55555, 55003, 55443, 55556, 1443, 9000, 9443, 5672, 5671, 8883, 8000
# match ports for Solace - tcp-semp, tls-semp, tcp-ssh, tcp-smfcomp, tcp-smfcomp, tls-smf, tls-smfroute, tls-web, tcp-rest, tls-rest, tcp-amqp, tls-amqp, tls-mqtt, tcp-mqtt
# HEALTHCHECK --start-period=60s --timeout=30s CMD curl -f localhost:8080/health || exit 1
ENTRYPOINT ["java", "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug", "-jar", "./oci-solace-1.0-SNAPSHOT-jar-with-dependencies.jar" ]