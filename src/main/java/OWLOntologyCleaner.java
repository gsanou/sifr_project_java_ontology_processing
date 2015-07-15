import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import com.hp.hpl.jena.reasoner.rulesys.builtins.Remove;
import org.apache.commons.lang3.StringUtils;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;


/**
 * Created by Vincent on 15/04/2015.
 */
public class OWLOntologyCleaner {

    static OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    static OWLDataFactory df = OWLManager.getOWLDataFactory();

    private OWLOntology ontology;
    private OWLOntology cleanedOntology;

    public OWLOntologyCleaner(File ontologyFile) {
        //File ontologyFile = new File(ontologyFileName);
        try {
            //Load ontology from file
            ontology = manager.loadOntologyFromOntologyDocument(ontologyFile);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
    }

    public static void main(String [ ] args) throws OWLOntologyCreationException {
        //String ontologyFileName = "ontology_files/ONTOTOXNUC.owl";
        String dirPath = "ontology_files/cmo/";

        File dir = new File(dirPath);
        File[] directoryListing = dir.listFiles();
        // Go through ontology files in the directory
        if (directoryListing != null) {
            for (File ontologyFile : directoryListing) {
                OWLOntologyCleaner oc = new OWLOntologyCleaner(ontologyFile);
                if (ontologyFile.getName().equals("ONTOPNEUMO.owl")) {
                    oc.cleanOntopneumoOntology();
                }

                //Clean the ontology by only keeping the lang asked for literals.
                oc.cleanMultilingualOntology("en");

                //oc.printLabels();
                oc.outputOntology(ontologyFile.getName());
                System.out.println(ontologyFile.getName() + " : DONE");
            }
        }
    }

    public void printLabels() {

        for (OWLClass cls : ontology.getClassesInSignature()) {
            // Get the annotations on the class that use the label property
            //System.out.println(cls);
            for (OWLAnnotation annotation : EntitySearcher.getAnnotations(cls.getIRI(), ontology)) {
                //System.out.println(annotation);
                if (annotation.getValue() instanceof OWLLiteral) {
                    OWLLiteral val = (OWLLiteral) annotation.getValue();
                    // look for french labels
                    if (val.hasLang("fr") || val.hasLang("en") || val.hasLang("")) {
                        System.out.println(cls + " " + annotation.getProperty() + " " + val.getLiteral() + " " + val.getLang());
                    }
                }
            }
        }
    }

    public void outputOntology(String outputFilename) {
        // Save the ontology in a file in ontology_files

        File outputFile = new File("ontology_files/lso/" + outputFilename);
        RDFXMLDocumentFormat rdfxmlFormat = new RDFXMLDocumentFormat();
        try {
            manager.saveOntology(ontology, rdfxmlFormat, IRI.create(outputFile.toURI()));
        } catch (OWLOntologyStorageException e) {
            e.printStackTrace();
        }
    }

    public void cleanOntopneumoOntology() {
        // A method to clean the very special case of ONTOPNEUMO

        String badLabel = "";
        String goodLabel = "";
        String altLabelVal = "";
        Pattern pattern = Pattern.compile("\"(.*?)\"");
        OWLAnnotationProperty prefLabelProperty = df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel"));

        for (OWLClass cls : ontology.getClassesInSignature()) {
            boolean noPrefLabel = true;
            boolean asAltLabel = false;

            // Go through all annotations of the class, if it gets only a skos:hiddenLabel, then it separates the word at
            // each capital letter.
            for (OWLAnnotationAssertionAxiom annAx : EntitySearcher.getAnnotationAssertionAxioms(cls.getIRI(), ontology)) {
                if (annAx.getProperty().toString().equals("<http://www.w3.org/2004/02/skos/core#hiddenLabel>")) {
                    // Extracting only the literal value (without @lang)
                    Matcher matcher = pattern.matcher(annAx.getValue().toString());
                    if (matcher.find())
                    {
                        badLabel = matcher.group(1);
                    }
                } else if (annAx.getProperty().toString().equals("<http://www.w3.org/2004/02/skos/core#altLabel>")) {
                    asAltLabel = true;
                    Matcher matcher = pattern.matcher(annAx.getValue().toString());
                    if (matcher.find())
                    {
                        altLabelVal = matcher.group(1);
                    }
                } else  if (annAx.getProperty().toString().equals("<http://www.w3.org/2004/02/skos/core#prefLabel>")) {
                    noPrefLabel = false;
                }
            }

            // Now that we know if the class as a prefLabel and a altLabel we can generate the good label
            // if no prefLabel : generated from altLabel. If no altLabel : prefLabel generated from hiddenlabel after transformations
            if (noPrefLabel == true) {

                if (asAltLabel == true) {
                    goodLabel = altLabelVal;
                } else  {
                    // If there is only a skos:hiddenLabel property we split the string at each capital letter
                    // (except if the whole string is in capital) and put it in a skos:prefLabel property

                    if (!StringUtils.isAllUpperCase(badLabel)) {
                        String[] arrayLabel = badLabel.split("(?=[A-Z])");
                        StringBuilder builder = new StringBuilder();
                        for (String s : arrayLabel) {
                            builder.append(s + " ");
                        }
                        goodLabel = builder.toString().trim().toLowerCase();
                        goodLabel = goodLabel.replaceAll(" d l c o", " DLCO").replaceAll(" p c o2", " pCO2").replaceAll(" c o2", " CO2").replaceAll(" p o2", " pO2").replaceAll(" g t", " GT");
                        goodLabel = goodLabel.replaceAll(" v i i i", " VIII").replaceAll(" v i i", " VII").replaceAll(" v i", " VI").replaceAll(" x i i", " XII").replaceAll(" x i", " XI").replaceAll(" i i", " II");
                        goodLabel = goodLabel.replaceAll(" l ", " l'").replaceAll(" d ", " d'");
                    } else {
                        goodLabel = badLabel;
                    }


                    //System.out.println(goodLabel);
                }

                // Generate the axiom with skos:prefLabel as property and the good label
                OWLAnnotation prefLabel = df.getOWLAnnotation(prefLabelProperty, df.getOWLLiteral(goodLabel, "fr"));
                OWLAxiom newAxiom = df.getOWLAnnotationAssertionAxiom(cls.getIRI(), prefLabel);
                manager.applyChange(new AddAxiom(ontology, newAxiom));

            }

        }

    }

    public void cleanMultilingualOntology(String lang) {
        //Clean the ontology by only keeping the lang asked for literals.
        //If the asked lang is not avalaible for the property of a class it keeps other langs
        List<String> listPropInLang = new ArrayList<String>();
        HashMap<String, List<RemoveAxiom>> removalHash = new HashMap<String, List<RemoveAxiom>>();

        for (OWLClass cls : ontology.getClassesInSignature()) {
            // Get the annotations on the class that use the label property
            listPropInLang.clear();
            removalHash.clear();

            for (OWLAnnotationAssertionAxiom annAx : EntitySearcher.getAnnotationAssertionAxioms(cls.getIRI(), ontology)) {
                if (annAx.getValue() instanceof OWLLiteral) {
                    OWLLiteral val = (OWLLiteral) annAx.getValue();

                    // If the annotation is in the lang we want then the property is added to a list (listPropInLang)
                    // to know that for this class this property is in the wanted lang (so we can remove other lang)
                    // If there is no annotation in the wanted lang for this class + property then the change is not applied
                    if (val.hasLang(lang)) {
                        listPropInLang.add(annAx.getProperty().toString());
                    } else if (!val.getLang().equals(lang) && !val.getLang().equals("")) {
                        RemoveAxiom rm = new RemoveAxiom(ontology, annAx);

                        if (removalHash.containsKey(annAx.getProperty().toString())) {
                            removalHash.get(annAx.getProperty().toString()).add(rm);
                        } else {
                            List<RemoveAxiom> removalList = new ArrayList<RemoveAxiom>();
                            removalList.add(rm);
                            removalHash.put(annAx.getProperty().toString(), removalList);
                        }
                    }

                }
            }

            //Iterate the list containing the property that have an annotation with the asked lang
            //And applyChange to the manager to remove those annotations from the ontology
            for (String propToRemove : listPropInLang) {
                if (removalHash.containsKey(propToRemove)) {
                    for (RemoveAxiom rma : removalHash.get(propToRemove)) {
                        manager.applyChange(rma);
                    }
                }
            }
        }

        for (OWLObjectProperty obj : ontology.getObjectPropertiesInSignature()) {
            // Same process than before but for ObjectProperties
            listPropInLang.clear();
            removalHash.clear();

            for (OWLAnnotationAssertionAxiom annAx : EntitySearcher.getAnnotationAssertionAxioms(obj.getIRI(), ontology)) {
                if (annAx.getValue() instanceof OWLLiteral) {
                    OWLLiteral val = (OWLLiteral) annAx.getValue();
                    if (val.hasLang(lang)) {
                        listPropInLang.add(annAx.getProperty().toString());
                    } else if (!val.getLang().equals(lang) && !val.getLang().equals("")) {
                        RemoveAxiom rm = new RemoveAxiom(ontology, annAx);

                        if (removalHash.containsKey(annAx.getProperty().toString())) {
                            removalHash.get(annAx.getProperty().toString()).add(rm);
                        } else {
                            List<RemoveAxiom> removalList = new ArrayList<RemoveAxiom>();
                            removalList.add(rm);
                            removalHash.put(annAx.getProperty().toString(), removalList);
                        }
                    }

                }
            }
            for (String propToRemove : listPropInLang) {
                if (removalHash.containsKey(propToRemove)) {
                    for (RemoveAxiom rma : removalHash.get(propToRemove)) {
                        manager.applyChange(rma);
                    }
                }
            }
        }
    }



}
