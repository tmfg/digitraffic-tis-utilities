package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import org.entur.netex.gtfs.export.repository.DefaultGtfsRepository;
import org.entur.netex.gtfs.export.repository.GtfsDatasetRepository;
import org.entur.netex.gtfs.export.stop.StopAreaRepository;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.model.Stop;
import org.rutebanken.netex.model.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for VacoStopProducer.
 * Modeled after StopProducerTest from the Entur netex-gtfs-converter library.
 */
class VacoStopProducerTest {

    private static final ObjectFactory NETEX_FACTORY = new ObjectFactory();

    private static final String QUAY_ID = "FTR:Quay:1";
    private static final String STOP_PLACE_ID = "FTR:StopPlace:1";
    private static final double LONGITUDE = 25.0;
    private static final double LATITUDE = 60.0;
    private static final String TEST_STOP_PLACE_NAME = "Test Stop";
    private static final String TEST_PRIVATE_CODE = "12345";

    @Test
    void testPrivateCodeIsCopiedToStopCode() {
        Quay quay = createTestQuay(QUAY_ID, LONGITUDE, LATITUDE);
        PrivateCodeStructure privateCode = new PrivateCodeStructure();
        privateCode.setValue(TEST_PRIVATE_CODE);
        quay.setPrivateCode(privateCode);

        StopPlace stopPlace = createTestStopPlace(STOP_PLACE_ID);

        StopAreaRepository stopAreaRepository = mock(StopAreaRepository.class);
        when(stopAreaRepository.getStopPlaceByQuayId(QUAY_ID)).thenReturn(stopPlace);

        GtfsDatasetRepository gtfsDatasetRepository = new DefaultGtfsRepository();
        VacoStopProducer stopProducer = new VacoStopProducer(stopAreaRepository, gtfsDatasetRepository);

        Stop stop = stopProducer.produceStopFromQuay(quay);

        assertNotNull(stop);
        assertEquals(TEST_PRIVATE_CODE, stop.getCode());
    }

    @Test
    void testNullPrivateCodeDoesNotSetStopCode() {
        Quay quay = createTestQuay(QUAY_ID, LONGITUDE, LATITUDE);
        quay.setPrivateCode(null);

        StopPlace stopPlace = createTestStopPlace(STOP_PLACE_ID);

        StopAreaRepository stopAreaRepository = mock(StopAreaRepository.class);
        when(stopAreaRepository.getStopPlaceByQuayId(QUAY_ID)).thenReturn(stopPlace);

        GtfsDatasetRepository gtfsDatasetRepository = new DefaultGtfsRepository();
        VacoStopProducer stopProducer = new VacoStopProducer(stopAreaRepository, gtfsDatasetRepository);

        Stop stop = stopProducer.produceStopFromQuay(quay);

        assertNotNull(stop);
        assertNull(stop.getCode());
    }

    @Test
    void testBlankPrivateCodeDoesNotSetStopCode() {
        Quay quay = createTestQuay(QUAY_ID, LONGITUDE, LATITUDE);
        PrivateCodeStructure privateCode = new PrivateCodeStructure();
        privateCode.setValue("   ");
        quay.setPrivateCode(privateCode);

        StopPlace stopPlace = createTestStopPlace(STOP_PLACE_ID);

        StopAreaRepository stopAreaRepository = mock(StopAreaRepository.class);
        when(stopAreaRepository.getStopPlaceByQuayId(QUAY_ID)).thenReturn(stopPlace);

        GtfsDatasetRepository gtfsDatasetRepository = new DefaultGtfsRepository();
        VacoStopProducer stopProducer = new VacoStopProducer(stopAreaRepository, gtfsDatasetRepository);

        Stop stop = stopProducer.produceStopFromQuay(quay);

        assertNotNull(stop);
        assertNull(stop.getCode());
    }

    @Test
    void testEmptyPrivateCodeValueDoesNotSetStopCode() {
        Quay quay = createTestQuay(QUAY_ID, LONGITUDE, LATITUDE);
        PrivateCodeStructure privateCode = new PrivateCodeStructure();
        privateCode.setValue(null);
        quay.setPrivateCode(privateCode);

        StopPlace stopPlace = createTestStopPlace(STOP_PLACE_ID);

        StopAreaRepository stopAreaRepository = mock(StopAreaRepository.class);
        when(stopAreaRepository.getStopPlaceByQuayId(QUAY_ID)).thenReturn(stopPlace);

        GtfsDatasetRepository gtfsDatasetRepository = new DefaultGtfsRepository();
        VacoStopProducer stopProducer = new VacoStopProducer(stopAreaRepository, gtfsDatasetRepository);

        Stop stop = stopProducer.produceStopFromQuay(quay);

        assertNotNull(stop);
        assertNull(stop.getCode());
    }

    private StopPlace createTestStopPlace(String stopPlaceId) {
        StopPlace stopPlace = new StopPlace();
        stopPlace.setId(stopPlaceId);
        MultilingualString stopPlaceName = NETEX_FACTORY.createMultilingualString();
        stopPlaceName.setValue(TEST_STOP_PLACE_NAME);
        stopPlace.setName(stopPlaceName);
        return stopPlace;
    }

    private Quay createTestQuay(String quayId, double longitude, double latitude) {
        Quay quay = new Quay();
        quay.setId(quayId);
        SimplePoint_VersionStructure centroid = new SimplePoint_VersionStructure();
        LocationStructure location = new LocationStructure();
        location.setLongitude(BigDecimal.valueOf(longitude));
        location.setLatitude(BigDecimal.valueOf(latitude));
        centroid.setLocation(location);
        quay.setCentroid(centroid);
        return quay;
    }
}
