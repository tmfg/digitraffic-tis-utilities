package fi.digitraffic.tis.rules.validation.netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.entur.netex.validation.validator.ValidationReport;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableReport.class)
@JsonDeserialize(as = ImmutableReport.class)
public interface Report {
    ValidationReport validationReport();
    String entry();
    List<String> errors();
}
