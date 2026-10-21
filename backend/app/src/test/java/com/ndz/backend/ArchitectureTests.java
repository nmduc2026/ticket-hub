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
 * Quy tắc kiến trúc bổ sung cho Modulith {@code verify()}: chỉ {@code api} và {@code event} của module là công khai,
 * `shared` độc lập, controller chỉ gọi service và chỉ thấy dto, service là interface. Mỗi quy tắc có test chứng minh
 * nó bắt được vi phạm trên fixture.
 */
class ArchitectureTests {

	private static final String ROOT = "com.ndz.backend";
	private static final String FIXTURES = "com.ndz.archfixtures";
	private static final List<String> BUSINESS_MODULES = List.of("account", "catalog", "booking", "payment", "ticket",
			"notification");
	private static final List<String> FIXTURE_MODULES = List.of("booking", "catalog");

	private static final JavaClasses APP = new ClassFileImporter()
			.withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages(ROOT);
	private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURES);

	@Test
	void appClassesAreImported() {
		assertThat(APP.size()).isGreaterThan(5);
	}

	@Test
	void nonPublicPackagesAreUsedOnlyByTheirOwnModule() {
		nonPublicPackagesAreUsedOnlyByTheirOwnModuleRule(ROOT).check(APP);
	}

	@Test
	void sharedDoesNotDependOnBusinessModules() {
		sharedDoesNotDependOnBusinessModulesRule(ROOT).check(APP);
	}

	@Test
	void controllersCallOnlyServiceInterfaces() {
		controllersCallOnlyServiceInterfacesRule().check(APP);
	}

	@Test
	void controllersDoNotUseEntities() {
		controllersDoNotUseEntitiesRule().check(APP);
	}

	@Test
	void serviceTypesAreInterfacesAndImplementationsEndWithImpl() {
		serviceTypesAreInterfacesRule().check(APP);
		serviceImplementationsEndWithImplRule().check(APP);
	}

	@Test
	void nonPublicPackageRuleCatchesAccessFromAnotherModule() {
		assertThatThrownBy(() -> nonPublicPackagesAreUsedOnlyByTheirOwnModuleRule(FIXTURES).check(FIXTURE_CLASSES))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("Intruder")
				.hasMessageContaining("SeatLock");
	}

	@Test
	void controllerRuleCatchesDirectRepositoryUse() {
		assertThatThrownBy(() -> controllersCallOnlyServiceInterfacesRule().check(FIXTURE_CLASSES))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("EventController")
				.hasMessageContaining("EventRepository");
	}

	@Test
	void controllerRuleCatchesDirectEntityUse() {
		assertThatThrownBy(() -> controllersDoNotUseEntitiesRule().check(FIXTURE_CLASSES))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("LeakyController")
				.hasMessageContaining("Event");
	}

	@Test
	void serviceRuleCatchesConcreteClassInServicePackage() {
		assertThatThrownBy(() -> serviceTypesAreInterfacesRule().check(FIXTURE_CLASSES))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("EventService");
	}

	@Test
	void sharedRuleCatchesDependencyOnABusinessModule() {
		assertThatThrownBy(() -> sharedDoesNotDependOnBusinessModulesRule(FIXTURES).check(FIXTURE_CLASSES))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("shared");
	}

	/** Chỉ package gốc, {@code api} và {@code event} của module là công khai; package khác chỉ module đó được dùng. */
	private static ArchRule nonPublicPackagesAreUsedOnlyByTheirOwnModuleRule(String root) {
		return classes().that(DescribedPredicate.describe("reside in a non-public package of a module",
				(JavaClass javaClass) -> isNonPublicPackage(javaClass.getPackageName(), root)))
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

	private static ArchRule controllersCallOnlyServiceInterfacesRule() {
		return noClasses().that().resideInAPackage("..controller..")
				.should().dependOnClassesThat().resideInAnyPackage("..repository..", "..service.impl..")
				.because("controller chỉ gọi interface service")
				.allowEmptyShould(true);
	}

	private static ArchRule controllersDoNotUseEntitiesRule() {
		return noClasses().that().resideInAPackage("..controller..")
				.should().dependOnClassesThat().resideInAPackage("..entity")
				.because("controller chỉ thấy dto; entity không ra khỏi service (enum ở entity.enums được phép)")
				.allowEmptyShould(true);
	}

	private static ArchRule serviceTypesAreInterfacesRule() {
		return classes().that().resideInAPackage("..service")
				.should().beInterfaces()
				.because("service khai báo bằng interface, cài đặt nằm ở service.impl")
				.allowEmptyShould(true);
	}

	private static ArchRule serviceImplementationsEndWithImplRule() {
		return classes().that().resideInAPackage("..service.impl")
				.and().areTopLevelClasses()
				.should().haveSimpleNameEndingWith("Impl")
				.allowEmptyShould(true);
	}

	private static boolean isNonPublicPackage(String packageName, String root) {
		if (!packageName.startsWith(root + ".")) {
			return false;
		}
		String rest = packageName.substring(root.length() + 1);
		int dot = rest.indexOf('.');
		if (dot < 0) {
			return false;
		}
		String module = rest.substring(0, dot);
		if (!BUSINESS_MODULES.contains(module) && !FIXTURE_MODULES.contains(module)) {
			return false;
		}
		String sub = rest.substring(dot + 1);
		return !sub.equals("api") && !sub.equals("event");
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
