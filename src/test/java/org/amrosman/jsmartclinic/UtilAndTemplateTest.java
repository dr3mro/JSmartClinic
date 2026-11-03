package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.Sex;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.service.TemplateService;
import org.amrosman.jsmartclinic.util.Csv;
import org.amrosman.jsmartclinic.util.Dates;
import org.amrosman.jsmartclinic.util.I18n;
import org.amrosman.jsmartclinic.util.Json;
import org.amrosman.jsmartclinic.util.Money;
import org.amrosman.jsmartclinic.util.Paging;
import org.amrosman.jsmartclinic.util.PasswordHasher;
import org.amrosman.jsmartclinic.util.Sha256;
import org.amrosman.jsmartclinic.util.Validators;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilAndTemplateTest {

    @AfterEach
    void resetLanguage() {
        I18n.setLanguage("en");
    }

    @Test
    void dateHelpersRoundTripAndDisplay() {
        Instant instant = Instant.parse("2024-05-06T12:34:56Z");
        String text = Dates.toText(instant);
        assertThat(text).isEqualTo("2024-05-06T12:34:56Z");
        assertThat(Dates.fromText(text)).isEqualTo(instant);
        assertThat(Dates.fromText("2024-05-06T12:34:56")).isNotNull();
        assertThat(Dates.fromText("nonsense")).isNull();
        assertThat(Dates.fromText(null)).isNull();
        assertThat(Dates.fromText("")).isNull();

        LocalDate date = LocalDate.of(2024, 5, 6);
        assertThat(Dates.toText(date)).isEqualTo("2024-05-06");
        assertThat(Dates.dateFromText("2024-05-06")).isEqualTo(date);
        assertThat(Dates.dateFromText("2024-05-06T12:34:56Z")).isEqualTo(date);
        assertThat(Dates.dateFromText("bad")).isNull();
        assertThat(Dates.dateFromText(null)).isNull();
        assertThat(Dates.isoDate(null)).isEmpty();
        assertThat(Dates.isoDate(date)).isEqualTo("2024-05-06");

        assertThat(Dates.display(instant)).contains("2024");
        assertThat(Dates.display(null)).isEqualTo("—");
        assertThat(Dates.displayDate(date)).contains("2024");
        assertThat(Dates.displayDate((LocalDate) null)).isEqualTo("—");
        assertThat(Dates.displayDate((Instant) null)).isEqualTo("—");
        assertThat(Dates.displayTime(null)).isEqualTo("—");
        assertThat(Dates.displayTime(instant)).matches("\\d{2}:\\d{2}");
        assertThat(Dates.startOfDay(date).atZone(ZoneId.systemDefault()).toLocalDate()).isEqualTo(date);
        assertThat(Dates.endOfDay(date)).isAfter(Dates.startOfDay(date));
    }

    @Test
    void moneyCsvJsonAndMiscHelpers() {
        assertThat(Money.format(123_456)).isEqualTo("$1,234.56");
        assertThat(Money.format(123_456, "€")).isEqualTo("€1,234.56");
        assertThat(Money.format(1, "")).isEqualTo("$0.01");
        assertThat(Money.parseToCents(null)).isZero();
        assertThat(Money.parseToCents("")).isZero();
        assertThat(Money.parseToCents("abc")).isZero();
        assertThat(Money.parseToCents("$1,234.56")).isEqualTo(123_456);
        assertThat(Money.parseToCents("-2.5")).isEqualTo(-250);

        assertThat(Csv.row("a", "b,c", "quote\"here", null, "line\nbreak"))
                .isEqualTo("a,\"b,c\",\"quote\"\"here\",,\"line\nbreak\"\n");
        assertThat(Csv.table(List.of("H1", "H2"), List.of(List.of(1, 2)))).isEqualTo("H1,H2\n1,2\n");

        String json = Json.write(Map.of("key", "value"));
        assertThat(Json.readMap(json)).containsEntry("key", "value");
        assertThat(Json.readMap(null)).isEmpty();
        assertThat(Json.readMap("not json")).isEmpty();

        assertThat(Paging.offset(-1, 20)).isZero();
        assertThat(Paging.offset(2, 20)).isEqualTo(40);
        assertThat(Paging.pageCount(0, 20)).isEqualTo(1);
        assertThat(Paging.pageCount(41, 20)).isEqualTo(3);

        assertThat(Sha256.hex("hello".getBytes())).isEqualTo(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void passwordHashingAndPolicy() {
        String hash = PasswordHasher.hash(TestSupport.PASSWORD);
        assertThat(hash).startsWith("pbkdf2$" + PasswordHasher.ITERATIONS + "$");
        assertThat(PasswordHasher.verify(TestSupport.PASSWORD, hash)).isTrue();
        assertThat(PasswordHasher.verify("wrong-password", hash)).isFalse();
        assertThat(PasswordHasher.verify(null, hash)).isFalse();
        assertThat(PasswordHasher.verify(TestSupport.PASSWORD, null)).isFalse();
        assertThat(PasswordHasher.verify(TestSupport.PASSWORD, "plain")).isFalse();
        assertThat(PasswordHasher.verify(TestSupport.PASSWORD, "pbkdf2$bad$bad$bad")).isFalse();
        assertThatThrownBy(() -> PasswordHasher.hash("short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordHasher.validatePolicy("           "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
        PasswordHasher.validatePolicy(TestSupport.PASSWORD);
    }

    @Test
    void validatorsCheckPatientInput() {
        assertThat(Validators.required(" value ", "Field")).isEqualTo("value");
        assertThatThrownBy(() -> Validators.required(null, "Field")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field is required");
        Validators.optionalEmail(null);
        Validators.optionalEmail("a.b+c@example.co.uk");
        assertThatThrownBy(() -> Validators.optionalEmail("bad")).isInstanceOf(IllegalArgumentException.class);
        Validators.optionalPhone("+1 (555) 010-0199");
        assertThatThrownBy(() -> Validators.optionalPhone("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Validators.safe(null)).isEmpty();
        assertThat(Validators.safe("  x  ")).isEqualTo("x");

        assertThatThrownBy(() -> Validators.requirePatient(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("required");
        Validators.requirePatient(TestSupport.patient("Valid", "Patient"));
    }

    @Test
    void i18nSwitchesLanguageAndDirection() {
        I18n.setLanguage("en");
        assertThat(I18n.t("nav.dashboard")).isEqualTo("Dashboard");
        assertThat(I18n.rightToLeft()).isFalse();
        I18n.setLanguage("ar");
        assertThat(I18n.t("nav.dashboard")).isEqualTo("لوحة التحكم");
        assertThat(I18n.rightToLeft()).isTrue();
        assertThat(I18n.t("does.not.exist")).isEqualTo("does.not.exist");
        assertThat(I18n.t("login.title", "x")).isNotNull();
    }

    @Test
    void templatesExposeSpecialtyDefaultsAndFields() {
        TemplateService service = new TemplateService();
        assertThat(service.all()).hasSize(6);
        assertThat(service.byId("cardiology")).isPresent();
        assertThat(service.byId("missing")).isEmpty();
        assertThat(service.defaultFor("Cardiology").orElseThrow().id()).isEqualTo("cardiology");
        assertThat(service.defaultFor(null).orElseThrow().id()).isEqualTo("general");
        assertThat(service.defaultFor("Unknown specialty").orElseThrow().id()).isEqualTo("general");
        assertThat(service.specialtyForUser(null)).isEqualTo("General");

        User noSpecialty = new User(1, "u", "User", "hash", Role.DOCTOR, false, null, true, 0,
                null, null, Instant.now(), Instant.now());
        assertThat(service.specialtyForUser(noSpecialty)).isEqualTo("General");
        User cardiologist = new User(1, "u", "User", "hash", Role.DOCTOR, false, "Cardiology", true, 0,
                null, null, Instant.now(), Instant.now());
        assertThat(service.specialtyForUser(cardiologist)).isEqualTo("Cardiology");

        TemplateService.Template cardiology = service.byId("cardiology").orElseThrow();
        assertThat(cardiology.name()).isEqualTo("Cardiology");
        assertThat(cardiology.sections()).isNotEmpty();
        TemplateService.Field field = cardiology.sections().stream()
                .flatMap(section -> section.fields().stream())
                .filter(f -> f.type().equals("select"))
                .findFirst().orElseThrow();
        assertThat(field.options()).isNotEmpty();
        assertThat(field.textArea()).isFalse();
        TemplateService.Field textArea = cardiology.sections().stream()
                .flatMap(section -> section.fields().stream())
                .filter(TemplateService.Field::textArea)
                .findFirst().orElseThrow();
        assertThat(textArea.type()).isEqualTo("textarea");
    }

    @Test
    void sexEnumFallsBackToUnknown() {
        assertThat(Sex.from(null)).isEqualTo(Sex.UNKNOWN);
        assertThat(Sex.from("")).isEqualTo(Sex.UNKNOWN);
        assertThat(Sex.from("FEMALE")).isEqualTo(Sex.FEMALE);
        assertThat(Sex.MALE.label()).isEqualTo("Male");
    }
}
