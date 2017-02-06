package org.sifrproject.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NcboSparqlOutputToTTL implements ContentHandler {

    private static final Logger logger = LoggerFactory.getLogger(NcboSparqlOutputToTTL.class);

    private String currentContent;
    private String currentSubject;
    private String currentObject;

    private final String relationURL;
    private final PrintWriter outputWriter;


    public NcboSparqlOutputToTTL(final String relationURL, final PrintWriter outputWriter) {
        this.relationURL = relationURL;
        this.outputWriter = outputWriter;
    }

    @Override
    public void setDocumentLocator(final Locator locator) {

    }

    @SuppressWarnings("HardcodedFileSeparator")
    @Override
    public void startDocument() throws SAXException {
        outputWriter.println("@prefix skos: <http://www.w3.org/2004/02/skos/core#> .");
        outputWriter.println("@prefix owl:  <http://www.w3.org/2002/07/owl#> .");
        outputWriter.println("@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .");
        outputWriter.println("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .");
        outputWriter.println("@prefix umls: <http://bioportal.bioontology.org/ontologies/umls#> .");
    }

    @Override
    public void endDocument() throws SAXException {

    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {

    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {

    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
        currentContent = "";
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        switch (localName) {
            case "uri":
                currentSubject = currentContent;
                break;
            case "literal":
                currentObject = currentContent;
                break;
            case "result":
                outputWriter.println(String.format("<%s> %s \"\"\"%s\"\"\"^^xsd:string.", currentSubject, relationURL, currentObject));
                break;
            default:
                break;
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        for (int i = start; i < (start + length); i++) {
            currentContent += ch[i];
        }
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {

    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {

    }

    @Override
    public void skippedEntity(final String name) throws SAXException {

    }

    private static void syntax() {
        logger.error("Syntax: command [cui xml file] [tui xml file]");
        System.exit(1);
    }

    public static void main(final String... args) throws SAXException, IOException {

        if (args.length < 2) {
            syntax();
        }

        final Path cuiPath = Paths.get(args[0]);
        final String outputFile = "tuicuimodel.ttl";

        final Path tuiPath =Paths.get(args[1]);

        try (PrintWriter outputWriter = new PrintWriter(outputFile)) {


            logger.info("Processing cuis...");
            final XMLReader cuiSAXReader = XMLReaderFactory.createXMLReader();
            cuiSAXReader.setContentHandler(new NcboSparqlOutputToTTL("umls:cui", outputWriter));
            cuiSAXReader.parse(new InputSource(new FileInputStream(cuiPath.toFile())));

            logger.info("Processing tuis...");
            final XMLReader tuiSAXReader = XMLReaderFactory.createXMLReader();
            tuiSAXReader.setContentHandler(new NcboSparqlOutputToTTL("umls:tui", outputWriter));
            tuiSAXReader.parse(new InputSource(new FileInputStream(tuiPath.toFile())));

            logger.info("Output written to tuicuimodel.ttl");

        }
    }
}
