package app.adventr.adventure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;

import app.adventr.group.GroupDeletedEvent;
import app.adventr.image.ImageProcessor;
import app.adventr.image.ImageRejectedException;
import app.adventr.image.ImageStore;

/**
 * Where adventure images live: {@code {groupId}/{uuid}.jpg} (at most 1600 px) plus a list
 * thumbnail {@code {groupId}/{uuid}-thumb.jpg}. One directory per group, so that deleting a
 * group removes all of its files at once.
 */
@Component
class AdventureImages {

	static final long MAX_BYTES = 10L * 1024 * 1024;

	static final int MAX_EDGE = 1600;

	static final int THUMBNAIL_EDGE = 320;

	private final ImageProcessor processor;

	private final ImageStore store;

	AdventureImages(ImageProcessor processor, ImageStore store) {
		this.processor = processor;
		this.store = store;
	}

	/**
	 * Checks, processes, and writes an upload; returns the new image path.
	 * @throws ImageRejectedException if the file is empty, too big, or not a supported image
	 */
	String store(long groupId, MultipartFile upload) {
		if (upload == null || upload.isEmpty()) {
			throw new ImageRejectedException("Please choose an image file.");
		}
		if (upload.getSize() > MAX_BYTES) {
			throw new ImageRejectedException("Images can be at most 10 MB.");
		}
		byte[] bytes;
		try {
			bytes = upload.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		byte[] image = this.processor.toJpeg(bytes, MAX_EDGE);
		byte[] thumbnail = this.processor.toJpeg(bytes, THUMBNAIL_EDGE);
		String version = UUID.randomUUID().toString();
		String path = groupId + "/" + version + ".jpg";
		this.store.write(path, image);
		this.store.write(thumbnailPath(path), thumbnail);
		return path;
	}

	Resource image(String path) {
		return this.store.read(path);
	}

	Resource thumbnail(String path) {
		return this.store.read(thumbnailPath(path));
	}

	/**
	 * Removes the image and its thumbnail once the transaction commits.
	 */
	void deleteAfterCommit(String path) {
		if (path != null) {
			this.store.deleteAfterCommit(List.of(path, thumbnailPath(path)));
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void groupDeleted(GroupDeletedEvent event) {
		this.store.deleteDirectory(String.valueOf(event.groupId()));
	}

	/**
	 * The random part of the file name. It changes with every upload, so image URLs carry it
	 * and can be cached for a long time.
	 */
	static String version(String path) {
		if (path == null) {
			return null;
		}
		String name = path.substring(path.lastIndexOf('/') + 1);
		return name.substring(0, name.length() - ".jpg".length());
	}

	private static String thumbnailPath(String path) {
		return path.substring(0, path.length() - ".jpg".length()) + "-thumb.jpg";
	}

}
