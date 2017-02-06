package org.sifrproject.ontology;


public enum UMLSLanguageCode {

    ENGLISH("ENG"),
    FRENCH("FRE"),
    CZECH("CZE"),
    FINNISH("FIN"),
    GERMAN("GER"),
    ITALIAN("ITA"),
    JAPANESE("JPN"),
    POLISH("POL"),
    PORTUGUESE("POR"),
    RUSSIAN("RUS"),
    SPANISH("SPA"),
    SWEDISH("SWE"),
    SERBO_CROATIAN("SCR"),
    DUTCH("DUT"),
    LATVIAN("LAV"),
    HUNGARIAN("HUN"),
    KOREAN("KOR"),
    DANISH("DAN"),
    NORWEGIAN("NOR"),
    HEBREW("HEB"),
    BASQUE("BAQ");

    UMLSLanguageCode(final String languageCode) {
        this.languageCode = languageCode;
    }

    private final String languageCode;

    @SuppressWarnings("PublicMethodNotExposedInInterface")
    public String getLanguageCode() {
        return languageCode;
    }
}
