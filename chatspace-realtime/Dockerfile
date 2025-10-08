FROM openjdk:8
ARG NACOS_IP
ARG HOST_IP
ARG SERVER_PORT
ARG REGISTER_PORT
ENV CHATSPACE_CLOUD_HOME=/project \
    HOST_IP=$HOST_IP \
    NACOS_IP=$NACOS_IP \
    SERVER_PORT=$SERVER_PORT \
    REGISTER_PORT=$REGISTER_PORT
COPY ./chatspace-realtime-1.0.jar ./chatspace-realtime-1.0.jar
ENTRYPOINT ["java", "-jar", "chatspace-realtime-1.0.jar"]
