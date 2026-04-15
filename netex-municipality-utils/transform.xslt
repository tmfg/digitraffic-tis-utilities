<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ogr="http://ogr.maptools.org/"
                xmlns:gml="http://www.opengis.net/gml/3.2"
                xmlns:netex="http://www.netex.org.uk/netex"
                exclude-result-prefixes="ogr">

    <xsl:output method="xml" indent="yes"/>
    <xsl:param name="current-timestamp"/>
    <xsl:param name="current-epoch"/>

    <!-- Match the root and create the Netex structure -->
    <xsl:template match="/ogr:FeatureCollection">
        <PublicationDelivery xmlns="http://www.netex.org.uk/netex"
                             xmlns:gml="http://www.opengis.net/gml/3.2"
                             xmlns:ns3="http://www.siri.org.uk/siri"
                             version="1">
            <dataObjects>
                <SiteFrame version="1">
                    <xsl:attribute name="created">
                        <xsl:value-of select="$current-timestamp"/>
                    </xsl:attribute>
                    <xsl:attribute name="id">
                        <xsl:value-of select="concat('GML:SiteFrame:', $current-epoch)"/>
                    </xsl:attribute>
                    <FrameDefaults>
                        <DefaultLocale>
                            <TimeZone>Europe/Helsinki</TimeZone>
                        </DefaultLocale>
                    </FrameDefaults>
                    <topographicPlaces>
                        <!-- Apply templates to each feature member -->
                        <xsl:apply-templates select="ogr:featureMember/ogr:*"/>

                        <!-- Copy TopographicPlace from europe-netex.xml -->
                        <xsl:copy-of select="document('europe-netex.xml')//netex:TopographicPlace"/>
                    </topographicPlaces>
                </SiteFrame>
            </dataObjects>
        </PublicationDelivery>
    </xsl:template>

    <!-- Match each municipality and transform it to a TopographicPlace -->
    <xsl:template match="*">
        <TopographicPlace xmlns="http://www.netex.org.uk/netex" xmlns:gml="http://www.opengis.net/gml/3.2" version="1">
            <xsl:attribute name="id">
                <xsl:value-of select="concat('FSR:TopographicPlace:', number(ogr:natcode))"/>
            </xsl:attribute>
            <Name lang="fi"><xsl:value-of select="ogr:namefin"/></Name>
            <Description lang="fi"><xsl:value-of select="ogr:namefin"/></Description>
            <PrivateCode type="natcode"><xsl:value-of select="ogr:natcode"/></PrivateCode>
            <Descriptor>
                <Name lang="fi"><xsl:value-of select="ogr:namefin"/></Name>
            </Descriptor>
            <TopographicPlaceType>municipality</TopographicPlaceType>

            <!-- Copy the geometry -->
            <xsl:copy-of select="ogr:multipolygon/gml:MultiSurface"/>
        </TopographicPlace>
    </xsl:template>
</xsl:stylesheet>
