# Stage 1: build the jar with the Maven Wrapper
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests

# Stage 2: run it on a small JRE image as a non-root user
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home stockflow
USER stockflow
WORKDIR /app
COPY --from=build /workspace/target/stockflow.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
