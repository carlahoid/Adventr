package app.adventr.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Component;

/**
 * Turns an uploaded file into a clean JPEG: the type is checked by content (magic bytes), the
 * EXIF orientation is applied, all metadata is dropped (the output is re-encoded from pixels),
 * and the image is scaled down to fit a maximum edge length, or cropped to a square. Knows
 * nothing about where images are stored, so that every kind of upload can share it.
 */
@Component
public class ImageProcessor {

	/** Larger images are refused before decoding, however small the file is. */
	static final long MAX_PIXELS = 100_000_000L;

	private static final float JPEG_QUALITY = 0.8f;

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

	static {
		// Decode in memory; no temporary files.
		ImageIO.setUseCache(false);
	}

	/**
	 * Decodes the upload and returns it as a JPEG whose longer side is at most {@code maxEdge}
	 * pixels. Smaller images keep their size.
	 * @throws ImageRejectedException if the file is not a readable JPEG, PNG, or WebP image
	 */
	public byte[] toJpeg(byte[] upload, int maxEdge) {
		BufferedImage image = decode(upload, maxEdge);
		double scale = Math.min(1.0, maxEdge / (double) Math.max(image.getWidth(), image.getHeight()));
		return encode(Thumbnails.of(image).scale(scale));
	}

	/**
	 * Decodes the upload, crops the largest centered square, and returns it as a JPEG of exactly
	 * {@code size} × {@code size} pixels, e.g. for avatars.
	 * @throws ImageRejectedException if the file is not a readable JPEG, PNG, or WebP image
	 */
	public byte[] toSquareJpeg(byte[] upload, int size) {
		BufferedImage image = decode(upload, size);
		return encode(Thumbnails.of(image).crop(Positions.CENTER).size(size, size));
	}

	private static byte[] encode(Thumbnails.Builder<BufferedImage> builder) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			builder.outputFormat("jpg").outputQuality(JPEG_QUALITY).toOutputStream(out);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

	/**
	 * Decodes to an upright RGB image. Very large images are subsampled while decoding, to at
	 * least twice the target size, so that memory stays bounded and the final resize still has
	 * enough pixels for good quality.
	 */
	private BufferedImage decode(byte[] upload, int maxEdge) {
		String format = formatOf(upload);
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
		if (!readers.hasNext()) {
			throw new IllegalStateException("No ImageIO reader for " + format);
		}
		ImageReader reader = readers.next();
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
			reader.setInput(input, true, true);
			int width = reader.getWidth(0);
			int height = reader.getHeight(0);
			if ((long) width * height > MAX_PIXELS) {
				throw new ImageRejectedException("This image has too many pixels. Please upload a smaller one.");
			}
			ImageReadParam param = reader.getDefaultReadParam();
			int subsampling = Math.max(1, Math.max(width, height) / (2 * maxEdge));
			param.setSourceSubsampling(subsampling, subsampling, 0, 0);
			BufferedImage decoded = reader.read(0, param);
			return upright(toRgb(decoded), orientationOf(upload));
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ImageRejectedException rejected) {
				throw rejected;
			}
			throw new ImageRejectedException("This image couldn't be read. Please try a different file.");
		}
		finally {
			reader.dispose();
		}
	}

	/**
	 * The ImageIO format name for JPEG, PNG, or WebP content; anything else is refused, whatever
	 * its file name or declared content type.
	 */
	static String formatOf(byte[] data) {
		if (data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff) {
			return "jpeg";
		}
		if (data.length >= 8 && Arrays.equals(data, 0, 8, PNG_SIGNATURE, 0, 8)) {
			return "png";
		}
		if (data.length >= 12 && ascii(data, 0, "RIFF") && ascii(data, 8, "WEBP")) {
			return "webp";
		}
		throw new ImageRejectedException("Only JPEG, PNG, and WebP images are supported.");
	}

	private static boolean ascii(byte[] data, int offset, String text) {
		for (int i = 0; i < text.length(); i++) {
			if (data[offset + i] != text.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The EXIF orientation (1–8), or 1 when there is none or it can't be read.
	 */
	private static int orientationOf(byte[] data) {
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data), data.length);
			ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
			if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
				return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
			}
		}
		catch (Exception ex) {
			// Broken or missing metadata: keep the image as stored.
		}
		return 1;
	}

	/**
	 * Opaque RGB; transparent areas become white, as JPEG has no alpha channel.
	 */
	private static BufferedImage toRgb(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_RGB) {
			return image;
		}
		BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = rgb.createGraphics();
		try {
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, image.getWidth(), image.getHeight());
			g.drawImage(image, 0, 0, null);
		}
		finally {
			g.dispose();
		}
		return rgb;
	}

	/**
	 * Applies an EXIF orientation, so that the stored pixels are upright without metadata.
	 */
	static BufferedImage upright(BufferedImage image, int orientation) {
		if (orientation < 2 || orientation > 8) {
			return image;
		}
		int w = image.getWidth();
		int h = image.getHeight();
		boolean swap = orientation >= 5;
		AffineTransform t = new AffineTransform();
		switch (orientation) {
			case 2 -> t.setTransform(-1, 0, 0, 1, w, 0); // mirror horizontally
			case 3 -> t.setTransform(-1, 0, 0, -1, w, h); // rotate 180
			case 4 -> t.setTransform(1, 0, 0, -1, 0, h); // mirror vertically
			case 5 -> t.setTransform(0, 1, 1, 0, 0, 0); // transpose
			case 6 -> t.setTransform(0, 1, -1, 0, h, 0); // rotate 90 clockwise
			case 7 -> t.setTransform(0, -1, -1, 0, h, w); // transverse
			case 8 -> t.setTransform(0, -1, 1, 0, 0, w); // rotate 90 counter-clockwise
			default -> {
			}
		}
		BufferedImage result = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = result.createGraphics();
		try {
			g.drawImage(image, t, null);
		}
		finally {
			g.dispose();
		}
		return result;
	}

}
