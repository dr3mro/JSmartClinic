package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.repository.SettingsRepository;

import java.util.Map;

public final class SettingsService {

    public record ClinicProfile(
            String name, String address, String phone, String email, String logoPath,
            String currency, int taxRate, String theme, String language, boolean autoBackup) {
    }

    private final SettingsRepository repository;
    private final Session session;
    private final AuditService audit;

    public SettingsService(SettingsRepository repository, Session session, AuditService audit) {
        this.repository = repository;
        this.session = session;
        this.audit = audit;
    }

    public String get(String key, String fallback) {
        return repository.get(key, fallback);
    }

    public Map<String, String> all() {
        return repository.all();
    }

    public String clinicName() {
        return repository.get("clinic_name", "JSmartClinic");
    }

    public ClinicProfile profile() {
        Map<String, String> map = repository.all();
        int tax = 0;
        try {
            tax = (int) Math.round(Double.parseDouble(map.getOrDefault("tax_rate", "0")));
        } catch (NumberFormatException ignored) {
            // keep zero
        }
        return new ClinicProfile(
                map.getOrDefault("clinic_name", "JSmartClinic"),
                map.getOrDefault("clinic_address", ""),
                map.getOrDefault("clinic_phone", ""),
                map.getOrDefault("clinic_email", ""),
                map.getOrDefault("clinic_logo", ""),
                map.getOrDefault("currency", "$"),
                Math.max(0, Math.min(100, tax)),
                map.getOrDefault("theme", "light"),
                map.getOrDefault("language", "en"),
                Boolean.parseBoolean(map.getOrDefault("auto_backup", "true")));
    }

    public void saveProfile(ClinicProfile profile) {
        session.require(Permission.MANAGE_USERS_SETTINGS);
        repository.putAll(Map.of(
                "clinic_name", profile.name() == null ? "JSmartClinic" : profile.name(),
                "clinic_address", profile.address() == null ? "" : profile.address(),
                "clinic_phone", profile.phone() == null ? "" : profile.phone(),
                "clinic_email", profile.email() == null ? "" : profile.email(),
                "clinic_logo", profile.logoPath() == null ? "" : profile.logoPath(),
                "currency", profile.currency() == null ? "$" : profile.currency(),
                "tax_rate", String.valueOf(profile.taxRate()),
                "auto_backup", String.valueOf(profile.autoBackup())));
        audit.log(AuditAction.UPDATE, "settings", null, null, "Clinic profile updated");
    }

    public void setTheme(String theme) {
        repository.put("theme", theme);
    }

    public void setLanguage(String language) {
        repository.put("language", language);
    }

    public void put(String key, String value) {
        session.require(Permission.MANAGE_USERS_SETTINGS);
        repository.put(key, value);
        audit.log(AuditAction.UPDATE, "settings", null, null, "Setting " + key + " updated");
    }

    public int taxRate() {
        return profile().taxRate();
    }

    public String currency() {
        return profile().currency();
    }
}
