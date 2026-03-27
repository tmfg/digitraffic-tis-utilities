package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import org.entur.netex.gtfs.export.producer.DefaultStopProducer;
import org.entur.netex.gtfs.export.repository.GtfsDatasetRepository;
import org.entur.netex.gtfs.export.stop.StopAreaRepository;
import org.onebusaway.gtfs.model.Stop;
import org.rutebanken.netex.model.KeyListStructure;
import org.rutebanken.netex.model.KeyValueStructure;
import org.rutebanken.netex.model.PrivateCodeStructure;
import org.rutebanken.netex.model.Quay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * Produce a GTFS stop from a NeTEx quay with privateCode / digiroad_id enrichment.
     * Calls the parent implementation and then enriches the stop_code / mtaStopId field
     * with the value from the Quay's privateCode / digiroad_id.
     *
     * @param quay the NeTEx Quay
     * @return the GTFS stop enriched with privateCode / digiroad_id in stop_code / mtaStopId field
     */
    @Override
    public Stop produceStopFromQuay(Quay quay) {
        Stop stop = super.produceStopFromQuay(quay);

        // Extract privateCode and set it as GTFS stop_code
        PrivateCodeStructure privateCode = quay.getPrivateCode();
        if (privateCode != null && privateCode.getValue() != null && !privateCode.getValue().isBlank()) {
            stop.setCode(privateCode.getValue());
        }

        // Extract digiroad_id from key values and copy to GTFS
        KeyListStructure keyValueStructure = quay.getKeyList();
        List<KeyValueStructure> keyValueStructureList = keyValueStructure != null ? keyValueStructure.getKeyValue() : List.of();
        Optional<KeyValueStructure> digiroadIdKeyValueStructure = keyValueStructureList.stream()
                .filter(k -> k.getKey() != null && k.getKey().equals("digiroad_id")).findFirst();

        if (digiroadIdKeyValueStructure.isPresent() && digiroadIdKeyValueStructure.get().getValue() != null && !digiroadIdKeyValueStructure.get().getValue().isBlank()) {
            stop.putExtension(DigiroadIdStopExtension.class, new DigiroadIdStopExtension(digiroadIdKeyValueStructure.get().getValue()));
        }

        return stop;
    }
}
