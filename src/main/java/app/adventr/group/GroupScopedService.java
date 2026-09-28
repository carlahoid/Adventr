package app.adventr.group;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service whose public methods read or change group-scoped data. Each public method
 * must call {@link GroupAccessService} (directly or through a private helper) or be annotated
 * {@link GuardExempt}. {@code ArchitectureTests} enforces this.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GroupScopedService {

}
