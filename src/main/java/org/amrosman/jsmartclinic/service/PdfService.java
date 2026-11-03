package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.Invoice;
import org.amrosman.jsmartclinic.domain.InvoiceItem;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Payment;
import org.amrosman.jsmartclinic.domain.Prescription;
import org.amrosman.jsmartclinic.domain.PrescriptionItem;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.util.Dates;
import org.amrosman.jsmartclinic.util.Money;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Generates prescription, receipt/invoice and report PDFs with the clinic header. */
public final class PdfService {

    private final SettingsService settings;
    private final Database database;

    public PdfService(SettingsService settings, Database database) {
        this.settings = settings;
        this.database = database;
    }

    public Path prescription(Patient patient, User prescriber, Prescription prescription,
                             List<PrescriptionItem> items) {
        Path target = exports("prescription", ".pdf");
        try (PdfBuilder pdf = new PdfBuilder("Prescription", "Issued " + Dates.display(prescription.issuedAt()))) {
            pdf.patientBlock(patient, prescriber);
            pdf.gap(8);
            pdf.section("Medication");
            for (int i = 0; i < items.size(); i++) {
                PrescriptionItem item = items.get(i);
                pdf.bold((i + 1) + ". " + safe(item.drugName()) + " " + safe(item.strength())
                        + (item.form() == null || item.form().isBlank() ? "" : " (" + safe(item.form()) + ")"));
                pdf.text("Dose: " + safe(item.dose()) + "    Frequency: " + safe(item.frequency())
                        + "    Duration: " + safe(item.duration()) + "    Qty: " + safe(item.quantity()));
                if (item.instructions() != null && !item.instructions().isBlank()) {
                    pdf.text("Instructions: " + safe(item.instructions()));
                }
                pdf.gap(4);
            }
            if (prescription.notes() != null && !prescription.notes().isBlank()) {
                pdf.section("Notes");
                pdf.wrapped(safe(prescription.notes()));
            }
            pdf.gap(18);
            pdf.text("Prescriber: " + safe(prescriber.fullName()));
            pdf.text("Signature: ______________________________");
            pdf.save(target);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create prescription PDF", e);
        }
        return target;
    }

    public Path receipt(Invoice invoice, Patient patient, List<InvoiceItem> items, List<Payment> payments) {
        Path target = exports("receipt", ".pdf");
        try (PdfBuilder pdf = new PdfBuilder("Receipt / Invoice", invoice.invoiceNo())) {
            pdf.patientBlock(patient, null);
            pdf.gap(8);
            pdf.section("Items");
            for (InvoiceItem item : items) {
                pdf.text(String.format("%-42s %3d x %10s  %12s", safe(item.description()), item.qty(),
                        Money.format(item.unitPriceCents(), settings.currency()),
                        Money.format(item.lineTotalCents(), settings.currency())));
            }
            pdf.gap(6);
            pdf.textRight("Subtotal: " + Money.format(invoice.subtotalCents(), settings.currency()));
            pdf.textRight("Discount: -" + Money.format(invoice.discountCents(), settings.currency()));
            pdf.textRight("Tax: " + Money.format(invoice.taxCents(), settings.currency()));
            pdf.boldRight("Total: " + Money.format(invoice.totalCents(), settings.currency()));
            pdf.gap(6);
            pdf.textRight("Status: " + invoice.status().name());
            if (!payments.isEmpty()) {
                pdf.section("Payments");
                for (Payment payment : payments) {
                    pdf.text(Dates.display(payment.paidAt()) + "   " + payment.method().name() + "   "
                            + Money.format(payment.amountCents(), settings.currency())
                            + (payment.reference() == null ? "" : "   Ref: " + safe(payment.reference())));
                }
            }
            pdf.save(target);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create receipt PDF", e);
        }
        return target;
    }

    public Path report(String title, List<String> headers, List<List<String>> rows) {
        Path target = exports("report", ".pdf");
        try (PdfBuilder pdf = new PdfBuilder(title, "Generated " + Dates.display(Instant.now()))) {
            pdf.table(headers, rows);
            pdf.save(target);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create report PDF", e);
        }
        return target;
    }

    private Path exports(String prefix, String suffix) {
        try {
            Path dir = database.dataDir().resolve("exports");
            Files.createDirectories(dir);
            Database.restrictPermissions(dir);
            return dir.resolve(prefix + "-" + Instant.now().toEpochMilli() + suffix);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\x00-\\xFF]", "?");
    }

    /** Simple flowing PDF layout helper. */
    private final class PdfBuilder implements AutoCloseable {
        private static final float MARGIN = 50;
        private static final float LINE = 15;
        private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        private final PDDocument document = new PDDocument();
        private final String title;
        private final String subtitle;
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        PdfBuilder(String title, String subtitle) throws IOException {
            this.title = title;
            this.subtitle = subtitle;
            newPage();
        }

        PDDocument document() {
            return document;
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
            header();
        }

        private void header() throws IOException {
            SettingsService.ClinicProfile profile = settings.profile();
            write(profile.name(), BOLD, 20, MARGIN);
            write(profile.address() + (profile.phone().isBlank() ? "" : "  ·  " + profile.phone()), REGULAR, 9, MARGIN);
            drawLogo(profile.logoPath());
            y -= 4;
            write(title, BOLD, 15, MARGIN);
            write(subtitle == null ? "" : subtitle, REGULAR, 9, MARGIN);
            line();
            y -= 4;
        }

        private void drawLogo(String logoPath) {
            if (logoPath == null || logoPath.isBlank()) {
                return;
            }
            try {
                Path logo = Path.of(logoPath);
                if (!Files.isRegularFile(logo)) {
                    return;
                }
                PDImageXObject image = PDImageXObject.createFromFile(logo.toString(), document);
                float maxWidth = 70;
                float maxHeight = 46;
                float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
                float width = image.getWidth() * scale;
                float height = image.getHeight() * scale;
                content.drawImage(image, page.getMediaBox().getWidth() - MARGIN - width,
                        page.getMediaBox().getHeight() - MARGIN + 12 - height, width, height);
            } catch (IOException | RuntimeException e) {
                // A missing or unreadable logo must never block a clinical document.
            }
        }

        void patientBlock(Patient patient, User prescriber) throws IOException {
            write("Patient: " + safe(patient.fullName()) + "    MRN: " + patient.mrn()
                    + "    DOB: " + Dates.displayDate(patient.dateOfBirth())
                    + "    Age: " + patient.age() + "    Sex: " + patient.sex().label(), REGULAR, 10, MARGIN);
            if (prescriber != null) {
                write("Prescriber: " + safe(prescriber.fullName())
                        + (prescriber.specialty() == null ? "" : " (" + safe(prescriber.specialty()) + ")"),
                        REGULAR, 10, MARGIN);
            }
        }

        void section(String text) throws IOException {
            ensureSpace(LINE * 2);
            y -= 6;
            write(text, BOLD, 12, MARGIN);
        }

        void bold(String text) throws IOException {
            ensureSpace(LINE);
            write(text, BOLD, 10, MARGIN);
        }

        void boldRight(String text) throws IOException {
            ensureSpace(LINE);
            writeRight(text, BOLD, 11);
        }

        void text(String text) throws IOException {
            ensureSpace(LINE);
            write(text, REGULAR, 10, MARGIN);
        }

        void textRight(String text) throws IOException {
            ensureSpace(LINE);
            writeRight(text, REGULAR, 10);
        }

        void wrapped(String text) throws IOException {
            for (String line : wrap(text, 95)) {
                text(line);
            }
        }

        void gap(float amount) {
            y -= amount;
        }

        void table(List<String> headers, List<List<String>> rows) throws IOException {
            section(String.join("  |  ", headers.stream().map(PdfService::safe).toList()));
            for (List<String> row : rows) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) {
                        sb.append("  |  ");
                    }
                    sb.append(PdfService.safe(row.get(i)));
                }
                text(sb.toString());
            }
        }

        void save(Path target) throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
            document.save(target.toFile());
        }

        private void line() throws IOException {
            content.setStrokingColor(0.85f, 0.89f, 0.94f);
            content.moveTo(MARGIN, y);
            content.lineTo(page.getMediaBox().getWidth() - MARGIN, y);
            content.stroke();
            y -= 12;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        private void write(String value, PDType1Font font, float size, float x) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, y);
            content.showText(value == null ? "" : safe(value));
            content.endText();
            y -= LINE;
        }

        private void writeRight(String value, PDType1Font font, float size) throws IOException {
            String text = safe(value);
            float width = font.getStringWidth(text) / 1000 * size;
            write(value, font, size, page.getMediaBox().getWidth() - MARGIN - width);
        }

        private List<String> wrap(String text, int width) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (String paragraph : safe(text).split("\\R")) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split(" ")) {
                    if (line.length() + word.length() + 1 > width) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    if (line.length() > 0) {
                        line.append(' ');
                    }
                    line.append(word);
                }
                lines.add(line.toString());
            }
            return lines;
        }

        @Override
        public void close() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
            document.close();
        }
    }
}
