module org.amrosman.jsmartclinic {
    requires javafx.controls;
    requires javafx.fxml;

    requires java.sql;
    requires java.desktop;
    requires java.logging;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome6;
    requires org.xerial.sqlitejdbc;
    requires org.slf4j;
    requires org.apache.pdfbox;
    requires com.fasterxml.jackson.databind;

    opens org.amrosman.jsmartclinic to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.auth to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.dashboard to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.patients to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.appointments to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.encounters to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.prescriptions to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.labs to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.billing to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.reports to javafx.fxml;
    opens org.amrosman.jsmartclinic.ui.settings to javafx.fxml;

    opens org.amrosman.jsmartclinic.domain to com.fasterxml.jackson.databind;

    exports org.amrosman.jsmartclinic;
    exports org.amrosman.jsmartclinic.domain;
}
