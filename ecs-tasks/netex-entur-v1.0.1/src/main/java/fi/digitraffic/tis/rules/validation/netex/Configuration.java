package fi.digitraffic.tis.rules.validation.netex;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableConfiguration.class)
@JsonDeserialize(as = ImmutableConfiguration.class)
public interface Configuration {
    Configuration DEFAULTS = ImmutableConfiguration.builder().codespace("FIN")
        .reportId("NO_REPORT_ID_PROVIDED")
        .addIgnorableNetexElements("SiteFrame")
        .maximumErrors(128)
        .build();
    String codespace();
    String reportId();
    Set<String> ignorableNetexElements();
    int maximumErrors();

}
