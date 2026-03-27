package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import org.onebusaway.csv_entities.schema.annotations.CsvField;

public class DigiroadIdStopExtension {
    @CsvField(optional = true, name = "digiroad_id")
    private String digiroadId;

    public DigiroadIdStopExtension (String digiroadId) {
        this.digiroadId = digiroadId;
    }

    public String getDigiroadId() {
        return this.digiroadId;
    }

    public void setDigiroadId(String digiroadId) {
        this.digiroadId = digiroadId;
    }
}
