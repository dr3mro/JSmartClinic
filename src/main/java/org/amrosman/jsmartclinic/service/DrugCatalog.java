package org.amrosman.jsmartclinic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bundled list of common generic drugs, used for autocomplete. */
public final class DrugCatalog {

    private static final Logger log = LoggerFactory.getLogger(DrugCatalog.class);

    public record Drug(String name, String strength, String form) {
        public String display() {
            return name + (strength == null || strength.isBlank() ? "" : " " + strength);
        }
    }

    private final List<Drug> drugs = new ArrayList<>();

    public DrugCatalog() {
        try (InputStream in = getClass().getResourceAsStream("/data/drugs.csv")) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    boolean first = true;
                    while ((line = reader.readLine()) != null) {
                        if (first) {
                            first = false;
                            continue;
                        }
                        String[] parts = line.split(",", -1);
                        if (parts.length >= 1 && !parts[0].isBlank()) {
                            drugs.add(new Drug(parts[0].trim(),
                                    parts.length > 1 ? parts[1].trim() : "",
                                    parts.length > 2 ? parts[2].trim() : ""));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Unable to load drug catalogue", e);
        }
    }

    public List<Drug> all() {
        return List.copyOf(drugs);
    }

    public List<Drug> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return drugs.stream().limit(limit).toList();
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return drugs.stream().filter(d -> d.name().toLowerCase(Locale.ROOT).contains(needle))
                .limit(limit).toList();
    }
}
