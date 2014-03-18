package org.freeyourmetadata.ner.services;

/**
 * This class contains the list of the Named Entity extracted by a service and an error message
 * in case of the service request throws an exception
 * @author Giuliano Tortoreto
 */
public class ExtractionResult {
    private NamedEntity[] namedEntities =null;
    private String errorMessage = null;

    /**
     * Creates a new extraction result
     * @param namedEntities The list of the found Named Entities
     * @param errorMessage The custom error message to show to the user
     */
    public ExtractionResult(NamedEntity[] namedEntities, String errorMessage) {
        this.namedEntities = namedEntities;
        this.errorMessage = errorMessage;

    }

    /**
     * Checks if the service has thrown an error on HttpUriRequest
     * @return true if there is an error, otherwise false
     */
    public boolean isError() {
        return errorMessage!=null;
    }

    public NamedEntity[] getNamedEntities() {
        return namedEntities;
    }

    public void setNamedEntities(NamedEntity[] namedEntities) {
        this.namedEntities = namedEntities;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
