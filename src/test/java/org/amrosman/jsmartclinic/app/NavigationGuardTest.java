package org.amrosman.jsmartclinic.app;

import org.amrosman.jsmartclinic.ui.encounters.EncounterEditorController;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** C-07: views can veto navigation while they hold unsaved work. */
class NavigationGuardTest {

    @Test
    void unguardedControllersNavigateImmediately() {
        AtomicBoolean proceeded = new AtomicBoolean();

        Navigator.requestLeave(new Object(), true, () -> proceeded.set(true));

        assertThat(proceeded).isTrue();
    }

    @Test
    void aGuardCanHoldNavigationUntilItIsReady() {
        AtomicBoolean proceeded = new AtomicBoolean();
        AtomicBoolean asked = new AtomicBoolean();
        Navigator.CanLeave guard = (interactive, proceed) -> asked.set(interactive);

        Navigator.requestLeave(guard, true, () -> proceeded.set(true));

        assertThat(asked).isTrue();
        assertThat(proceeded).isFalse();
    }

    @Test
    void aGuardCanLetNavigationProceedAndSeesTheLockMode() {
        AtomicBoolean proceeded = new AtomicBoolean();
        AtomicBoolean interactive = new AtomicBoolean(true);
        Navigator.CanLeave guard = (askedInteractive, proceed) -> {
            interactive.set(askedInteractive);
            proceed.run();
        };

        Navigator.requestLeave(guard, false, () -> proceeded.set(true));

        assertThat(interactive).isFalse();
        assertThat(proceeded).isTrue();
    }

    @Test
    void theEncounterEditorIsAGuardedView() {
        assertThat(Navigator.CanLeave.class.isAssignableFrom(EncounterEditorController.class)).isTrue();
    }
}
