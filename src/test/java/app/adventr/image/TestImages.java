package app.adventr.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import javax.imageio.ImageIO;

/**
 * Image files for upload tests, generated in memory.
 */
public final class TestImages {

	/** A 1 × 1 lossless WebP. */
	public static final byte[] WEBP = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

	/** A PDF, whatever name it is uploaded under. */
	public static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n".getBytes();

	private TestImages() {
	}

	/**
	 * A JPEG with the left half red and the right half blue, so that orientation is visible.
	 */
	public static byte[] jpeg(int width, int height) {
		return encode(twoColor(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
	}

	public static byte[] png(int width, int height) {
		return encode(twoColor(width, height, BufferedImage.TYPE_INT_ARGB), "png");
	}

	/**
	 * A JPEG with an EXIF APP1 segment that holds only the given orientation tag.
	 */
	public static byte[] jpegWithOrientation(int width, int height, int orientation) {
		byte[] jpeg = jpeg(width, height);
		ByteBuffer tiff = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
		tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8); // header, IFD0 at offset 8
		tiff.putShort((short) 1); // one entry
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
		tiff.putInt(0); // no next IFD
		byte[] exif = new byte[6 + tiff.capacity()];
		System.arraycopy("Exif\0\0".getBytes(), 0, exif, 0, 6);
		System.arraycopy(tiff.array(), 0, exif, 6, tiff.capacity());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2); // SOI
		out.write(0xff);
		out.write(0xe1); // APP1
		int length = exif.length + 2;
		out.write(length >> 8);
		out.write(length & 0xff);
		out.write(exif, 0, exif.length);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	public static BufferedImage read(byte[] data) {
		try {
			return ImageIO.read(new ByteArrayInputStream(data));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static BufferedImage twoColor(int width, int height, int type) {
		BufferedImage image = new BufferedImage(width, height, type);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, width / 2, height);
		g.setColor(Color.BLUE);
		g.fillRect(width / 2, 0, width - width / 2, height);
		g.dispose();
		return image;
	}

	private static byte[] encode(BufferedImage image, String format) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, format, out);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

}
