Java Ontology pre-processing utilities used for the SIFR project at LIRMM
--------------


# Multilingual ontology cleaner using OWLAPI

- Open the project
- Add the ontologies you want to be monolingual in ontology_files/cmo
- In OWLOntologyCleaner main method change "oc.cleanMultilingualOntology("en");" to the language you want to keep
- Run it
- Get the monolingual ontology in ontology_files/lso

http://owlapi.sourceforge.net
https://github.com/owlcs/owlapi

http://www.lirmm.fr/sifr/


#CUI and semantic group enrichment using interportal mappings

##Requirements
  1. The source ontologies
  2. The ttl file for the mappings, found here under data/
  3. The target ontologies for the mappings
  4. A redis server running on the same machine on its default port
  5. Access to an SQL version of the UMLS Meta-thesaurus 

##Steps

  1. Create a TDB repository with the source ontology files
  2. Create a TDB repository with the target ontology files
  3. Create a TDB repository with the mappings 
  4. Modify configuration parameters in src/main/resources/config.properties to specify the source/target/mappings repository and the database access credentials. 
  5. Run the org.sifrproject.cli.OntologyCUIProcessor class, by specifying the output file extention, the format and the path to the ontology file to enritch. 

