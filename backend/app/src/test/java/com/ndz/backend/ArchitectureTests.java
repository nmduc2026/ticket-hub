package com.ndz.backend;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;

/**
 * Quy tắc kiến trúc bổ sung cho Modulith {@code verify()}: nội bộ module, `shared` độc lập, controller không gọi
 * repository. Mỗi quy tắc có test chứng minh nó bắt được vi phạm trên fixture.
 */
class ArchitectureTests {

	private static final String ROOT = "com.ndz.backend";
	private static final String FIXTURES = "com.ndz.archfixtures";
	private static final List<String> BUSINESS_MODULES = List.of("account", "catalog", "booking", "payment", "ticket",
			"notification");

	private static final JavaClasses APP = new ClassFileImporter()
			.withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages(ROOT);
	private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURES);

	@Test
	void appClassesAreImported() {
		assertThat(APP.size()).isGreaterThan(5);
	}

	@Test
	void internalClassesAreUsedOnlyByTheirOwnModule() {
		internalClassesAreUsedOnlyByTheirOwnModuleRule(ROOT).check(APP);
	}

	@Test
	void sharedDoesNotDependOnBusinessModules() {
		sharedDoesNotDependOnBusinessModulesRule(ROOT).check(APP);
	}

	@Test
	void controllersDoNotUsePersistenceDirectly() {
		controllersDoNotUsePersistenceDirectlyRule().check(APP);
	}

	@Test
	void internalRuleCatchesAccessFromAnotherModule() {
		assertThatThrownBy(() -> internalClassesAreUsedOnlyByTheirOwnModuleRule(FIXTURES).check(FIXTURE_CLASSES))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("Intruder")
				.hasMessageContaining("SeatLock");
	}

	@Test
	void controllerRuleCatchesDirectRepositoryUse() {
		assertThatThrownBy(() -> controllersDoNotUsePersistenceDirectlyRule().check(FIXTURE_CLASSES))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("EventController")
				.hasMessageContaining("EventRepository");
	}

	@Test
	void sharedRuleCatchesDependencyOnABusinessModule() {
		assertThatThrownBy(() -> sharedDoesNotDependOnBusinessModulesRule(FIXTURES).check(FIXTURE_CLASSES))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("shared");
	}

	private static ArchRule internalClassesAreUsedOnlyByTheirOwnModuleRule(String root) {
		return classes().that().resideInAPackage(root + ".*.internal..")
				.should(new ArchCondition<JavaClass>("be used only by classes of their own module") {
					@Override
					public void check(JavaClass target, ConditionEvents events) {
						String owner = moduleOf(target.getPackageName(), root);
						for (Dependency dependency : target.getDirectDependenciesToSelf()) {
							String origin = moduleOf(dependency.getOriginClass().getPackageName(), root);
							if (!owner.equals(origin)) {
								events.add(violated(dependency, dependency.getDescription()));
							}
						}
					}
				})
				.allowEmptyShould(true);
	}

	private static ArchRule sharedDoesNotDependOnBusinessModulesRule(String root) {
		return noClasses().that().resideInAPackage(root + ".shared..")
				.should().dependOnClassesThat(DescribedPredicate.describe(
						"reside in a business module",
						(JavaClass javaClass) -> BUSINESS_MODULES.stream()
								.anyMatch(module -> moduleOf(javaClass.getPackageName(), root).equals(module))))
				.because("`shared` không chứa logic của module nghiệp vụ")
				.allowEmptyShould(true);
	}

	private static ArchRule controllersDoNotUsePersistenceDirectlyRule() {
		return noClasses().that().resideInAPackage("..internal.web..")
				.should().dependOnClassesThat().resideInAPackage("..internal.persistence..")
				.because("controller chỉ gọi application service")
				.allowEmptyShould(true);
	}

	private static String moduleOf(String packageName, String root) {
		if (!packageName.startsWith(root + ".")) {
			return packageName.equals(root) ? "" : packageName;
		}
		String rest = packageName.substring(root.length() + 1);
		int dot = rest.indexOf('.');
		return dot < 0 ? rest : rest.substring(0, dot);
	}

}
