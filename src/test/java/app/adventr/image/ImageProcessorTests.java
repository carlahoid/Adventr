package app.adventr.image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ImageProcessorTests {

	private final ImageProcessor processor = new ImageProcessor();

	@Test
	void largeJpegIsScaledDownToTheMaximumEdge() {
		BufferedImage result = TestImages.read(this.processor.toJpeg(TestImages.jpeg(4000, 3000), 1600));

		assertThat(result.getWidth()).isEqualTo(1600);
		assertThat(result.getHeight()).isEqualTo(1200);
	}

	@Test
	void smallImagesKeepTheirSize() {
		BufferedImage result = TestImages.read(this.processor.toJpeg(TestImages.jpeg(300, 200), 1600));

		assertThat(result.getWidth()).isEqualTo(300);
		assertThat(result.getHeight()).isEqualTo(200);
	}

	@Test
	void exifOrientationIsAppliedAndMetadataIsStripped() {
		byte[] rotated = TestImages.jpegWithOrientation(400, 200, 6);
		assertThat(new String(rotated, StandardCharsets.ISO_8859_1)).contains("Exif");

		byte[] output = this.processor.toJpeg(rotated, 1600);
		BufferedImage result = TestImages.read(output);

		// 90° clockwise: the left (red) half ends up on top.
		assertThat(result.getWidth()).isEqualTo(200);
		assertThat(result.getHeight()).isEqualTo(400);
		assertThat(isReddish(result.getRGB(100, 50))).isTrue();
		assertThat(isReddish(result.getRGB(100, 350))).isFalse();
		assertThat(new String(output, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
	}

	@Test
	void squareCropIsCenteredAndExactlyTheRequestedSize() {
		// 3000 × 2000: left half red, right half blue; the centered 2000 × 2000 square keeps both.
		BufferedImage result = TestImages.read(this.processor.toSquareJpeg(TestImages.jpeg(3000, 2000), 256));

		assertThat(result.getWidth()).isEqualTo(256);
		assertThat(result.getHeight()).isEqualTo(256);
		assertThat(isReddish(result.getRGB(20, 128))).isTrue();
		assertThat(isReddish(result.getRGB(236, 128))).isFalse();
	}

	@Test
	void squareCropAppliesOrientationFirstAndScalesUpSmallImages() {
		byte[] output = this.processor.toSquareJpeg(TestImages.jpegWithOrientation(120, 60, 6), 256);
		BufferedImage result = TestImages.read(output);

		assertThat(result.getWidth()).isEqualTo(256);
		assertThat(result.getHeight()).isEqualTo(256);
		assertThat(new String(output, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
	}

	@Test
	void pngWithTransparencyAndWebpAreAccepted() {
		assertThat(TestImages.read(this.processor.toJpeg(TestImages.png(120, 80), 1600)).getWidth()).isEqualTo(120);
		assertThat(TestImages.read(this.processor.toJpeg(TestImages.WEBP, 1600)).getWidth()).isEqualTo(1);
	}

	@Test
	void outputIsAJpeg() {
		byte[] output = this.processor.toJpeg(TestImages.png(50, 50), 1600);

		assertThat(ImageProcessor.formatOf(output)).isEqualTo("jpeg");
	}

	@Test
	void nonImagesAreRejectedByContent() {
		assertThatExceptionOfType(ImageRejectedException.class)
			.isThrownBy(() -> this.processor.toJpeg(TestImages.PDF, 1600))
			.withMessage("Only JPEG, PNG, and WebP images are supported.");
		assertThatExceptionOfType(ImageRejectedException.class)
			.isThrownBy(() -> this.processor.toJpeg(new byte[0], 1600));
	}

	@Test
	void truncatedImagesAreRejected() {
		byte[] jpeg = TestImages.jpeg(200, 200);
		byte[] truncated = java.util.Arrays.copyOf(jpeg, 20);

		assertThatExceptionOfType(ImageRejectedException.class)
			.isThrownBy(() -> this.processor.toJpeg(truncated, 1600))
			.withMessageContaining("couldn't be read");
	}

	private static boolean isReddish(int rgb) {
		Color color = new Color(rgb);
		return color.getRed() > 200 && color.getBlue() < 80;
	}

}
