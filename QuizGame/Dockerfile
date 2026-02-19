# Fase 1: Compilar con Maven
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests

# Fase 2: Imagen final ligera solo con JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/QuizGame-1.0-SNAPSHOT.jar app.jar
COPY --from=build /app/src/main/resources/blocket.csv /app/blocket.csv
COPY --from=build /app/src/main/resources/Certificados /app/Certificados
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh

# Puerto TCP (quiz normal) y SSL
EXPOSE 8080 8443

# Arrancar ambos servidores
CMD ["/bin/bash", "/app/start.sh"]
