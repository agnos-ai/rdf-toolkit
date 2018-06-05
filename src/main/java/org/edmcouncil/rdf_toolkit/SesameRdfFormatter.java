/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2015 Enterprise Data Management Council
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.edmcouncil.rdf_toolkit;

import org.apache.commons.cli.*;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * RDF formatter that formats in a consistent order, friendly for version control systems.
 * Should not be used with files that are too large to be fully loaded into memory for sorting.
 * Run with "--help" option for help.
 */
public class SesameRdfFormatter {

    private static final Logger logger = LoggerFactory.getLogger(SesameRdfFormatter.class);

    private static Options options = null;

    private static ValueFactory valueFactory = SimpleValueFactory.getInstance();

    static {
        // Create list of program options, using Apache Commons CLI library.
        options = new Options();
        options.addOption(
                "s", "source", true, "source (input) RDF file to format"
        );
        options.addOption(
                "sfmt", "source-format", true, "source (input) RDF format; one of: " + SesameSortedRDFWriterFactory.SourceFormats.summarise()
        );
        options.addOption(
                "t", "target", true, "target (output) RDF file"
        );
        options.addOption(
                "tfmt", "target-format", true, "target (output) RDF format: one of: " + SesameSortedRDFWriterFactory.TargetFormats.summarise()
        );
        options.addOption(
                "h", "help", false, "print out details of the command-line arguments for the program"
        );
        options.addOption(
                "bi", "base-iri", true, "set IRI to use as base URI"
        );
        options.addOption(
                "sip", "short-iri-priority", true, "set what takes priority when shortening IRIs: " + SesameSortedRDFWriter.ShortIriPreferences.summarise()
        );
        options.addOption(
                "ip", "iri-pattern", true, "set a pattern to replace in all IRIs (used together with --iri-replacement)"
        );
        options.addOption(
                "ir", "iri-replacement", true, "set replacement text used to replace a matching pattern in all IRIs (used together with --iri-pattern)"
        );
        options.addOption(
                "dtd", "use-dtd-subset", false, "for XML, use a DTD subset in order to allow prefix-based IRI shortening"
        );
        options.addOption(
                "ibn", "inline-blank-nodes", false, "use inline representation for blank nodes.  NOTE: this will fail if there are any recursive relationships involving blank nodes.  Usually OWL has no such recursion involving blank nodes.  It also will fail if any blank nodes are a triple subject but not a triple object."
        );
        options.addOption(
                "ibi", "infer-base-iri", false, "use the OWL ontology IRI as the base URI.  Ignored if an explicit base IRI has been set"
        );
        options.addOption(
                "lc", "leading-comment", true, "sets the text of the leading comment in the ontology.  Can be repeated for a multi-line comment"
        );
        options.addOption(
                "tc", "trailing-comment", true, "sets the text of the trailing comment in the ontology.  Can be repeated for a multi-line comment"
        );
        options.addOption(
                "sdt", "string-data-typing", true, "sets whether string data values have explicit data types, or not; one of: " + SesameSortedRDFWriterFactory.StringDataTypeOptions.summarise()
        );
        options.addOption(
                "i", "indent", true, "sets the indent string.  Default is a single tab character"
        );
    }

    /** Main method for running the RDF formatter. Run with "--help" option for help. */
    public static void main(String[] args) {
        // Main program block.
        try {
            run(args);
            System.exit(0);
        } catch (Throwable t) {
            logger.error(SesameRdfFormatter.class.getSimpleName() + ": stopped by unexpected exception:");
            logger.error(t.getClass().getSimpleName() + ": " + t.getMessage());
            StringWriter stackTraceWriter = new StringWriter();
            t.printStackTrace(new PrintWriter(stackTraceWriter));
            logger.error(stackTraceWriter.toString());
            usage(options);
            System.exit(1);
        }

    }

    /** Main method, but throws exceptions for use from inside other Java code. */
    public static void run(String[] args) throws Exception {
        IRI baseIri = null;
        String baseIriString = "";
        String iriPattern = null;
        String iriReplacement = null;
        boolean useDtdSubset = false;
        boolean inlineBlankNodes = false;
        boolean inferBaseIri = false;
        IRI inferredBaseIri = null;
        String[] leadingComments = null;
        String[] trailingComments = null;
        String indent = "\t";
        SesameSortedRDFWriterFactory.StringDataTypeOptions stringDataTypeOption = SesameSortedRDFWriterFactory.StringDataTypeOptions.implicit;

        // Parse the command line options.
        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse( options, args );

        // Print out help, if requested.
        if (line.hasOption("h")) {
            usage(options);
            return;
        }

        // Check if required arguments provided.
        if (!line.hasOption("s")) {
            logger.error("No source (input) file specified, nothing to format.  Use --help for help.");
            return;
        }
        if (!line.hasOption("t")) {
            logger.error("No target (target) file specified, cannot format source.  Use --help for help.");
            return;
        }

        // Check if source files exists.
        String sourceFilePath = line.getOptionValue("s");
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists()) {
            logger.error("Source file does not exist: " + sourceFilePath);
            return;
        }
        if (!sourceFile.isFile()) {
            logger.error("Source file is not a file: " + sourceFilePath);
            return;
        }
        if (!sourceFile.canRead()) {
            logger.error("Source file is not readable: " + sourceFilePath);
            return;
        }

        // Check if target file can be written.
        String targetFilePath = line.getOptionValue("t");
        File targetFile = new File(targetFilePath);
        if (targetFile.exists()) {
            if (!targetFile.isFile()) {
                logger.error("Target file is not a file: " + targetFilePath);
                return;
            }
            if (!targetFile.canWrite()) {
                logger.error("Target file is not writable: " + targetFilePath);
                return;
            }
        }

        // Create directory for target file, if required.
        File targetFileDir = targetFile.getParentFile();
        if (targetFileDir != null) {
            targetFileDir.mkdirs();
            if (!targetFileDir.exists()) {
                logger.error("Target file directory could not be created: " + targetFileDir.getAbsolutePath());
                return;
            }
        }

        // Check if a base URI was provided
        try {
            if (line.hasOption("bi")) {
                baseIriString = line.getOptionValue("bi");
                baseIri = valueFactory.createIRI(baseIriString);
                if (baseIriString.endsWith("#")) {
                    logger.warn("base IRI ends in '#', which is unusual: " + baseIriString);
                }
            }
        } catch (Throwable t) {
            baseIri = null;
            baseIriString = "";
        }

        // Check if there is a valid URI pattern/replacement pair
        if (line.hasOption("ip")) {
            if(line.hasOption("ir")) {
                if (line.getOptionValue("ip").length() < 1) {
                    logger.error("An IRI pattern cannot be an empty string.  Use --help for help.");
                    return;
                }
                iriPattern = line.getOptionValue("ip");
                iriReplacement = line.getOptionValue("ir");
            } else {
                logger.error("If an IRI pattern is specified, an IRI replacement must also be specified.  Use --help for help.");
                return;
            }
        } else {
            if (line.hasOption("ir")) {
                logger.error("If an IRI replacement is specified, an IRI pattern must also be specified.  Use --help for help.");
                return;
            }
        }

        // Check if a DTD subset should be used for namespace shortening in XML
        if (line.hasOption("dtd")) {
            useDtdSubset = true;
        }

        // Check if blank nodes should be rendered inline
        if (line.hasOption("ibn")) {
            inlineBlankNodes = true;
        }

        // Check if the base URI should be set to be the same as the OWL ontology URI
        if (line.hasOption("ibi")) {
            inferBaseIri = true;
        }

        // Check if there are leading comments
        if (line.hasOption("lc")) {
            leadingComments = line.getOptionValues("lc");
        }

        // Check if there are trailing comments
        if (line.hasOption("tc")) {
            trailingComments = line.getOptionValues("tc");
        }

        // Check if there is a string data type option
        if (line.hasOption("sdt")) {
            stringDataTypeOption = SesameSortedRDFWriterFactory.StringDataTypeOptions.getByOptionValue(line.getOptionValue("sdt"));
        }

        // Check if an explicit indent string has been provided
        if (line.hasOption("i")) {
            indent = "ABC".replaceFirst("ABC", line.getOptionValue("i")); // use 'replaceFirst' to get cheap support for escaped characters like tabs
        }

        // Load RDF file.
        SesameSortedRDFWriterFactory.SourceFormats sourceFormat = null;
        if (line.hasOption("sfmt")) {
            sourceFormat = SesameSortedRDFWriterFactory.SourceFormats.getByOptionValue(line.getOptionValue("sfmt"));
        } else {
            sourceFormat = SesameSortedRDFWriterFactory.SourceFormats.auto;
        }
        if (sourceFormat == null) {
            logger.error("Unsupported or unrecognised source format: " + line.getOptionValue("sfmt"));
            return;
        }
        RDFFormat sesameSourceFormat = null;
        if (SesameSortedRDFWriterFactory.SourceFormats.auto.equals(sourceFormat)) {
            sesameSourceFormat = Rio.getParserFormatForFileName(sourceFilePath).orElse(sourceFormat.getRDFFormat());
        } else {
            sesameSourceFormat = sourceFormat.getRDFFormat();
        }
        if (sesameSourceFormat == null) {
            logger.error("Unsupported or unrecognised source format enum: " + sourceFormat);
        }

        Model sourceModel = null;
        try {
            sourceModel = Rio.parse(new FileInputStream(sourceFile), baseIriString, sesameSourceFormat);
        } catch (Throwable t) {
            logger.error(SesameRdfFormatter.class.getSimpleName() + ": stopped by unexpected exception:");
            logger.error("Unable to parse input file: " + sourceFile.getAbsolutePath());
            logger.error(t.getClass().getSimpleName() + ": " + t.getMessage());
            StringWriter stackTraceWriter = new StringWriter();
            t.printStackTrace(new PrintWriter(stackTraceWriter));
            logger.error(stackTraceWriter.toString());
            usage(options);
            System.exit(1);
        }

        // Do any URI replacements
        if ((iriPattern != null) && (iriReplacement != null)) {
            Model replacedModel = new TreeModel();
            for (Statement st : sourceModel) {
                Resource replacedSubject = st.getSubject();
                if (replacedSubject instanceof IRI) {
                    replacedSubject = valueFactory.createIRI(replacedSubject.stringValue().replaceFirst(iriPattern, iriReplacement));
                }

                IRI replacedPredicate = st.getPredicate();
                replacedPredicate = valueFactory.createIRI(replacedPredicate.stringValue().replaceFirst(iriPattern, iriReplacement));

                Value replacedObject = st.getObject();
                if (replacedObject instanceof IRI) {
                    replacedObject = valueFactory.createIRI(replacedObject.stringValue().replaceFirst(iriPattern, iriReplacement));
                }

                Statement replacedStatement = valueFactory.createStatement(replacedSubject, replacedPredicate, replacedObject);
                replacedModel.add(replacedStatement);
            }
            // Do IRI replacements in namespaces as well.
            Set<Namespace> namespaces = sourceModel.getNamespaces();
            for (Namespace nmsp : namespaces) {
                replacedModel.setNamespace(nmsp.getPrefix(), nmsp.getName().replaceFirst(iriPattern, iriReplacement));
            }
            sourceModel = replacedModel;
            // This is also the right time to do IRI replacement in the base URI, if appropriate
            if (baseIri != null) {
                baseIriString = baseIriString.replaceFirst(iriPattern, iriReplacement);
                baseIri = valueFactory.createIRI(baseIriString);
            }
        }

        // Infer the base URI, if requested
        if (inferBaseIri) {
            LinkedList<IRI> owlOntologyIris = new LinkedList<IRI>();
            for (Statement st : sourceModel) {
                if ((SesameSortedRDFWriter.rdfType.equals(st.getPredicate())) && (SesameSortedRDFWriter.owlOntology.equals(st.getObject())) && (st.getSubject() instanceof IRI)) {
                    owlOntologyIris.add((IRI)st.getSubject());
                }
            }
            if (owlOntologyIris.size() >= 1) {
                Comparator<IRI> iriComparator = new Comparator<IRI>() {
                    @Override
                    public int compare(IRI iri1, IRI iri2) {
                        return iri1.toString().compareTo(iri2.toString());
                    }
                };
                owlOntologyIris.sort(iriComparator);
                inferredBaseIri = owlOntologyIris.getFirst();
            }
        }

        // Write sorted RDF file.
        SesameSortedRDFWriterFactory.TargetFormats targetFormat = null;
        if (line.hasOption("tfmt")) {
            targetFormat = SesameSortedRDFWriterFactory.TargetFormats.getByOptionValue(line.getOptionValue("tfmt"));
        } else {
            targetFormat = SesameSortedRDFWriterFactory.TargetFormats.turtle;
        }
        if (targetFormat == null) {
            logger.error("Unsupported or unrecognised target format: " + line.getOptionValue("tfmt"));
            return;
        }
        SesameSortedRDFWriter.ShortIriPreferences shortUriPref = null;
        if (line.hasOption("sip")) {
            shortUriPref = SesameSortedRDFWriter.ShortIriPreferences.getByOptionValue(line.getOptionValue("sip"));
        } else {
            shortUriPref = SesameSortedRDFWriter.ShortIriPreferences.prefix;
        }
        if (shortUriPref == null) {
            logger.error("Unsupported or unrecognised short IRI preference: " + line.getOptionValue("sup"));
            return;
        }

        Writer targetWriter = new OutputStreamWriter(new FileOutputStream(targetFile), "UTF-8");
        SesameSortedRDFWriterFactory factory = new SesameSortedRDFWriterFactory(targetFormat);
        Map<String, Object> writerOptions = new HashMap<String, Object>();
        if (baseIri != null) {
            writerOptions.put("baseIri", baseIri);
        } else if (inferBaseIri && (inferredBaseIri != null)) {
            writerOptions.put("baseIri", inferredBaseIri);
        }
        if (indent != null) { writerOptions.put("indent", indent); }
        if (shortUriPref != null) { writerOptions.put("shortUriPref", shortUriPref); }
        writerOptions.put("useDtdSubset", useDtdSubset);
        writerOptions.put("inlineBlankNodes", inlineBlankNodes);
        writerOptions.put("leadingComments", leadingComments);
        writerOptions.put("trailingComments", trailingComments);
        writerOptions.put("stringDataTypeOption", stringDataTypeOption);
        RDFWriter rdfWriter = factory.getWriter(targetWriter, writerOptions);
        Rio.write(sourceModel, rdfWriter);
        targetWriter.flush();
        targetWriter.close();
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "SesameRdfFormatter", options );
    }

}
