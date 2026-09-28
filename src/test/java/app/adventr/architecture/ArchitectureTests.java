package app.adventr.architecture;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;

import app.adventr.group.GroupAccessService;
import app.adventr.group.GroupScopedService;
import app.adventr.group.GuardExempt;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the central authorization design (design.md, decision 3): membership is checked in the
 * service layer by {@link GroupAccessService}, never in controllers, and controllers never
 * bypass the services by using repositories.
 */
class ArchitectureTests {

	private static final JavaClasses CLASSES = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("app.adventr");

	@Test
	void controllersDoNotUseRepositories() {
		noClasses().that()
			.areAnnotatedWith(Controller.class)
			.or()
			.areAnnotatedWith(ControllerAdvice.class)
			.should()
			.dependOnClassesThat(assignableTo(Repository.class))
			.check(CLASSES);
	}

	@Test
	void groupScopedServiceMethodsCallTheMembershipGuard() {
		methods().that()
			.areDeclaredInClassesThat()
			.areAnnotatedWith(GroupScopedService.class)
			.and()
			.arePublic()
			.and()
			.areNotStatic()
			.and()
			.areNotAnnotatedWith(GuardExempt.class)
			.should(callTheMembershipGuard())
			.check(CLASSES);
	}

	/**
	 * The method calls {@code requireMember} or {@code requireOwner}, directly or through other
	 * methods of its own class.
	 */
	private static ArchCondition<JavaMethod> callTheMembershipGuard() {
		return new ArchCondition<>("call GroupAccessService.requireMember or requireOwner") {

			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				boolean guarded = reachesGuard(method);
				String message = method.getFullName() + (guarded ? " calls" : " does not call")
						+ " the membership guard; call GroupAccessService or annotate it @GuardExempt(\"why\")";
				events.add(new SimpleConditionEvent(method, guarded, message));
			}

		};
	}

	private static boolean reachesGuard(JavaMethod start) {
		Set<JavaMethod> seen = new HashSet<>();
		Deque<JavaMethod> pending = new ArrayDeque<>();
		pending.push(start);
		while (!pending.isEmpty()) {
			JavaMethod method = pending.pop();
			if (!seen.add(method)) {
				continue;
			}
			for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
				if (call.getTargetOwner().isEquivalentTo(GroupAccessService.class)
						&& call.getName().startsWith("require")) {
					return true;
				}
				if (call.getTargetOwner().equals(start.getOwner())) {
					call.getTarget().resolveMember().ifPresent(pending::push);
				}
			}
		}
		return false;
	}

}
