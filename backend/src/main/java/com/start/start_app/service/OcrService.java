package com.start.start_app.service;

import com.start.start_app.exception.business.InvalidDocumentException;
import com.start.start_app.exception.business.OcrException;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

@Service
public class OcrService {

    @Value("${tesseract.datapath}")
    private String datapath;

    @Value("${tesseract.language:eng}")
    private String language;

    // Injeção via construtor: permite que o Spring injete sem precisar de @Autowired no campo
    private final PiiMaskingService piiMaskingService;

    public OcrService(PiiMaskingService piiMaskingService) {
        this.piiMaskingService = piiMaskingService;
    }

    private static final List<String> ALLOWED_EXTENSIONS = List.of("png", "jpg", "jpeg", "pdf");

    // Mínimo de caracteres para que o texto extraído seja considerado um documento financeiro legível.
    // Uma fatura real sempre terá ao menos datas, valores e descrições — 80 chars é um piso conservador.
    private static final int MIN_TEXT_LENGTH = 80;

    public String extractText(MultipartFile file) {
        String ext = getValidatedExtension(file);

        String rawText = ext.equals("pdf")
                ? extractFromPdf(file)
                : extractFromImage(file);

        // Mascara CPF, CNPJ, RG, cartão, e-mail e telefone antes de expor ao frontend
        String maskedText = piiMaskingService.mask(rawText);

        // Rejeita antes de chamar a IA: imagens sem texto (foto de cachorro, foto de pessoa, etc.)
        // ou com conteúdo insuficiente para constituir uma fatura.
        // maskedText pode ser null se piiMaskingService recebeu null (Tesseract sem texto extraído)
        if (maskedText == null || maskedText.strip().length() < MIN_TEXT_LENGTH) {
            throw new InvalidDocumentException(
                    "Não foi possível extrair conteúdo financeiro do arquivo. " +
                    "Certifique-se de que o arquivo é uma fatura ou conta legível.");
        }

        return maskedText;
    }

    private String extractFromImage(MultipartFile file) {
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            // ImageIO.read() retorna null quando não reconhece o formato da imagem
            if (img == null) {
                throw new OcrException("Não foi possível decodificar a imagem. Verifique se o arquivo não está corrompido.");
            }
            return runTesseract(img);
        } catch (IOException e) {
            throw new OcrException("Erro ao ler a imagem enviada.");
        }
    }


    private String extractFromPdf(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            String embeddedText = new PDFTextStripper().getText(document);

            if (embeddedText != null && !embeddedText.isBlank()) {
                return embeddedText;
            }


            // Usamos 300 DPI para garantir qualidade suficiente para o Tesseract reconhecer
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder result = new StringBuilder();

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(page, 300);
                result.append(runTesseract(pageImage));

                // Separador entre páginas para facilitar leitura do resultado
                if (page < document.getNumberOfPages() - 1) {
                    result.append("\n\n--- Página ").append(page + 2).append(" ---\n\n");
                }
            }

            return result.toString();

        } catch (IOException e) {
            throw new OcrException("Erro ao ler o arquivo PDF enviado.");
        }
    }


    // IMPORTANTE: Tesseract NÃO é thread-safe → nova instância a cada chamada
    private String runTesseract(BufferedImage image) {
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(datapath);
            tesseract.setLanguage(language);
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            throw new OcrException("Falha no motor OCR: " + e.getMessage());
        }
    }

    private String getValidatedExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String ext = (filename != null && filename.contains("."))
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                : "";

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new OcrException("Formato inválido: '" + ext + "'. Formatos suportados: PNG, JPG e PDF.");
        }

        return ext;
    }
}