package io.github.klisman99.collectorsauctionplatform.catalog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Safely validates and normalizes image bytes owned by the catalog module. */
final class CatalogImageProcessor {

  static final long MAX_IMAGE_PIXELS = 25_000_000;
  private static final int DISPLAY_MAX_EDGE = 2_000;
  private static final int THUMBNAIL_MAX_EDGE = 400;

  private CatalogImageProcessor() {}

  static ImageFormat imageFormat(byte[] bytes) {
    return ImageFormat.from(bytes)
        .orElseThrow(
            () ->
                CatalogApiException.invalidImage(
                    "Only JPEG, PNG, and WebP image bytes are accepted."));
  }

  static Renditions process(byte[] source) throws IOException {
    try (ImageInputStream input =
        ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new IOException("No image reader is available for the supplied bytes.");
      }

      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        validateDimensions(reader.getWidth(0), reader.getHeight(0));

        BufferedImage sourceImage = reader.read(0);
        if (sourceImage == null) {
          throw new IOException("The image content could not be decoded.");
        }
        return Renditions.from(sourceImage);
      } finally {
        reader.dispose();
      }
    }
  }

  private static void validateDimensions(int width, int height) {
    long pixelCount = (long) width * height;
    if (width < 1 || height < 1 || pixelCount > MAX_IMAGE_PIXELS) {
      throw CatalogApiException.invalidImage(
          "Images may contain at most 25 million pixels after decoding.");
    }
  }

  enum ImageFormat {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private final String contentType;

    ImageFormat(String contentType) {
      this.contentType = contentType;
    }

    String contentType() {
      return contentType;
    }

    private static Optional<ImageFormat> from(byte[] bytes) {
      if (isJpeg(bytes)) {
        return Optional.of(JPEG);
      }
      if (isPng(bytes)) {
        return Optional.of(PNG);
      }
      if (isWebp(bytes)) {
        return Optional.of(WEBP);
      }
      return Optional.empty();
    }

    private static boolean isJpeg(byte[] bytes) {
      return bytes.length >= 3
          && (bytes[0] & 0xff) == 0xff
          && (bytes[1] & 0xff) == 0xd8
          && (bytes[2] & 0xff) == 0xff;
    }

    private static boolean isPng(byte[] bytes) {
      return bytes.length >= 8
          && bytes[0] == (byte) 0x89
          && bytes[1] == 'P'
          && bytes[2] == 'N'
          && bytes[3] == 'G'
          && bytes[4] == 13
          && bytes[5] == 10
          && bytes[6] == 26
          && bytes[7] == 10;
    }

    private static boolean isWebp(byte[] bytes) {
      return bytes.length >= 12
          && bytes[0] == 'R'
          && bytes[1] == 'I'
          && bytes[2] == 'F'
          && bytes[3] == 'F'
          && bytes[8] == 'W'
          && bytes[9] == 'E'
          && bytes[10] == 'B'
          && bytes[11] == 'P';
    }
  }

  record Renditions(byte[] display, byte[] thumbnail, int displayWidth, int displayHeight) {

    private static Renditions from(BufferedImage source) throws IOException {
      BufferedImage display = scale(source, DISPLAY_MAX_EDGE);
      BufferedImage thumbnail = scale(source, THUMBNAIL_MAX_EDGE);
      return new Renditions(
          jpeg(display), jpeg(thumbnail), display.getWidth(), display.getHeight());
    }

    private static BufferedImage scale(BufferedImage source, int maximumEdge) {
      double scaleRatio =
          Math.min(1d, maximumEdge / (double) Math.max(source.getWidth(), source.getHeight()));
      int width = Math.max(1, (int) Math.round(source.getWidth() * scaleRatio));
      int height = Math.max(1, (int) Math.round(source.getHeight() * scaleRatio));

      BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = result.createGraphics();
      try {
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, width, height, null);
      } finally {
        graphics.dispose();
      }
      return result;
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      if (!ImageIO.write(image, "jpeg", output)) {
        throw new IOException("JPEG writer unavailable.");
      }
      return output.toByteArray();
    }
  }
}
