package com.example.chatbot.service;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.model.Message;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ExportService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Charset PDF_CHARSET = Charset.forName("windows-1252");

    // ================= EXPORT METHODS =================
    
    /**
     * Export conversation to plain text file
     */
    public static boolean exportToText(Conversation conversation, Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        
        // Header
        content.append("=".repeat(80)).append("\n");
        content.append("CHAT EXPORT: ").append(conversation.getTitle()).append("\n");
        content.append("=".repeat(80)).append("\n\n");
        
        // Messages
        for (Message msg : conversation.getMessages()) {
            String sender = msg.getSender() == Message.Sender.USER ? "USER" : "CORTEX";
            String timestamp = msg.getTimestamp().format(DATE_FORMATTER);
            
            content.append("[").append(timestamp).append("] ").append(sender).append(":\n");
            content.append(msg.getContent()).append("\n\n");
        }
        
        Files.writeString(filePath, content.toString(), StandardCharsets.UTF_8);
        return true;
    }

    /**
     * Export conversation to Markdown file
     */
    public static boolean exportToMarkdown(Conversation conversation, Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        
        // Header
        content.append("# ").append(conversation.getTitle()).append("\n\n");
        
        // Messages
        for (Message msg : conversation.getMessages()) {
            String sender = msg.getSender() == Message.Sender.USER ? "👤 User" : "🤖 Cortex";
            String timestamp = msg.getTimestamp().format(DATE_FORMATTER);
            
            content.append("## ").append(sender).append(" (").append(timestamp).append(")\n\n");
            content.append(msg.getContent()).append("\n\n");
            content.append("---\n\n");
        }
        
        Files.writeString(filePath, content.toString(), StandardCharsets.UTF_8);
        return true;
    }

    /**
     * Export conversation to PDF file
     */
    public static boolean exportToPDF(Conversation conversation, Path filePath) throws IOException {
        PDDocument document = new PDDocument();
        
        try {
            // Add title page
            PDPage page = new PDPage();
            document.addPage(page);
            
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
            contentStream.beginText();
            contentStream.newLineAtOffset(50, 750);
            String safeTitle = sanitizeForPdf(conversation.getTitle()).replace("\r", " ").replace("\n", " ");
            contentStream.showText("Chat Export: " + safeTitle);
            contentStream.endText();
            
            // Add messages
            contentStream.setFont(PDType1Font.HELVETICA, 10);
            float yPosition = 700;
            
            for (Message msg : conversation.getMessages()) {
                String sender = msg.getSender() == Message.Sender.USER ? "USER" : "CORTEX";
                String timestamp = msg.getTimestamp().format(DATE_FORMATTER);
                String content = sanitizeForPdf(msg.getContent());

                if (yPosition < 70) {
                    contentStream.close();
                    page = new PDPage();
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    yPosition = 750;
                }
                
                // Message header
                contentStream.beginText();
                contentStream.newLineAtOffset(50, yPosition);
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                contentStream.showText("[" + timestamp + "] " + sender + ":");
                contentStream.endText();
                
                yPosition -= 15;
                
                // Message content (wrap text while preserving explicit line breaks)
                List<String> lines = wrapTextPreservingLineBreaks(content, 80);
                
                for (String line : lines) {
                    if (yPosition < 50) {
                        contentStream.close();
                        page = new PDPage();
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        yPosition = 750;
                    }

                    if (!line.isEmpty()) {
                        contentStream.beginText();
                        contentStream.setFont(PDType1Font.HELVETICA, 10);
                        contentStream.newLineAtOffset(60, yPosition);
                        contentStream.showText(line);
                        contentStream.endText();
                    }
                    yPosition -= 12;
                }
                
                yPosition -= 10;
            }
            
            contentStream.close();
            document.save(filePath.toFile());
            return true;
        } finally {
            document.close();
        }
    }

    /**
     * Export conversation to Word (.docx) file
     */
    public static boolean exportToWord(Conversation conversation, Path filePath) throws IOException {
        XWPFDocument document = new XWPFDocument();
        
        try {
            // Title
            XWPFParagraph titleParagraph = document.createParagraph();
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText("Chat Export: " + conversation.getTitle());
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            
            // Add spacing
            document.createParagraph();
            
            // Messages
            for (Message msg : conversation.getMessages()) {
                String sender = msg.getSender() == Message.Sender.USER ? "User" : "Cortex";
                String timestamp = msg.getTimestamp().format(DATE_FORMATTER);
                
                // Sender + timestamp
                XWPFParagraph headerParagraph = document.createParagraph();
                XWPFRun headerRun = headerParagraph.createRun();
                headerRun.setText("[" + timestamp + "] " + sender + ":");
                headerRun.setBold(true);
                headerRun.setColor("366092");
                
                // Content
                XWPFParagraph contentParagraph = document.createParagraph();
                XWPFRun contentRun = contentParagraph.createRun();
                contentRun.setText(msg.getContent());
                
                // Spacing between messages
                document.createParagraph();
            }
            
            document.write(new FileOutputStream(filePath.toFile()));
            return true;
        } finally {
            document.close();
        }
    }

    /**
     * Helper method to wrap text to specified width
     */
    private static String[] wrapText(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return new String[]{ text };
        }
        
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
            }
            
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines.isEmpty() ? new String[]{ text } : lines.toArray(new String[0]);
    }

    private static List<String> wrapTextPreservingLineBreaks(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }

        List<String> wrappedLines = new ArrayList<>();
        String normalizedText = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawLines = normalizedText.split("\n", -1);

        for (String rawLine : rawLines) {
            String[] lineParts = wrapText(rawLine, maxWidth);
            for (String linePart : lineParts) {
                wrappedLines.add(linePart == null ? "" : linePart);
            }
        }

        return wrappedLines;
    }

    private static String sanitizeForPdf(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        CharsetEncoder encoder = PDF_CHARSET.newEncoder();
        StringBuilder sanitized = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\r' || ch == '\n') {
                sanitized.append(ch);
                continue;
            }
            if (ch == '\t') {
                sanitized.append("    ");
                continue;
            }
            if (Character.isISOControl(ch)) {
                continue;
            }
            sanitized.append(encoder.canEncode(ch) ? ch : '?');
        }

        return sanitized.toString();
    }
}
