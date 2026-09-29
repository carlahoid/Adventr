package app.adventr.account;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import app.adventr.image.ImageProcessor;
import app.adventr.image.ImageRejectedException;
import app.adventr.image.ImageStore;

/**
 * Where avatars live: {@code avatars/{userId}/{uuid}.jpg} under the images directory, a
 * 256 × 256 square. The directory is part of the images volume, so backups include it.
 */
@Component
class AvatarStorage {

	static final long MAX_BYTES = 5L * 1024 * 1024;

	static final int SIZE = 256;

	private final ImageProcessor processor;

	private final ImageStore store;

	AvatarStorage(ImageProcessor processor, ImageStore store) {
		this.processor = processor;
		this.store = store;
	}

	/**
	 * Checks, crops, and writes an upload; returns the new avatar path.
	 * @throws ImageRejectedException if the file is empty, too big, or not a supported image
	 */
	String store(long userId, MultipartFile upload) {
		if (upload == null || upload.isEmpty()) {
			throw new ImageRejectedException("Please choose an image file.");
		}
		if (upload.getSize() > MAX_BYTES) {
			throw new ImageRejectedException("Profile pictures can be at most 5 MB.");
		}
		byte[] bytes;
		try {
			bytes = upload.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		String path = "avatars/" + userId + "/" + UUID.randomUUID() + ".jpg";
		this.store.write(path, this.processor.toSquareJpeg(bytes, SIZE));
		return path;
	}

	Resource read(String path) {
		return this.store.read(path);
	}

	/**
	 * Removes the file once the transaction commits.
	 */
	void deleteAfterCommit(String path) {
		if (path != null) {
			this.store.deleteAfterCommit(List.of(path));
		}
	}

}
