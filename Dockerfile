# Use a lightweight Java base image with Java 25
FROM openjdk:25-slim

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "target/OTP-Login-0.0.1-SNAPSHOT.jar"]