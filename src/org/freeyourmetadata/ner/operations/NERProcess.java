package org.freeyourmetadata.ner.operations;

import com.google.refine.browsing.Engine;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.history.HistoryEntry;
import com.google.refine.model.*;
import com.google.refine.process.LongRunningProcess;
import org.apache.log4j.Logger;
import org.freeyourmetadata.ner.services.ExtractionResult;
import org.freeyourmetadata.ner.services.NERService;
import org.freeyourmetadata.ner.services.NamedEntity;
import org.freeyourmetadata.util.ExtractionException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Process that executes named-entity recognition services
 * and aggregates their results.
 * @author Ruben Verborgh
 */
public class NERProcess extends LongRunningProcess implements Runnable {
    private final static Logger LOGGER = Logger.getLogger(NERProcess.class);
    private final static ExtractionResult[] EMPTY_RESULT_SET = new ExtractionResult[0];

    private final Project project;
    private final Column column;
    private final Map<String, NERService> services;
    private final Map<String, Map<String,String>> settings;
    private final AbstractOperation parentOperation;
    private final JSONObject engineConfig;
    private final long historyEntryId;

    /**
     * Creates a new <tt>NERProcess</tt>
     * @param project The project
     * @param column The column on which named-entity recognition is performed
     * @param services The services that will be used for named-entity recognition
     * @param settings The settings of the individual services
     * @param parentOperation The operation that creates this process
     * @param description The description of this operation
     * @param engineConfig The faceted browsing engine configuration
     */
    protected NERProcess(final Project project, final Column column,
    		             final Map<String, NERService> services, final Map<String, Map<String, String>> settings,
                         final AbstractOperation parentOperation, final String description,
                         final JSONObject engineConfig) {
        super(description);
        this.project = project;
        this.column = column;
        this.services = services;
        this.settings = settings;
        this.parentOperation = parentOperation;
        this.engineConfig = engineConfig;
        historyEntryId = HistoryEntry.allocateID();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        final int columnIndex = project.columnModel.getColumnIndexByName(column.getName()) + 1;
        final String[] serviceNames = services.keySet().toArray(new String[services.size()]);
        final ExtractionResult[][] namedEntities = performExtraction();
        
        if (!_canceled) {
            project.history.addEntry(new HistoryEntry(historyEntryId, project, _description, parentOperation,
                                                      new NERChange(columnIndex, serviceNames, namedEntities)));
            project.processManager.onDoneProcess(this);
        }
    }

    /**
     * Performs named-entity extraction on all rows
     * @return The extracted named entities per row and service
     */
    protected ExtractionResult[][] performExtraction() {
        // Count all rows
        final int rowsTotal = project.rows.size();
        // Get the cell index of the column in which to perform entity extraction
        final int cellIndex = column.getCellIndex();
        // Get the filtered rows
        final Set<Integer> filteredRowIndices = getFilteredRowIndices();
        final int rowsFiltered = filteredRowIndices.size();
        
        // Go through each row and extract entities if the row is part of the filter
        final ExtractionResult[][] namedEntities = new ExtractionResult[rowsTotal][];
        int rowsProcessed = 0;
        for (int rowIndex = 0; rowIndex < rowsTotal; rowIndex++) {
            // Initialize to the empty result set, in case no entities are extracted
            namedEntities[rowIndex] = EMPTY_RESULT_SET;
            // If the row is part of the filter, extract entities
            if (filteredRowIndices.contains(rowIndex)) {
                final Row row = project.rows.get(rowIndex);
                // Determine the text value of the cell
                final Cell cell = row.getCell(cellIndex);
                final Serializable cellValue = cell == null ? null : cell.value;
                final String text = cellValue == null ? "" : cellValue.toString().trim();
                // Perform extraction if the text is not empty
                if (text.length() > 0) {
                    LOGGER.info(String.format("Extracting named entities in column %s on row %d of %d.",
                                              column.getName(), rowsProcessed + 1, rowsFiltered));
                    namedEntities[rowIndex] = performExtraction(text);
                }
                _progress = 100 * ++rowsProcessed / rowsFiltered;
            }
            // Exit directly if the process has been cancelled
            if (_canceled)
                return null;
        }
        return namedEntities;
    }

    
    /**
     * Performs named-entity extraction on the specified text
     * @param text The text
     * @return The extracted named entities per service
     */
    protected ExtractionResult[] performExtraction(final String text) {
        // The execution of the services happens in parallel.
        // Create the extractors and corresponding threads
        final Extractor[] extractors = new Extractor[services.size()];
        int i = 0;
        for (final Map.Entry<String, NERService> service : services.entrySet()) {
            final Extractor extractor = extractors[i++]
              = new Extractor(text, service.getValue(), settings.get(service.getKey()));
            extractor.start();
        }
        
        // Wait for all threads to finish and collect their results
        final ExtractionResult[] extractions = new ExtractionResult[extractors.length];
        for (i = 0; i < extractors.length; i++) {
            try {
                extractors[i].join();
            }
            catch (InterruptedException error) {
                LOGGER.error("The extractor was interrupted", error);
            }
            extractions[i] = extractors[i].getExtractionResult();
        }
        return extractions;
    }
    
    /**
     * Gets the indices of all rows that are part of the active selection filter
     * @return The filtered rows
     */
    protected Set<Integer> getFilteredRowIndices() {
        // Load the faceted browsing engine and configuration (including row filters)
        final Engine engine = new Engine(project);
        try { engine.initializeFromJSON(engineConfig); }
        catch (Exception e) {}
        
        // Collect indices of rows that belong to the filter
        final HashSet<Integer> filteredRowIndices = new HashSet<Integer>(project.rows.size());
        engine.getAllFilteredRows().accept(project, new RowVisitor() {
            @Override
            public boolean visit(final Project project, final int rowIndex, final Row row) {
                filteredRowIndices.add(rowIndex);
                return false;
            }
            @Override
            public void start(Project project) {}
            @Override
            public void end(Project project) {}
        });
        return filteredRowIndices;
    }

    /** {@inheritDoc} */
    @Override
    protected Runnable getRunnable() {
        return this;
    }
    
    /**
     * Thread that executes a named-entity recognition service
     */
    protected static class Extractor extends Thread {
        private final static NamedEntity[] EMPTY_ENTITY_SET = new NamedEntity[0];
        
        private final String text;
        private final NERService service;
        private final Map<String,String> settings;
        private NamedEntity[] namedEntities;
        private ExtractionException error = null;

        /**
         * Creates a new <tt>Extractor</tt>
         * @param text The text to analyze
         * @param service The service that will analyze the text
         * @param settings The extraction settings
         */
        public Extractor(final String text, final NERService service, final Map<String, String> settings) {
            this.text = text;
            this.service = service;
            this.settings = settings;
            this.namedEntities = EMPTY_ENTITY_SET;
        }
        
        /**
         * Gets the named entities the service extracted from the text
         * @return The extracted named entities
         */
        public NamedEntity[] getNamedEntities() {
            return namedEntities;
        }
        
        /** {@inheritDoc} */
        @Override
        public void run() {
            try {
                namedEntities = service.extractNamedEntities(text, settings);
            }
            catch (ExtractionException error) {
                this.error = error;
            }catch (Exception e) {
                LOGGER.error("The extractor failed", e);
            }
        }

        public ExtractionResult getExtractionResult() {
            return (new ExtractionResult(getNamedEntities(), service.onError(this.error)));
        }
    }
}
