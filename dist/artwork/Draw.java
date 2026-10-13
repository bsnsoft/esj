import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Draws the images the templates of this directory name, and writes them beside this file.
 *
 *     java dist/artwork/Draw.java
 *
 * run from the root of the repository with a JDK of 17 or later. Nothing here is anybody's
 * picture: the logo is three squares and the sheet is a hairline, set pixel by pixel rather
 * than drawn by a rasteriser, so that the pictures are the same wherever this runs. The
 * encodings are the JDK's own writers; a colour profile, where an image carries one, is the
 * sRGB profile esj-render already embeds in every rendering, unaltered.
 */
public final class Draw {

    /** The colour of the logo and of the rule: the brand colour of the example templates. */
    private static final int[] BRAND = {0x12, 0x3a, 0x63};

    /** The same colour as one grey level, by the weights of ITU-R BT.601. */
    private static final int BRAND_GREY = (299 * BRAND[0] + 587 * BRAND[1] + 114 * BRAND[2]) / 1000;

    /** The width and the height of the logo, in pixels. */
    private static final int LOGO_WIDTH = 160;
    private static final int LOGO_HEIGHT = 48;

    /** The width and the height of the sheet: A4 at one pixel per five points. */
    private static final int SHEET_WIDTH = 119;
    private static final int SHEET_HEIGHT = 168;

    /** The colour profile, from the resources of esj-render. */
    private static final Path PROFILE =
            Path.of("esj-render/src/main/resources/de/bsnsoft/esj/render/icc/sRGB2014.icc");

    /** Where the images go: beside this file. */
    private static final Path HERE = Path.of("dist/artwork");

    public static void main(String[] args) throws IOException {
        byte[] profile = Files.readAllBytes(PROFILE);

        write("logo.jpg", jpeg(logo(BufferedImage.TYPE_INT_RGB), false));
        write("logo-icc.png", withChunk(png(logo(BufferedImage.TYPE_INT_RGB)), "iCCP",
                iccp(profile)));
        write("logo-alpha.png", png(logo(BufferedImage.TYPE_INT_ARGB)));

        write("sheet.jpg", jpeg(sheet(BufferedImage.TYPE_INT_RGB), false));
        write("sheet-grey.jpg", jpeg(sheet(BufferedImage.TYPE_BYTE_GRAY), true));
        write("sheet-icc.jpg", withApp2(jpeg(sheet(BufferedImage.TYPE_INT_RGB), false), profile));
        write("sheet-icc.png", withChunk(png(sheet(BufferedImage.TYPE_INT_RGB)), "iCCP",
                iccp(profile)));
        write("sheet-grey8.png", png(sheet(BufferedImage.TYPE_BYTE_GRAY)));
        write("sheet-srgb.png", withChunk(png(sheet(BufferedImage.TYPE_INT_RGB)), "sRGB",
                new byte[] {0}));

        write("sheet-grey1.png", png(sheet(BufferedImage.TYPE_BYTE_BINARY)));
        write("sheet-grey16.png", png(sheet(BufferedImage.TYPE_USHORT_GRAY)));
        write("logo-grey-alpha.png", png(logo(greyWithAlpha(LOGO_WIDTH, LOGO_HEIGHT))));
    }

    /** An image in grey with an alpha channel, which BufferedImage has no type for. */
    private static BufferedImage greyWithAlpha(int width, int height) {
        ColorModel model = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY),
                true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
        return new BufferedImage(model, model.createCompatibleWritableRaster(width, height),
                false, null);
    }

    /** The logo: three squares of the brand colour, the middle one open. */
    private static BufferedImage logo(int type) {
        return logo(new BufferedImage(LOGO_WIDTH, LOGO_HEIGHT, type));
    }

    /** The logo, drawn into an image; the paper is transparent where it has an alpha channel. */
    private static BufferedImage logo(BufferedImage image) {
        boolean alpha = image.getColorModel().hasAlpha();
        fill(image, 0, 0, LOGO_WIDTH, LOGO_HEIGHT, false, alpha);
        for (int square = 0; square < 3; square++) {
            int x = 4 + square * 56;
            fill(image, x, 4, 40, 40, true, alpha);
            if (square == 1) {
                fill(image, x + 4, 8, 32, 32, false, alpha);
            }
        }
        return image;
    }

    /** The sheet: white paper with a hairline of the brand colour across its head. */
    private static BufferedImage sheet(int type) {
        BufferedImage image = new BufferedImage(SHEET_WIDTH, SHEET_HEIGHT, type);
        fill(image, 0, 0, SHEET_WIDTH, SHEET_HEIGHT, false, false);
        fill(image, 11, 17, SHEET_WIDTH - 22, 1, true, false);
        return image;
    }

    /**
     * Sets a rectangle of samples: the brand colour, or the paper — white, or nothing at all
     * in an image with an alpha channel. One bit of grey is black or white, sixteen bits are
     * the eight of the others spread over the wider range.
     */
    private static void fill(BufferedImage image, int x, int y, int width, int height,
                             boolean ink, boolean alpha) {
        WritableRaster raster = image.getRaster();
        int bits = raster.getSampleModel().getSampleSize(0);
        int[] sample = switch (raster.getNumBands()) {
            case 1 -> new int[] {bits == 1 ? (ink ? 0 : 1)
                    : (ink ? BRAND_GREY : 255) * (bits == 16 ? 257 : 1)};
            case 2 -> new int[] {ink ? BRAND_GREY : 255, ink || !alpha ? 255 : 0};
            case 3 -> ink ? BRAND.clone() : new int[] {255, 255, 255};
            case 4 -> ink ? new int[] {BRAND[0], BRAND[1], BRAND[2], 255}
                    : new int[] {255, 255, 255, alpha ? 0 : 255};
            default -> throw new IllegalArgumentException("bands: " + raster.getNumBands());
        };
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                raster.setPixel(column, row, sample);
            }
        }
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) {
            throw new IOException("this JDK writes no PNG");
        }
        return bytes.toByteArray();
    }

    /** A JPEG at a fixed quality; a progressive one where asked. */
    private static byte[] jpeg(BufferedImage image, boolean progressive) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.9f);
        if (progressive) {
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** The data of an iCCP chunk: a name, the compression method and the deflated profile. */
    private static byte[] iccp(byte[] profile) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write("sRGB2014".getBytes(StandardCharsets.ISO_8859_1));
        bytes.write(0);
        bytes.write(0);
        try (DeflaterOutputStream deflated = new DeflaterOutputStream(bytes)) {
            deflated.write(profile);
        }
        return bytes.toByteArray();
    }

    /** Puts a chunk right after the header of a PNG, where both chunks used here belong. */
    private static byte[] withChunk(byte[] png, String type, byte[] data) {
        int afterHeader = 8 + 4 + 4 + 13 + 4;
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(png, 0, afterHeader);
        int length = data.length;
        bytes.writeBytes(new byte[] {(byte) (length >>> 24), (byte) (length >>> 16),
                (byte) (length >>> 8), (byte) length});
        bytes.writeBytes(name);
        bytes.writeBytes(data);
        long sum = crc.getValue();
        bytes.writeBytes(new byte[] {(byte) (sum >>> 24), (byte) (sum >>> 16),
                (byte) (sum >>> 8), (byte) sum});
        bytes.write(png, afterHeader, png.length - afterHeader);
        return bytes.toByteArray();
    }

    /** Puts the profile into an APP2 segment after the APP0 segment the JDK writes first. */
    private static byte[] withApp2(byte[] jpeg, byte[] profile) {
        if ((jpeg[2] & 0xff) != 0xff || (jpeg[3] & 0xff) != 0xe0) {
            throw new IllegalStateException("the JPEG does not begin with an APP0 segment");
        }
        int afterApp0 = 4 + ((jpeg[4] & 0xff) << 8 | jpeg[5] & 0xff);
        byte[] marker = "ICC_PROFILE".getBytes(StandardCharsets.ISO_8859_1);
        int length = 2 + marker.length + 1 + 2 + profile.length;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(jpeg, 0, afterApp0);
        bytes.writeBytes(new byte[] {(byte) 0xff, (byte) 0xe2, (byte) (length >>> 8),
                (byte) length});
        bytes.writeBytes(marker);
        bytes.writeBytes(new byte[] {0, 1, 1});
        bytes.writeBytes(profile);
        bytes.write(jpeg, afterApp0, jpeg.length - afterApp0);
        return bytes.toByteArray();
    }

    private static void write(String name, byte[] bytes) throws IOException {
        Files.write(HERE.resolve(name), bytes);
        System.out.println(HERE.resolve(name) + " (" + bytes.length + " bytes)");
    }
}
