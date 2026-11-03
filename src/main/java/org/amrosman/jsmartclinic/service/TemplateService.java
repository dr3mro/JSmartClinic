package org.amrosman.jsmartclinic.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Loads specialty JSON templates and exposes their structure to the UI. */
public final class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);
    private static final List<String> IDS = List.of(
            "general", "cardiology", "pediatrics", "obgyn", "dermatology", "ophthalmology");

    public record Field(String key, String label, String type, List<String> options, String unit,
                        Double min, Double max, String placeholder) {
        public boolean textArea() {
            return "textarea".equals(type);
        }
    }

    public record Section(String title, List<Field> fields) {
    }

    public record Template(String id, String name, String specialty, List<Section> sections) {
    }

    private final List<Template> templates = new ArrayList<>();

    public TemplateService() {
        for (String id : IDS) {
            Template template = load(id);
            if (template != null) {
                templates.add(template);
            }
        }
    }

    public List<Template> all() {
        return List.copyOf(templates);
    }

    public Optional<Template> byId(String id) {
        return templates.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public Optional<Template> defaultFor(String specialty) {
        if (specialty == null || specialty.isBlank()) {
            return byId("general");
        }
        String needle = specialty.toLowerCase(Locale.ROOT);
        return templates.stream()
                .filter(t -> t.specialty().toLowerCase(Locale.ROOT).contains(needle)
                        || needle.contains(t.specialty().toLowerCase(Locale.ROOT)))
                .findFirst()
                .or(() -> byId("general"));
    }

    public String specialtyForUser(User user) {
        if (user == null) {
            return "General";
        }
        return user.specialty() == null || user.specialty().isBlank() ? "General" : user.specialty();
    }

    private Template load(String id) {
        String resource = "/templates/" + id + ".json";
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("Template {} not found", id);
                return null;
            }
            JsonNode root = Json.mapper().readTree(in);
            List<Section> sections = new ArrayList<>();
            for (JsonNode sectionNode : root.path("sections")) {
                List<Field> fields = new ArrayList<>();
                for (JsonNode fieldNode : sectionNode.path("fields")) {
                    List<String> options = new ArrayList<>();
                    fieldNode.path("options").forEach(o -> options.add(o.asText()));
                    fields.add(new Field(
                            fieldNode.path("key").asText(),
                            fieldNode.path("label").asText(),
                            fieldNode.path("type").asText("text"),
                            options,
                            fieldNode.has("unit") ? fieldNode.get("unit").asText() : null,
                            fieldNode.has("min") ? fieldNode.get("min").asDouble() : null,
                            fieldNode.has("max") ? fieldNode.get("max").asDouble() : null,
                            fieldNode.has("placeholder") ? fieldNode.get("placeholder").asText() : null));
                }
                sections.add(new Section(sectionNode.path("title").asText(), fields));
            }
            return new Template(root.path("id").asText(id), root.path("name").asText(id),
                    root.path("specialty").asText("General"), sections);
        } catch (Exception e) {
            log.error("Unable to load template {}", id, e);
            return null;
        }
    }
}
