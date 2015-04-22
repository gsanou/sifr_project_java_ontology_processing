import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import com.hp.hpl.jena.reasoner.rulesys.builtins.Remove;
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
        if (directoryListing != null) {
            for (File ontologyFile : directoryListing) {
                OWLOntologyCleaner oc = new OWLOntologyCleaner(ontologyFile);

                oc.cleanMultilingualOntology("fr");

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
        File outputFile = new File("ontology_files/lso/" + outputFilename);
        RDFXMLDocumentFormat rdfxmlFormat = new RDFXMLDocumentFormat();
        try {
            manager.saveOntology(ontology, rdfxmlFormat, IRI.create(outputFile.toURI()));
        } catch (OWLOntologyStorageException e) {
            e.printStackTrace();
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
