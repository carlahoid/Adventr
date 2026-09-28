package app.adventr.group;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exempts a public method of a {@link GroupScopedService} from the membership guard. The
 * reason must say why the method is safe without it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GuardExempt {

	/**
	 * Why this method does not need the membership guard.
	 */
	String value();

}
