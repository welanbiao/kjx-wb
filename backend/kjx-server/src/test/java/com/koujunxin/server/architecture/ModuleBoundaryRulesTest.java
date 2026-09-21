package com.koujunxin.server.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * 模块边界门禁：无环、runtime/tool 不得互依、禁止跨模块 Mapper/Entity。
 */
@AnalyzeClasses(packages = "com.koujunxin", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryRulesTest {

    @ArchTest
    static final ArchRule no_module_cycles = slices()
            .matching("com.koujunxin.(*)..")
            .should()
            .beFreeOfCycles();

    @ArchTest
    static final ArchRule runtime_must_not_depend_on_tool = noClasses()
            .that().resideInAPackage("com.koujunxin.runtime..")
            .and().resideOutsideOfPackage("com.koujunxin.runtime.api..")
            .should().dependOnClassesThat().resideInAPackage("com.koujunxin.tool..")
            .because("kjx-runtime 只能依赖 kjx-runtime-api，不得直接依赖 kjx-tool");

    @ArchTest
    static final ArchRule tool_must_not_depend_on_runtime_impl = noClasses()
            .that().resideInAPackage("com.koujunxin.tool..")
            .should().dependOnClassesThat(
                    resideInAPackage("com.koujunxin.runtime..")
                            .and(resideOutsideOfPackage("com.koujunxin.runtime.api..")))
            .because("kjx-tool 只能依赖 kjx-runtime-api，不得直接依赖 kjx-runtime 实现");

    @ArchTest
    static final ArchRule runtime_api_must_not_depend_on_implementations = noClasses()
            .that().resideInAPackage("com.koujunxin.runtime.api..")
            .should().dependOnClassesThat(
                    resideInAPackage("com.koujunxin.tool..")
                            .or(resideInAPackage("com.koujunxin.runtime..")
                                    .and(resideOutsideOfPackage("com.koujunxin.runtime.api.."))))
            .because("runtime-api 不得依赖 runtime/tool 实现");

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_mappers = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..mapper..")
            .allowEmptyShould(true)
            .because("controller 不得直接访问 mapper");

    @ArchTest
    static final ArchRule core_domain_must_not_use_clickhouse = noClasses()
            .that().resideInAnyPackage(
                    "com.koujunxin.account..",
                    "com.koujunxin.member..",
                    "com.koujunxin.work..",
                    "com.koujunxin.runtime..",
                    "com.koujunxin.tool..",
                    "com.koujunxin.billing..",
                    "com.koujunxin.ai..",
                    "com.koujunxin.channel..")
            .should().dependOnClassesThat().resideInAnyPackage("com.clickhouse..", "..clickhouse..")
            .because("ClickHouse client 不得出现在核心领域模块");

    @ArchTest
    static final ArchRule no_cross_module_mapper_or_entity = noClasses()
            .that().resideInAPackage("com.koujunxin..")
            .should(new ArchCondition<JavaClass>("access another module's Mapper or Entity") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    String origin = topModule(javaClass.getPackageName());
                    for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                        JavaClass target = dependency.getTargetClass();
                        String targetPackage = target.getPackageName();
                        if (!isMapperOrEntityPackage(targetPackage)) {
                            continue;
                        }
                        String targetModule = topModule(targetPackage);
                        if (!targetModule.isEmpty() && !targetModule.equals(origin)) {
                            events.add(SimpleConditionEvent.violated(
                                    javaClass,
                                    javaClass.getName() + " 访问了其他模块的 Mapper/Entity: " + target.getName()));
                        }
                    }
                }
            })
            .because("禁止跨模块访问 Mapper、Entity、Repository 实现");

    private static String topModule(String packageName) {
        String prefix = "com.koujunxin.";
        if (!packageName.startsWith(prefix)) {
            return "";
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static boolean isMapperOrEntityPackage(String packageName) {
        return packageName.contains(".mapper.")
                || packageName.endsWith(".mapper")
                || packageName.contains(".entity.")
                || packageName.endsWith(".entity");
    }
}
