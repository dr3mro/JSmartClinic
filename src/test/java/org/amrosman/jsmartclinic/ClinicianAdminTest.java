package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.EncounterStatus;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.amrosman.jsmartclinic.service.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** C-00: a solo administrator can opt in to seeing patients. */
class ClinicianAdminTest {

    @TempDir
    Path temp;

    @Test
    void clinicianAdministratorCanStartSaveAndSignEncounters() {
        AppContext context = TestSupport.context(temp);
        User admin = context.auth().createFirstAdministrator("Test Clinic", "Dr Admin", "admin",
                TestSupport.PASSWORD, true, "General");
        Patient patient = TestSupport.createPatient(context, "Ada", "Apple");

        assertThat(admin.clinician()).isTrue();
        assertThat(admin.specialty()).isEqualTo("General");
        assertThat(context.session().can(Permission.ENCOUNTERS)).isTrue();
        assertThat(context.session().can(Permission.PRESCRIBE)).isTrue();
        assertThat(context.session().can(Permission.LABS)).isTrue();
        assertThat(context.session().can(Permission.VITALS)).isTrue();
        assertThat(context.users().providers()).extracting(User::id).contains(admin.id());

        Encounter draft = context.encounters().start(patient.id(), null, null, null);
        assertThat(draft.status()).isEqualTo(EncounterStatus.DRAFT);
        Encounter saved = context.encounters().save(TestSupport.withContent(draft, "Fever",
                "Two days", "Temperature 38.2", "Viral illness", "Fluids and rest"));
        assertThat(saved.id()).isEqualTo(draft.id());
        Encounter signed = context.encounters().sign(saved.id());
        assertThat(signed.status()).isEqualTo(EncounterStatus.SIGNED);
        assertThat(signed.signedAt()).isNotNull();
    }

    @Test
    void plainAdministratorStillCannotWriteEncounters() {
        AppContext context = TestSupport.adminContext(temp);
        Patient patient = TestSupport.createPatient(context, "Bea", "Brown");

        assertThat(context.session().current().clinician()).isFalse();
        assertThat(context.session().can(Permission.ENCOUNTERS)).isFalse();
        assertThatThrownBy(() -> context.encounters().start(patient.id(), null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
