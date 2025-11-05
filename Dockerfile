FROM openjdk:21-jdk-slim
COPY build/libs/*SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]