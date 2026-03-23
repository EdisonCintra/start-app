package com.start.start_app.infrastructure.configuration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolve o caminho do tessdata do Tesseract em qualquer ambiente.
 *
 * Estratégia:
 *  1. Se o diretório configurado em ${tesseract.datapath} existir → usa ele (local/Windows).
 *  2. Caso contrário, extrai os arquivos .traineddata de classpath:/tessdata/ para um
 *     diretório temporário e usa esse caminho. Isso torna o JAR autocontido para deploy.
 */
@Component
public class
TesseractConfig {

    private static final Logger log = LoggerFactory.getLogger(TesseractConfig.class);

    @Value("${tesseract.datapath}")
    private String configuredDatapath;

    // Caminho resolvido — usado por OcrService via getTessDataPath()
    private String resolvedDatapath;

    @PostConstruct
    public void init() throws IOException {
        File configured = new File(configuredDatapath);

        if (configured.exists() && configured.isDirectory()) {
            // Ambiente local com Tesseract instalado (ex: Windows com instalador).
            // setDatapath() deve receber a pasta tessdata/ em si (onde ficam os .traineddata),
            // não o pai — comportamento do Tesseract 4+/5+.
            File tessdata = new File(configured, "tessdata");
            resolvedDatapath = tessdata.exists() ? tessdata.getAbsolutePath() : configuredDatapath;

            // Aponta JNA para as DLLs nativas do Tesseract instalado no sistema (pasta pai).
            // Sem isso, tess4j tenta carregar suas próprias DLLs embutidas no JAR,
            // o que pode falhar no Windows dependendo do antivírus ou permissões do temp.
            System.setProperty("jna.library.path", configuredDatapath);
            log.info("Tesseract: usando tessdata local → {}", resolvedDatapath);
        } else {
            // Deploy sem Tesseract instalado → extrai tessdata do classpath para diretório temp
            resolvedDatapath = extractTessdataFromClasspath();
            log.info("Tesseract: datapath configurado não encontrado, usando tessdata embutido → {}", resolvedDatapath);
        }
    }

    /**
     * Copia os arquivos *.traineddata de classpath:/tessdata/ para um diretório temporário.
     * O diretório temp sobrevive enquanto a JVM estiver rodando.
     */
    private String extractTessdataFromClasspath() throws IOException {
        Path tempDir = Files.createTempDirectory("tessdata-");
        Path tessdataDir = tempDir.resolve("tessdata");
        Files.createDirectories(tessdataDir);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:tessdata/*.traineddata");

        if (resources.length == 0) {
            throw new IllegalStateException(
                "Nenhum arquivo .traineddata encontrado em classpath:/tessdata/. " +
                "Adicione os arquivos de idioma (ex: por.traineddata) em src/main/resources/tessdata/."
            );
        }

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, tessdataDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                log.info("Tesseract: tessdata extraído → {}", filename);
            }
        }

        // setDatapath() recebe o diretório tessdata/ em si (onde estão os .traineddata)
        return tessdataDir.toString();
    }

    public String getTessDataPath() {
        return resolvedDatapath;
    }
}