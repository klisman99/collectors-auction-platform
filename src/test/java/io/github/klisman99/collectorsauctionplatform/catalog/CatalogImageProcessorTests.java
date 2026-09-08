package io.github.klisman99.collectorsauctionplatform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CatalogImageProcessorTests {

  @Test
  void createsPrivateJpegRenditionsForASupportedImage() throws IOException {
    BufferedImage source = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
    ImageIO.write(source, "png", encoded);

    CatalogImageProcessor.Renditions renditions =
        CatalogImageProcessor.process(encoded.toByteArray());

    assertThat(renditions.displayWidth()).isEqualTo(100);
    assertThat(renditions.displayHeight()).isEqualTo(50);
    assertThat(renditions.display()).isNotEmpty();
    assertThat(renditions.thumbnail()).isNotEmpty();
  }

  @Test
  void rejectsOversizedImageBeforeDecodingItsPixelBuffer() {
    byte[] oversizedPngHeader = pngHeader(10_000, 10_000);

    assertThatThrownBy(() -> CatalogImageProcessor.process(oversizedPngHeader))
        .isInstanceOf(CatalogApiException.class)
        .hasMessageContaining("25 million pixels");
  }

  private static byte[] pngHeader(int width, int height) {
    ByteBuffer png = ByteBuffer.allocate(33);
    png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
    png.putInt(13);
    png.put(new byte[] {'I', 'H', 'D', 'R'});
    png.putInt(width);
    png.putInt(height);
    png.put(new byte[] {8, 2, 0, 0, 0});

    CRC32 checksum = new CRC32();
    checksum.update(png.array(), 12, 17);
    png.putInt((int) checksum.getValue());
    return png.array();
  }
}
