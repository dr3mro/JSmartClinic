package org.amrosman.jsmartclinic;

import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Static FXML contract checks that do not need the JavaFX toolkit: every view parses, its
 * controller is a public no-arg class, and referenced ids/handlers actually exist.
 */
class FxmlContractTest {

    @Test
    void everyViewParsesAndMatchesItsController() throws Exception {
        List<Path> views = views();
        assertThat(views).isNotEmpty();

        for (Path view : views) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(view.toFile());
            Element root = document.getDocumentElement();
            String controllerName = root.getAttribute("fx:controller");
            assertThat(controllerName).as("controller in %s", view.getFileName()).isNotBlank();
            Class<?> controller = Class.forName(controllerName);
            assertThat(Modifier.isPublic(controller.getModifiers()))
                    .as("%s must be public for Scene Builder", controllerName).isTrue();
            assertThat(controller.getConstructor()).as("%s needs a public no-arg constructor", controllerName)
                    .isNotNull();

            Set<String> fields = fieldNames(controller);
            Set<String> methods = methodNames(controller);
            NodeList all = document.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                Node node = all.item(i);
                if (!(node instanceof Element element)) {
                    continue;
                }
                String id = element.getAttribute("fx:id");
                if (!id.isBlank()) {
                    assertThat(fields).as("fx:id '%s' in %s", id, view.getFileName()).contains(id);
                }
                assertThatHandlerMethods(element, methods, view);
            }
        }
    }

    @Test
    void everyFontAwesomeIconLiteralExists() throws Exception {
        Set<String> literals = new TreeSet<>();
        for (Path view : views()) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(view.toFile());
            NodeList icons = document.getElementsByTagName("FontIcon");
            for (int i = 0; i < icons.getLength(); i++) {
                String literal = ((Element) icons.item(i)).getAttribute("iconLiteral");
                if (!literal.isBlank()) {
                    literals.add(literal);
                }
            }
        }

        assertThat(literals).isNotEmpty();
        for (String literal : literals) {
            assertThatCode(() -> FontAwesomeSolid.findByDescription(literal))
                    .as("icon literal '%s' used by FXML", literal)
                    .doesNotThrowAnyException();
        }
    }

    private void assertThatHandlerMethods(Element element, Set<String> methods, Path view) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName();
            if (!name.startsWith("on") || !attribute.getNodeValue().startsWith("#")) {
                continue;
            }
            String method = attribute.getNodeValue().substring(1);
            assertThat(methods).as("handler '%s' referenced from %s", method, view.getFileName()).contains(method);
        }
    }

    private Set<String> fieldNames(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                names.add(field.getName());
            }
        }
        return names;
    }

    private Set<String> methodNames(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private List<Path> views() throws Exception {
        URL viewsUrl = getClass().getClassLoader().getResource("org/amrosman/jsmartclinic/views");
        assertThat(viewsUrl).isNotNull();
        Path root = Path.of(viewsUrl.toURI());
        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(path -> path.toString().endsWith(".fxml")).forEach(result::add);
        }
        URL hello = getClass().getClassLoader().getResource("org/amrosman/jsmartclinic/hello-view.fxml");
        assertThat(hello).isNotNull();
        result.add(Path.of(hello.toURI()));
        result.sort(Path::compareTo);
        return result;
    }
}
