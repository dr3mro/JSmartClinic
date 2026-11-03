package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.repository.BillingRepository;
import org.amrosman.jsmartclinic.repository.ReportRepository;
import org.amrosman.jsmartclinic.repository.ReportRepository.NamedCount;
import org.amrosman.jsmartclinic.repository.ReportRepository.PeriodAmount;
import org.amrosman.jsmartclinic.util.Csv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public final class ReportService {

    private final ReportRepository reports;
    private final BillingRepository billing;
    private final PdfService pdf;
    private final DocumentService documents;
    private final Session session;
    private final AuditService audit;

    public ReportService(ReportRepository reports, BillingRepository billing, PdfService pdf,
                         DocumentService documents, Session session, AuditService audit) {
        this.reports = reports;
        this.billing = billing;
        this.pdf = pdf;
        this.documents = documents;
        this.session = session;
        this.audit = audit;
    }

    public List<NamedCount> appointmentsByProvider(LocalDate from, LocalDate to) {
        session.require(Permission.REPORTS);
        return reports.appointmentsByProvider(from, to);
    }

    public List<NamedCount> topDiagnoses(LocalDate from, LocalDate to, int limit) {
        session.require(Permission.REPORTS);
        return reports.topDiagnoses(from, to, limit);
    }

    public List<NamedCount> demographics() {
        session.require(Permission.REPORTS);
        return reports.demographics();
    }

    public List<NamedCount> ageGroups() {
        session.require(Permission.REPORTS);
        return reports.ageGroups();
    }

    public List<NamedCount> appointmentsByStatus(LocalDate from, LocalDate to) {
        session.require(Permission.REPORTS);
        return reports.appointmentsByStatus(from, to);
    }

    public List<PeriodAmount> revenueByMonth(LocalDate from, LocalDate to) {
        session.require(Permission.REPORTS);
        return reports.revenueByMonth(from, to);
    }

    public long revenue(LocalDate from, LocalDate to) {
        session.require(Permission.REPORTS);
        return billing.revenue(from, to);
    }

    public Path exportCsv(String name, List<String> headers, List<List<Object>> rows) {
        session.require(Permission.REPORTS);
        Path target = documents.uniqueExport(name, ".csv");
        try {
            Files.writeString(target, Csv.table(headers, rows), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to export CSV", e);
        }
        audit.log(AuditAction.EXPORT, "report", null, null, name);
        return target;
    }

    public Path exportPdf(String title, List<String> headers, List<List<String>> rows) {
        session.require(Permission.REPORTS);
        Path target = pdf.report(title, headers, rows);
        audit.log(AuditAction.PRINT, "report", null, null, title);
        return target;
    }
}
