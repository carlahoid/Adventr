package app.adventr.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Image files on disk under {@code adventr.images.dir} ({@code /data/images} in the container),
 * addressed by paths relative to that root. Files are never served statically; guarded
 * controllers stream them. File changes follow the database transaction: a file written in a
 * transaction that rolls back is removed, and deletions happen only after commit.
 */
@Component
public class ImageStore {

	private static final Logger logger = LoggerFactory.getLogger(ImageStore.class);

	private final Path root;

	public ImageStore(@Value("${adventr.images.dir:/data/images}") Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	/**
	 * Writes a new file. Inside a transaction, the file is removed again if it rolls back.
	 */
	public void write(String relativePath, byte[] content) {
		Path file = resolve(relativePath);
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, content, StandardOpenOption.CREATE_NEW);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status == STATUS_ROLLED_BACK) {
						deleteQuietly(file);
					}
				}
			});
		}
	}

	/**
	 * The file as a resource, or {@code null} if it does not exist.
	 */
	public Resource read(String relativePath) {
		Path file = resolve(relativePath);
		return Files.isRegularFile(file) ? new FileSystemResource(file) : null;
	}

	/**
	 * Deletes the files once the current transaction commits (at once when there is none), so
	 * that a rollback never leaves a row pointing at a missing file.
	 */
	public void deleteAfterCommit(List<String> relativePaths) {
		List<Path> files = relativePaths.stream().map(this::resolve).toList();
		afterCommit(() -> files.forEach(ImageStore::deleteQuietly));
	}

	/**
	 * Deletes a directory with everything in it, immediately. Meant for after-commit listeners,
	 * e.g. on {@code GroupDeletedEvent}: a synchronization registered while a transaction is
	 * already completing would never run.
	 */
	public void deleteDirectory(String relativePath) {
		deleteTree(resolve(relativePath));
	}

	/**
	 * Resolves a relative path inside the root; anything that would escape it is refused.
	 */
	private Path resolve(String relativePath) {
		Path path = this.root.resolve(relativePath).normalize();
		if (!path.startsWith(this.root) || path.equals(this.root)) {
			throw new IllegalArgumentException("Path outside the image directory: " + relativePath);
		}
		return path;
	}

	private static void afterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

	private static void deleteTree(Path directory) {
		if (!Files.isDirectory(directory)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(ImageStore::deleteQuietly);
		}
		catch (IOException ex) {
			logger.warn("Could not delete image directory {}", directory, ex);
		}
	}

	/**
	 * The database is already consistent at this point; a leftover file is only wasted space.
	 */
	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			logger.warn("Could not delete image file {}", file, ex);
		}
	}

}
