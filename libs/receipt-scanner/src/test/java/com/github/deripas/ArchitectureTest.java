package com.github.deripas;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Dependencies between the packages, from the bottom up:
 * <ol>
 *     <li>{@code image}: images and OpenCV;</li>
 *     <li>{@code ollama}, {@code tesseract}: clients of the tools, know nothing about receipts;
 *     {@code ollama} is independent of {@code image} too;</li>
 *     <li>{@code receipt}, {@code receipt.scan}, {@code receipt.read}, {@code receipt.parse}: the receipt model,
 *     the stages and their default implementations, independent of the tools that read the receipt;</li>
 *     <li>{@code receipt.scan.opencv}, {@code receipt.scan.ollama}, {@code receipt.read.ollama}: stages done
 *     by a particular tool.</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.github.deripas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String IMAGE = "com.github.deripas.image..";
    private static final String OLLAMA = "com.github.deripas.ollama..";
    private static final String TESSERACT = "com.github.deripas.tesseract..";
    private static final String RECEIPT = "com.github.deripas.receipt..";

    @ArchTest
    static final ArchRule topLevelPackagesAreFreeOfCycles = slices()
            .matching("com.github.deripas.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule receiptStagesAreFreeOfCycles = slices()
            .matching("com.github.deripas.receipt.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule imageDependsOnNothingElse = noClasses()
            .that().resideInAPackage(IMAGE)
            .should().dependOnClassesThat().resideInAnyPackage(OLLAMA, TESSERACT, RECEIPT)
            .because("images are the base used by everything else");

    @ArchTest
    static final ArchRule toolClientsKnowNothingAboutReceipts = noClasses()
            .that().resideInAnyPackage(OLLAMA, TESSERACT)
            .should().dependOnClassesThat().resideInAPackage(RECEIPT)
            .because("clients of Ollama and Tesseract can serve any task");

    @ArchTest
    static final ArchRule ollamaDoesNotDependOnImage = noClasses()
            .that().resideInAPackage(OLLAMA)
            .should().dependOnClassesThat().resideInAPackage(IMAGE)
            .because("Ollama and images are separate tools, joined only by the stages that use both;"
                    + " the client takes BufferedImage");

    @ArchTest
    static final ArchRule receiptStagesDoNotDependOnTheirImplementations = noClasses()
            .that().resideInAnyPackage(
                    "com.github.deripas.receipt",
                    "com.github.deripas.receipt.scan",
                    "com.github.deripas.receipt.read",
                    "com.github.deripas.receipt.parse")
            .should().dependOnClassesThat().resideInAPackage("com.github.deripas.receipt.*.*..")
            .because("an implementation of a stage is chosen by the caller");

    @ArchTest
    static final ArchRule stageImplementationsDoNotDependOnEachOther = slices()
            .matching("com.github.deripas.receipt.(*).(*)..")
            .should().notDependOnEachOther()
            .because("a stage done by one tool must not need another tool");

    @ArchTest
    static final ArchRule onlyOllamaPackagesUseOllama = noClasses()
            .that().resideOutsideOfPackages("..ollama..")
            .should().dependOnClassesThat().resideInAPackage(OLLAMA)
            .because("the receipt model and the stages must not depend on a tool, e.g. by its annotations;"
                    + " a model answer gets its own class in the ollama package of the stage");

    @ArchTest
    static final ArchRule imageFunctionsAreInternal = noClasses()
            .that().resideOutsideOfPackage(IMAGE)
            .should().dependOnClassesThat().resideInAPackage("com.github.deripas.image.function..")
            .because("images are transformed through ImageTransform");

    @ArchTest
    static final ArchRule openCvStaysInImageAndOpenCvDetector = noClasses()
            .that().resideOutsideOfPackages(IMAGE, "com.github.deripas.receipt.scan.opencv..")
            .should().dependOnClassesThat().resideInAPackage("org.opencv..")
            .because("OpenCV images need to be released; outside of them images are ImageData");

    @ArchTest
    static final ArchRule tesseractLibraryStaysInTesseract = noClasses()
            .that().resideOutsideOfPackage(TESSERACT)
            .should().dependOnClassesThat().resideInAPackage("org.bytedeco.tesseract..");

    @ArchTest
    static final ArchRule schemaAndTemplateLibrariesStayInOllama = noClasses()
            .that().resideOutsideOfPackage(OLLAMA)
            .should().dependOnClassesThat().resideInAnyPackage("com.github.victools..", "org.stringtemplate..");
}
