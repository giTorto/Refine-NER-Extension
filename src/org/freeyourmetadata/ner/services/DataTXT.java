package org.freeyourmetadata.ner.services;

import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.freeyourmetadata.util.ExtractionException;
import org.freeyourmetadata.util.ParameterList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import static org.freeyourmetadata.util.UriUtil.createUri;

/**
 * dataTXT service connector
 * @author Stefano Parmesan
 * @author Giuliano Tortoreto
 */
public class DataTXT extends NERServiceBase {
    private final static URI SERVICEBASEURL = createUri("https://api.dandelion.eu/datatxt/nex/v1");
    private final static URI DOCUMENTATIONURI = createUri("https://dandelion.eu/docs/api/datatxt/nex/v1/");
    private final static String[] SERVICESETTINGS = { "App ID", "App key"};
    private final static String[] EXTRACTIONSETTINGS = {"Language", "Confidence", "Parse hashtag", "Min length"};

    /**
     * Creates a new dataTXT service connector
     */
    public DataTXT() {
        super(SERVICEBASEURL, DOCUMENTATIONURI, SERVICESETTINGS, EXTRACTIONSETTINGS);
        setExtractionSettingDefault("Language", "auto");
        setExtractionSettingDefault("Confidence", "0.6");
        setExtractionSettingDefault("Parse hashtag", "false");
        setExtractionSettingDefault("Min length", "2");
    }

    /** {@inheritDoc} */
    public boolean isConfigured() {
        return getServiceSetting("App ID").length() > 0
                && getServiceSetting("App key").length() > 0;
    }

    /** {@inheritDoc} */
    protected HttpEntity createExtractionRequestBody(final String text, final Map<String, String> extractionSettings)
    throws UnsupportedEncodingException {
        final ParameterList parameters = new ParameterList();
        parameters.add("lang", extractionSettings.get("Language"));
        parameters.add("text", text);
        parameters.add("min_confidence", extractionSettings.get("Confidence"));
        parameters.add("min_length", extractionSettings.get("Min length"));
        parameters.add("parse_hashtag", extractionSettings.get("Parse hashtag"));
        parameters.add("$app_id", getServiceSetting("App ID"));
        parameters.add("$app_key", getServiceSetting("App key"));
        return parameters.toEntity();
    }

    /** {@inheritDoc} */
    @Override
    protected NamedEntity[] parseExtractionResponseEntity(final JSONTokener tokener) throws JSONException {
        // Check response status
        final JSONObject response = (JSONObject)tokener.nextValue();
        if (!response.isNull("error"))
            throw new IllegalArgumentException("dataTXT request failed.");
        
        // Find all annotations
        final JSONArray annotations = response.getJSONArray("annotations");
        final NamedEntity[] results = new NamedEntity[annotations.length()];
        for (int i = 0; i < results.length; i++) {
            final JSONObject annotation = annotations.getJSONObject(i);
            final String label = annotation.getString("title");
            final double score = annotation.getDouble("confidence");
            final ArrayList<Disambiguation> disambiguations = new ArrayList<Disambiguation>();

            disambiguations.add(new Disambiguation(label, createUri(annotation.getString("uri")), score));
            results[i] = new NamedEntity(annotation.getString("spot"), disambiguations);
        }
        
        return results;
    }

    @Override
    public String onError(ExtractionException e) {

        if (e==null)
            return null;

        try {
            String body = EntityUtils.toString(e.getResponse().getEntity());
            JSONObject returnedResult = new JSONObject(body);
            body = returnedResult.getString("message");
            if(body!=null)
                return body;

        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (JSONException e1) {
            e1.printStackTrace();
        }

        return e.getMessage();
    }

}
