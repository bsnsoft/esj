import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Writes the macOS icon of ESJ Viewer.app from the one source of the icon, esj.svg beside
 * this file.
 *
 *     java dist/desktop/Icon.java dist/desktop/esj.svg <out.icns> [<preview directory>]
 *
 * with a JDK of 17 or later. The SVG is read, not copied: its paths are drawn with Java2D at
 * every size an .icns carries, from 16 to 1024 pixels, as macOS wants an application icon —
 * the sheet at seventy per cent of a square tile that fills the whole image, in the panel
 * colour of the page, because macOS 26 frames an icon that does not fill its tile in grey —
 * and the PNGs are put under the four-letter codes of the format. Nothing else is needed: no
 * rasterizer, no iconutil. A preview directory, where one is named, gets the PNGs as well.
 *
 * The SVG this reads is the one the Linux packages install as it stands. It uses the path
 * commands M, H, V, L, C, Z and a, in absolute and relative form, and fill, stroke,
 * stroke-width, stroke-linejoin and stroke-linecap, on a path or on the group around it;
 * anything else in it is refused rather than drawn wrong.
 */
public final class Icon {

    /** The colour of the tile: --panel of the page's style sheet. */
    private static final Color TILE = new Color(0xf6, 0xf8, 0xfa);

    /** How much of the tile the sheet takes. */
    private static final double SHEET = 0.7;

    /** The entries of the icon file: the code of each, and the pixels it holds. */
    private static final String[][] ENTRIES = {
        {"icp4", "16"}, {"icp5", "32"}, {"icp6", "64"}, {"ic07", "128"}, {"ic08", "256"},
        {"ic09", "512"}, {"ic10", "1024"}, {"ic11", "32"}, {"ic12", "64"}, {"ic13", "256"},
        {"ic14", "512"},
    };

    private static final Pattern TOKEN =
            Pattern.compile("[A-Za-z]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    private Icon() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println("usage: java Icon.java <esj.svg> <out.icns> [<preview directory>]");
            System.exit(2);
        }
        List<Shape> shapes = read(Path.of(args[0]));
        ByteArrayOutputStream icns = new ByteArrayOutputStream();
        for (String[] entry : ENTRIES) {
            int size = Integer.parseInt(entry[1]);
            byte[] png = png(shapes, size);
            icns.write(entry[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            length(icns, png.length + 8);
            icns.write(png);
            if (args.length == 3) {
                Path preview = Files.createDirectories(Path.of(args[2]));
                Files.write(preview.resolve("esj-" + size + ".png"), png);
            }
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write("icns".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        length(file, icns.size() + 8);
        icns.writeTo(file);
        Files.write(Path.of(args[1]), file.toByteArray());
        System.out.println("Icon: " + args[1] + ", " + ENTRIES.length + " images, "
                + file.size() + " bytes");
    }

    private static void length(ByteArrayOutputStream out, int length) {
        out.write(length >>> 24);
        out.write(length >>> 16);
        out.write(length >>> 8);
        out.write(length);
    }

    /** Draws the icon at one size and returns it as a PNG. */
    private static byte[] png(List<Shape> shapes, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(TILE);
        g.fillRect(0, 0, size, size);
        double scale = size * SHEET / 256.0;
        AffineTransform sheet = new AffineTransform();
        sheet.translate(size * (1 - SHEET) / 2, size * (1 - SHEET) / 2);
        sheet.scale(scale, scale);
        g.transform(sheet);
        for (Shape shape : shapes) {
            if (shape.fill() != null) {
                g.setColor(shape.fill());
                g.fill(shape.path());
            }
            if (shape.stroke() != null) {
                g.setColor(shape.stroke());
                g.setStroke(new BasicStroke(shape.width(), shape.cap(), shape.join()));
                g.draw(shape.path());
            }
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** One path of the SVG with its paint. */
    private record Shape(Path2D path, Color fill, Color stroke, float width, int cap, int join) {
    }

    /** Reads the paths of the SVG, with the paint each inherits from the group around it. */
    private static List<Shape> read(Path svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Element root = factory.newDocumentBuilder().parse(svg.toFile()).getDocumentElement();
        if (!"0 0 256 256".equals(root.getAttribute("viewBox"))) {
            throw new IllegalArgumentException("the icon is drawn on a viewBox of 0 0 256 256");
        }
        List<Shape> shapes = new ArrayList<>();
        walk(root, shapes);
        return shapes;
    }

    private static void walk(Element element, List<Shape> shapes) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element child)) {
                continue;
            }
            switch (child.getTagName()) {
                case "g" -> walk(child, shapes);
                case "path" -> shapes.add(new Shape(path(child.getAttribute("d")),
                        paint(attribute(child, "fill", "#000")),
                        paint(attribute(child, "stroke", "none")),
                        Float.parseFloat(attribute(child, "stroke-width", "1")),
                        cap(attribute(child, "stroke-linecap", "butt")),
                        join(attribute(child, "stroke-linejoin", "miter"))));
                default -> throw new IllegalArgumentException("the icon draws no <"
                        + child.getTagName() + ">");
            }
        }
    }

    /** Returns an attribute of an element, or of the nearest group around it that has one. */
    private static String attribute(Element element, String name, String fallback) {
        for (Node node = element; node instanceof Element at; node = node.getParentNode()) {
            if (at.hasAttribute(name)) {
                return at.getAttribute(name);
            }
        }
        return fallback;
    }

    private static Color paint(String value) {
        if ("none".equals(value)) {
            return null;
        }
        if ("#fff".equals(value)) {
            return Color.WHITE;
        }
        if (value.matches("#[0-9a-fA-F]{6}")) {
            return new Color(Integer.parseInt(value.substring(1), 16));
        }
        throw new IllegalArgumentException("the icon paints with #fff or #rrggbb, not " + value);
    }

    private static int cap(String value) {
        return switch (value) {
            case "round" -> BasicStroke.CAP_ROUND;
            case "square" -> BasicStroke.CAP_SQUARE;
            default -> BasicStroke.CAP_BUTT;
        };
    }

    private static int join(String value) {
        return switch (value) {
            case "round" -> BasicStroke.JOIN_ROUND;
            case "bevel" -> BasicStroke.JOIN_BEVEL;
            default -> BasicStroke.JOIN_MITER;
        };
    }

    /** Builds the path of a {@code d} attribute. */
    private static Path2D path(String d) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(d);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        Path2D.Double path = new Path2D.Double();
        double x = 0;
        double y = 0;
        double startX = 0;
        double startY = 0;
        char command = 0;
        int i = 0;
        while (i < tokens.size()) {
            String token = tokens.get(i);
            if (Character.isLetter(token.charAt(0))) {
                command = token.charAt(0);
                i++;
                if (command == 'Z' || command == 'z') {
                    path.closePath();
                    x = startX;
                    y = startY;
                    continue;
                }
            }
            boolean relative = Character.isLowerCase(command);
            double dx = relative ? x : 0;
            double dy = relative ? y : 0;
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    x = dx + number(tokens, i++);
                    y = dy + number(tokens, i++);
                    path.moveTo(x, y);
                    startX = x;
                    startY = y;
                    command = relative ? 'l' : 'L';
                }
                case 'L' -> {
                    x = dx + number(tokens, i++);
                    y = dy + number(tokens, i++);
                    path.lineTo(x, y);
                }
                case 'H' -> {
                    x = dx + number(tokens, i++);
                    path.lineTo(x, y);
                }
                case 'V' -> {
                    y = dy + number(tokens, i++);
                    path.lineTo(x, y);
                }
                case 'C' -> {
                    double x1 = dx + number(tokens, i++);
                    double y1 = dy + number(tokens, i++);
                    double x2 = dx + number(tokens, i++);
                    double y2 = dy + number(tokens, i++);
                    x = dx + number(tokens, i++);
                    y = dy + number(tokens, i++);
                    path.curveTo(x1, y1, x2, y2, x, y);
                }
                case 'A' -> {
                    double rx = number(tokens, i++);
                    double ry = number(tokens, i++);
                    double rotation = number(tokens, i++);
                    boolean large = number(tokens, i++) != 0;
                    boolean sweep = number(tokens, i++) != 0;
                    double toX = dx + number(tokens, i++);
                    double toY = dy + number(tokens, i++);
                    if (rotation != 0) {
                        throw new IllegalArgumentException("the icon draws no rotated arc");
                    }
                    arc(path, x, y, rx, ry, large, sweep, toX, toY);
                    x = toX;
                    y = toY;
                }
                default -> throw new IllegalArgumentException("the icon draws no path command "
                        + command);
            }
        }
        return path;
    }

    private static double number(List<String> tokens, int index) {
        return Double.parseDouble(tokens.get(index));
    }

    /**
     * Appends an elliptical arc of the SVG endpoint form as cubic curves, converted to the
     * centre form of SVG 1.1, appendix F.6.5, and split into quarters or less.
     */
    private static void arc(Path2D path, double x1, double y1, double rx, double ry,
                            boolean large, boolean sweep, double x2, double y2) {
        if (rx == 0 || ry == 0 || (x1 == x2 && y1 == y2)) {
            path.lineTo(x2, y2);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double hx = (x1 - x2) / 2;
        double hy = (y1 - y2) / 2;
        double lambda = hx * hx / (rx * rx) + hy * hy / (ry * ry);
        if (lambda > 1) {
            rx *= Math.sqrt(lambda);
            ry *= Math.sqrt(lambda);
        }
        double numerator = rx * rx * ry * ry - rx * rx * hy * hy - ry * ry * hx * hx;
        double denominator = rx * rx * hy * hy + ry * ry * hx * hx;
        double factor = Math.sqrt(Math.max(0, numerator / denominator)) * (large == sweep ? -1 : 1);
        double cxp = factor * rx * hy / ry;
        double cyp = -factor * ry * hx / rx;
        double cx = cxp + (x1 + x2) / 2;
        double cy = cyp + (y1 + y2) / 2;
        double start = Math.atan2((hy - cyp) / ry, (hx - cxp) / rx);
        double end = Math.atan2((-hy - cyp) / ry, (-hx - cxp) / rx);
        double extent = end - start;
        if (sweep && extent < 0) {
            extent += 2 * Math.PI;
        } else if (!sweep && extent > 0) {
            extent -= 2 * Math.PI;
        }
        int segments = (int) Math.ceil(Math.abs(extent) / (Math.PI / 2) - 1e-9);
        double step = extent / segments;
        double k = 4.0 / 3.0 * Math.tan(step / 4);
        double angle = start;
        for (int s = 0; s < segments; s++) {
            double cos1 = Math.cos(angle);
            double sin1 = Math.sin(angle);
            double cos2 = Math.cos(angle + step);
            double sin2 = Math.sin(angle + step);
            path.curveTo(cx + rx * (cos1 - k * sin1), cy + ry * (sin1 + k * cos1),
                    cx + rx * (cos2 + k * sin2), cy + ry * (sin2 - k * cos2),
                    cx + rx * cos2, cy + ry * sin2);
            angle += step;
        }
    }
}
