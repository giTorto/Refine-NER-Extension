package org.freeyourmetadata.util;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

/**
 * An Extraction Exception with the request and the response that caused it.
 * @author Giuliano Tortoreto
 */
public class ExtractionException extends java.lang.RuntimeException {
    private HttpUriRequest request;
    private HttpResponse response;

    /**
     * Creates a new ExtractionException with HttpUriRequest and HttpResponse that throwed the exception
     * and a message
     * @param request The label of the HttpUriRequest
     * @param response The label of the HttpResponse
     * @param message The message to show
     */
    public ExtractionException(HttpUriRequest request, HttpResponse response, java.lang.String message) {
        super(message);
        this.request = request;
        this.response = response;
    }

    public HttpUriRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }
}
