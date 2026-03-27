package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import org.entur.netex.gtfs.export.exception.GtfsExportException;
import org.entur.netex.gtfs.export.exception.GtfsSerializationException;
import org.entur.netex.gtfs.export.repository.DefaultGtfsRepository;
import org.onebusaway.csv_entities.exceptions.CsvException;
import org.onebusaway.csv_entities.schema.DefaultEntitySchemaFactory;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.serialization.GtfsEntitySchemaFactory;
import org.onebusaway.gtfs.serialization.GtfsWriter;
import org.onebusaway.gtfs.services.GtfsMutableDao;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class VacoGtfsRepository extends DefaultGtfsRepository {

    private final GtfsMutableDao gtfsDao;

    public VacoGtfsRepository() {
        super();
        this.gtfsDao = new GtfsRelationalDaoImpl();
    }


    @Override
    public void saveEntity(Object entity) {
        gtfsDao.saveEntity(entity);
    }

    @Override
    public InputStream writeGtfs() {
        DefaultEntitySchemaFactory factory = GtfsEntitySchemaFactory.createEntitySchemaFactory();
        factory.addExtension(Stop.class, DigiroadIdStopExtension.class);
        GtfsWriter writer = new GtfsWriter();
        writer.setEntitySchemaFactory(factory);
        try {
            File outputFile = Files.createTempFile("gtfs-export", ".zip").toFile();
            writer.setOutputLocation(outputFile);
            writer.run(this.gtfsDao);
            return Files.newInputStream(outputFile.toPath());
        } catch (CsvException csve) {
            throw new GtfsExportException(
                    "Cannot produce a valid GTFS dataset",
                    csve
            );
        } catch (IOException e) {
            throw new GtfsSerializationException(
                    "Error while saving the GTFS dataset",
                    e
            );
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // LOGGER.warn("Error while closing the GTFS writer", e);
                }
            }
        }
    }
}
