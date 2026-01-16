package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import org.entur.netex.gtfs.export.DefaultGtfsExporter;
import org.entur.netex.gtfs.export.stop.StopAreaRepository;

/**
 * Custom GTFS exporter that populates stop_code from NeTEx Quay privateCode.
 * Uses custom VacoStopProducer for stop production.
 */
public class VacoGtfsExporter extends DefaultGtfsExporter {

    /**
     * Create a GTFS exporter with privateCode enrichment.
     *
     * @param codespace          the codespace of the exported dataset.
     * @param stopAreaRepository the stop area repository.
     */
    public VacoGtfsExporter(String codespace, StopAreaRepository stopAreaRepository) {
        super(codespace, stopAreaRepository);
        setStopProducer(new VacoStopProducer(stopAreaRepository, getGtfsDatasetRepository()));
    }
}
