package com.github.deripas;

import com.github.deripas.receipt.ReceiptParsers;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Dependencies between the packages, from the bottom up:
 * <ol>
 *     <li>{@code image}: images, OpenCV and the QR codes read with BoofCV;</li>
 *     <li>{@code ollama}, {@code tesseract}: clients of the tools, know nothing about receipts;
 *     {@code ollama} is independent of {@code image} too;</li>
 *     <li>{@code receipt}: the receipt model and {@link com.github.deripas.receipt.ReceiptParser};
 *     {@code receipt.scan}: the optional stage that cuts the receipt out of the photo, with its default
 *     implementation; {@code receipt.parse}: parsers made of other parsers; all of them independent of the tools;</li>
 *     <li>{@code receipt.scan.opencv}, {@code receipt.scan.boofcv}, {@code receipt.scan.ollama}: detectors
 *     of the receipt by a particular tool;</li>
 *     <li>{@code receipt.parse.ollama}, {@code receipt.parse.qr}: parsers by a particular way, each with its
 *     own parts in subpackages: {@code receipt.parse.ollama.model} reads the receipt with the model and
 *     converts what was read, {@code receipt.parse.qr.rs} loads a Serbian receipt by its QR code with
 *     the client {@code receipt.parse.qr.rs.suf}, which knows nothing about the receipt model;</li>
 *     <li>{@link ReceiptParsers}: parsers put together from the stages and the tools, for the users
 *     of the library.</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.github.deripas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String IMAGE = "com.github.deripas.image..";
    private static final String OLLAMA = "com.github.deripas.ollama..";
    private static final String TESSERACT = "com.github.deripas.tesseract..";
    private static final String SUF = "com.github.deripas.receipt.parse.qr.rs.suf..";
    private static final String PARSERS = ReceiptParsers.class.getName();
    private static final String RECEIPT = "com.github.deripas.receipt..";

    @ArchTest
    static final ArchRule topLevelPackagesAreFreeOfCycles = slices()
            .matching("com.github.deripas.(*)..")
            .should().beFreeOfCycles();

    /**
     * Every package is a slice of its own, so that a parser and its parts in subpackages do not depend on each
     * other both ways either.
     */
    @ArchTest
    static final ArchRule receiptPackagesAreFreeOfCycles = slices()
            .matching("com.github.deripas.receipt.(**)")
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
    static final ArchRule sufKnowsNothingAboutReceiptModel = noClasses()
            .that().resideInAPackage(SUF)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.github.deripas.receipt",
                    "com.github.deripas.receipt.scan..",
                    "com.github.deripas.receipt.parse",
                    "com.github.deripas.receipt.parse.ollama..",
                    "com.github.deripas.receipt.parse.qr",
                    "com.github.deripas.receipt.parse.qr.rs")
            .because("the client gives the receipt as the service has it; the loader turns it into a Receipt");

    @ArchTest
    static final ArchRule sufDoesNotDependOnImage = noClasses()
            .that().resideInAPackage(SUF)
            .should().dependOnClassesThat().resideInAPackage(IMAGE)
            .because("the receipt service is called by the link; the QR code is read by the parser");

    @ArchTest
    static final ArchRule onlySufPackagesUseSuf = noClasses()
            .that().resideOutsideOfPackages(SUF, "com.github.deripas.receipt.parse.qr.rs")
            .should().dependOnClassesThat().resideInAPackage(SUF)
            .because("the Serbian service is used by its loader only");

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
                    "com.github.deripas.receipt.parse")
            .and().doNotHaveFullyQualifiedName(PARSERS)
            .should().dependOnClassesThat().resideInAPackage("com.github.deripas.receipt.*.*..")
            .because("an implementation of a stage is chosen by the caller");

    @ArchTest
    static final ArchRule stageImplementationsDoNotDependOnEachOther = slices()
            .matching("com.github.deripas.receipt.(*).(*)..")
            .should().notDependOnEachOther()
            .because("a stage done by one tool must not need another tool, nor a parser the other one");

    @ArchTest
    static final ArchRule onlyOllamaPackagesUseOllama = noClasses()
            .that().resideOutsideOfPackages("..ollama..")
            .and().doNotHaveFullyQualifiedName(PARSERS)
            .should().dependOnClassesThat().resideInAPackage(OLLAMA)
            .because("the receipt model and the stages must not depend on a tool, e.g. by its annotations;"
                    + " a model answer gets its own class in the ollama package of the stage");

    @ArchTest
    static final ArchRule parsersAreNotUsedInside = noClasses()
            .that().doNotHaveFullyQualifiedName(PARSERS)
            .should().dependOnClassesThat().haveFullyQualifiedName(PARSERS)
            .because("the parsers put together are for the users of the library: they depend on everything");

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
    static final ArchRule boofCvStaysInImageAndBoofCvDetector = noClasses()
            .that().resideOutsideOfPackages(IMAGE, "com.github.deripas.receipt.scan.boofcv..")
            .should().dependOnClassesThat().resideInAnyPackage("boofcv..", "georegression..")
            .because("the detector in pure Java must not need OpenCV, and the rest of the library uses ImageData");

    @ArchTest
    static final ArchRule tesseractLibraryStaysInTesseract = noClasses()
            .that().resideOutsideOfPackage(TESSERACT)
            .should().dependOnClassesThat().resideInAPackage("org.bytedeco.tesseract..");

    @ArchTest
    static final ArchRule schemaAndTemplateLibrariesStayInOllama = noClasses()
            .that().resideOutsideOfPackage(OLLAMA)
            .should().dependOnClassesThat().resideInAnyPackage("com.github.victools..", "org.stringtemplate..");
}
