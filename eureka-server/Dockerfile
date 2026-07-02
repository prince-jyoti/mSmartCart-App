FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy the source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# ---- Runtime Image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
