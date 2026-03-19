FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copia o pom primeiro para cachear dependências
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Depois copia o código
COPY src ./src

RUN mvn clean package -DskipTests -B

# ---------- Runtime Stage ----------
FROM eclipse-temurin:21-jre

# Instala o Tesseract OCR. libtesseract-dev cria o symlink .so necessário pelo JNA.
# O tessdata do português é baixado direto do repositório oficial para /tessdata,
# evitando a variação de caminho entre versões do Ubuntu (4.00 vs 5.00).
RUN apt-get update && \
    apt-get install -y tesseract-ocr libtesseract-dev wget && \
    mkdir -p /tessdata && \
    wget -q "https://github.com/tesseract-ocr/tessdata_fast/raw/main/por.traineddata" \
         -O /tessdata/por.traineddata && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/start-app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# jna.library.path aponta para onde libtesseract.so está instalado no Linux
ENTRYPOINT ["java", "-Djna.library.path=/usr/lib/x86_64-linux-gnu", "-jar", "app.jar"]