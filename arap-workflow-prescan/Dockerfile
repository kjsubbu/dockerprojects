FROM java:8
VOLUME /tmp
ADD ./target/arap-workflow-prescan-1.0.0.jar app.jar
RUN bash -c 'touch /app.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]

