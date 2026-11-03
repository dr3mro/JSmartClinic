package org.amrosman.jsmartclinic.ui.components;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * A form made of titled sections whose fields (label above control) flow into two
 * columns, or one column when the form is narrower than {@link #BREAKPOINT}.
 */
public final class ResponsiveForm extends VBox {

    public static final double BREAKPOINT = 620;
    private static final String WIDE = "responsive-form-wide";

    private final List<GridPane> grids = new ArrayList<>();
    private final List<List<Node>> fields = new ArrayList<>();
    private int columns = -1;

    public ResponsiveForm() {
        super(18);
        getStyleClass().add("responsive-form");
        widthProperty().addListener((obs, old, width) -> relayout(width.doubleValue()));
    }

    /** A label stacked above its control. */
    public static VBox field(String label, Node control) {
        Label caption = new Label(label);
        caption.getStyleClass().add("form-label");
        caption.setLabelFor(control);
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        VBox box = new VBox(5, caption, control);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Marks a field so it always spans the full width of its section. */
    public static Node wide(Node field) {
        field.getProperties().put(WIDE, Boolean.TRUE);
        return field;
    }

    public ResponsiveForm section(String title, Node... sectionFields) {
        Label heading = new Label(title.toUpperCase(java.util.Locale.ROOT));
        heading.getStyleClass().add("section-label");
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setMaxWidth(Double.MAX_VALUE);
        grids.add(grid);
        fields.add(List.of(sectionFields));
        VBox section = new VBox(10, heading, grid);
        section.getStyleClass().add("form-section");
        getChildren().add(section);
        columns = -1;
        relayout(getWidth());
        return this;
    }

    private void relayout(double width) {
        int wanted = width > 0 && width < BREAKPOINT ? 1 : 2;
        if (wanted == columns) {
            return;
        }
        columns = wanted;
        for (int s = 0; s < grids.size(); s++) {
            GridPane grid = grids.get(s);
            grid.getChildren().clear();
            grid.getColumnConstraints().clear();
            for (int c = 0; c < columns; c++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(100.0 / columns);
                cc.setHgrow(Priority.ALWAYS);
                cc.setFillWidth(true);
                grid.getColumnConstraints().add(cc);
            }
            int row = 0;
            int col = 0;
            for (Node field : fields.get(s)) {
                boolean wide = Boolean.TRUE.equals(field.getProperties().get(WIDE));
                if (wide && col != 0) {
                    row++;
                    col = 0;
                }
                grid.add(field, col, row, wide ? columns : 1, 1);
                GridPane.setHgrow(field, Priority.ALWAYS);
                if (wide) {
                    row++;
                } else if (++col == columns) {
                    col = 0;
                    row++;
                }
            }
        }
    }

    /** Wraps this form in a scroll pane and sizes the dialog so it always fits on screen. */
    public ScrollPane fitInto(Dialog<?> dialog, double preferredWidth, double preferredHeight) {
        ScrollPane scroll = new ScrollPane(this);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("dialog-scroll");

        Window owner = Window.getWindows().stream().filter(Window::isFocused).findFirst()
                .orElse(Window.getWindows().stream().findFirst().orElse(null));
        if (owner != null && dialog.getOwner() == null) {
            dialog.initOwner(owner);
        }
        Rectangle2D bounds = owner == null ? Screen.getPrimary().getVisualBounds()
                : Screen.getScreensForRectangle(owner.getX(), owner.getY(),
                        Math.max(1, owner.getWidth()), Math.max(1, owner.getHeight()))
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();

        double maxHeight = bounds.getHeight() * 0.9;
        // Leave room for the dialog header and the button bar (~200px).
        scroll.setPrefViewportHeight(Math.max(240, Math.min(preferredHeight, maxHeight - 200)));
        dialog.getDialogPane().setPrefWidth(Math.min(preferredWidth, bounds.getWidth() * 0.92));
        dialog.getDialogPane().setMinWidth(Math.min(380, bounds.getWidth() * 0.92));
        dialog.getDialogPane().setMaxHeight(maxHeight);
        dialog.setResizable(true);
        return scroll;
    }
}
