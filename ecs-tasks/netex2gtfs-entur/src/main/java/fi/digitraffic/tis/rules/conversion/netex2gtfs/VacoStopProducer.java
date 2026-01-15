package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import org.entur.netex.gtfs.export.producer.DefaultStopProducer;
import org.entur.netex.gtfs.export.repository.GtfsDatasetRepository;
import org.entur.netex.gtfs.export.stop.StopAreaRepository;
import org.onebusaway.gtfs.model.Stop;
import org.rutebanken.netex.model.PrivateCodeStructure;
import org.rutebanken.netex.model.Quay;

/**
 * Custom StopProducer that enriches GTFS stops by mapping NeTEx Quay privateCode to GTFS stop_code field.
 */
public class VacoStopProducer extends DefaultStopProducer {

    public VacoStopProducer(
            StopAreaRepository stopAreaRepository,
            GtfsDatasetRepository gtfsDatasetRepository) {
        super(stopAreaRepository, gtfsDatasetRepository);
    }

    /**
     * Produce a GTFS stop from a NeTEx quay with privateCode enrichment.
     * Calls the parent implementation and then enriches the stop_code field
     * with the value from the Quay's privateCode.
     *
     * @param quay the NeTEx Quay
     * @return the GTFS stop enriched with privateCode in stop_code field
     */
    @Override
    public Stop produceStopFromQuay(Quay quay) {
        Stop stop = super.produceStopFromQuay(quay);

        // Extract privateCode and set it as GTFS stop_code
        PrivateCodeStructure privateCode = quay.getPrivateCode();
        if (privateCode != null && privateCode.getValue() != null && !privateCode.getValue().isBlank()) {
            stop.setCode(privateCode.getValue());
        }

        return stop;
    }
}
