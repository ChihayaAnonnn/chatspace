FROM openjdk:8
ARG NACOS_IP
ARG HOST_IP
ARG SERVER_PORT
ARG JVM_PORT
ARG MANAGEMENT_PORT
ARG REGISTER_PORT
ENV CHATSPACE_CLOUD_HOME=/project \
    HOST_IP=$HOST_IP \
    NACOS_IP=$NACOS_IP \
    SERVER_PORT=$SERVER_PORT \
    JVM_PORT=$JVM_PORT \
    MANAGEMENT_PORT=$MANAGEMENT_PORT \
    REGISTER_PORT=$REGISTER_PORT
COPY ./chatspace-realtime-1.0.jar ./chatspace-realtime-1.0.jar
ENTRYPOINT java -jar \
           -Djava.rmi.server.hostname=$HOST_IP \
           -Dcom.sun.management.jmxremote=true \
           -Dcom.sun.management.jmxremote.port=$JVM_PORT \
           -Dcom.sun.management.jmxremote.rmi.port=$JVM_PORT \
           -Dcom.sun.management.jmxremote.authenticate=false \
           -Dcom.sun.management.jmxremote.ssl=false \
           chatspace-realtime-1.0.jar
