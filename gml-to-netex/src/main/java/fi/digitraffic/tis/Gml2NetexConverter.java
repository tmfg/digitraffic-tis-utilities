package fi.digitraffic.tis;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import net.opengis.gml._3.*;
import net.opengis.gml._3.ObjectFactory;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.gml3.GMLConfiguration;
import org.geotools.xsd.Parser;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.rutebanken.netex.model.*;
import org.rutebanken.netex.validation.NeTExValidator;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;


public class Gml2NetexConverter {

    private static final String CODESPACE = "FSR";
    private static final AtomicInteger idCounter = new AtomicInteger(1);
    private static final ObjectFactory gmlObjectFactory = new ObjectFactory();
    private static final Map<String, AtomicInteger> codespaceIds = new HashMap<>();

    private static AbstractRingPropertyType abstractRingFromCoordinates(Coordinate[] coordinates) {
        // Coordinate order is Y X
        List<Double> coords = Arrays.stream(coordinates).flatMap(coordinate -> Stream.of(coordinate.y, coordinate.x)).toList();

        return new AbstractRingPropertyType()
                .withAbstractRing(gmlObjectFactory.createLinearRing(
                        new LinearRingType()
                                .withPosList(
                                        new DirectPositionListType().withValue(coords))));
    }

    private static PolygonType createGmlPolygon(Polygon polygon, String id) {
        AbstractRingPropertyType exteriorRing = abstractRingFromCoordinates(polygon.getExteriorRing().getCoordinates());

        List<AbstractRingPropertyType> interiorRings = new ArrayList<>();
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            interiorRings.add(abstractRingFromCoordinates(polygon.getInteriorRingN(i).getCoordinates()));
        }

        return new PolygonType()
                .withExterior(exteriorRing)
                .withInterior(interiorRings)
                .withId(id);
    }

    @SuppressWarnings("unchecked")
    private static List<TopographicPlace> topographicPlacesFromGml(String inFile) throws IOException, ParserConfigurationException, SAXException {
        FileInputStream inStream = new FileInputStream(inFile);
        GMLConfiguration configuration = new GMLConfiguration();
        Parser parser = new Parser(configuration);
        Object featureCollectionObject = parser.parse(inStream);

        assert featureCollectionObject instanceof FeatureCollection;
        FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection = (FeatureCollection<SimpleFeatureType, SimpleFeature>) featureCollectionObject;

        List<TopographicPlace> topographicPlaces = new ArrayList<>();
        try (FeatureIterator<SimpleFeature> i = featureCollection.features()) {
            while (i.hasNext()) {
                SimpleFeature feature = i.next();
                TopographicPlace place = createTopographicPlace(feature);
                topographicPlaces.add(place);
            }
        }
        return topographicPlaces;
    }

    private static SiteFrame createSiteFrame() {
        return new SiteFrame()
                .withVersion("1")
                .withId("GML:SiteFrame:"+System.currentTimeMillis() / 1000)
                .withCreated(LocalDateTime.now())
                .withFrameDefaults(new VersionFrameDefaultsStructure().withDefaultLocale(
                        new LocaleStructure().withTimeZone("Europe/Helsinki")
                ));
    }

    private static String getNetexId() {
        return String.format("%s:TopographicPlace:%d", CODESPACE, idCounter.getAndIncrement());
    }

    private static String getGmlId() {
        return String.format("%s.Polygon.%d", CODESPACE, idCounter.get());
    }

    private static TopographicPlace createTopographicPlace(SimpleFeature feature) {
        Polygon polygon = (Polygon) feature.getDefaultGeometry();
        String codespace = (String) feature.getAttribute("codespace");
        String areaName = (String) feature.getAttribute("area_name");
        String topographicPlaceId = getNetexId();
        String gmlId = getGmlId();

        KeyValueStructure codespaceKeyValue = new KeyValueStructure()
                .withKey("codespace").withValue(codespace);

        return new TopographicPlace()
                .withId(topographicPlaceId)
                .withKeyList(new KeyListStructure().withKeyValue(codespaceKeyValue))
                .withVersion("1")
                .withName(new MultilingualString().withLang("fi").withValue(areaName))
                .withTopographicPlaceType(TopographicPlaceTypeEnumeration.REGION)
                .withDescription(new MultilingualString().withLang("fi").withValue(areaName))
                .withDescriptor(new TopographicPlaceDescriptor_VersionedChildStructure().withName(new MultilingualString().withLang("fi").withValue(areaName)))
                .withPolygon(createGmlPolygon(polygon, gmlId));
    }

    private static PublicationDeliveryStructure createPublicationDeliveryStructure(SiteFrame siteFrame) {
        return new PublicationDeliveryStructure()
                .withVersion("1")
                .withPublicationTimestamp(LocalDateTime.now())
                .withParticipantRef(Gml2NetexConverter.class.getCanonicalName())
                .withDataObjects(new PublicationDeliveryStructure.DataObjects()
                        .withCompositeFrameOrCommonFrame(
                                List.of(new org.rutebanken.netex.model.ObjectFactory().createSiteFrame(siteFrame))));
    }

    public static void convertGml2TopographicPlacesNetex(String inFile, String outFile) throws IOException, ParserConfigurationException, SAXException, JAXBException {
        SiteFrame siteFrame = createSiteFrame();

        List<TopographicPlace> topographicPlaces = topographicPlacesFromGml(inFile);
        siteFrame.withTopographicPlaces(
                new TopographicPlacesInFrame_RelStructure()
                        .withTopographicPlace(topographicPlaces)
        );

        PublicationDeliveryStructure publicationDeliveryStructure = createPublicationDeliveryStructure(siteFrame);

        FileOutputStream outStream = new FileOutputStream(outFile);

        Marshaller marshaller = JAXBContext.newInstance(StopPlace.class).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setSchema(new NeTExValidator().getSchema());

        marshaller.marshal(new org.rutebanken.netex.model.ObjectFactory().createPublicationDelivery(publicationDeliveryStructure), outStream);
    }
}
