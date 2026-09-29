package app.adventr.image;

/**
 * An upload that is not an acceptable image. The message is meant for the user.
 */
public class ImageRejectedException extends RuntimeException {

	public ImageRejectedException(String message) {
		super(message);
	}

}
