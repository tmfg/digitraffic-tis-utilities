<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ogr="http://ogr.maptools.org/"
                xmlns:gml="http://www.opengis.net/gml/3.2"
                xmlns:netex="http://www.netex.org.uk/netex"
                exclude-result-prefixes="ogr">

    <xsl:output method="xml" indent="yes"/>
    <xsl:param name="namespace"/>
    <xsl:param name="current-epoch"/>
    <xsl:param name="current-timestamp"/>

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
                    <tariffZones>
                        <!-- Apply templates to each feature member -->
                        <xsl:apply-templates select="ogr:featureMember/ogr:*"/>
                    </tariffZones>
                </SiteFrame>
            </dataObjects>
        </PublicationDelivery>
    </xsl:template>

    <!-- Match each feature and transform it to a FareZone -->
    <xsl:template match="*">
        <FareZone xmlns="http://www.netex.org.uk/netex" xmlns:gml="http://www.opengis.net/gml/3.2" version="1">
            <xsl:attribute name="id">
                <xsl:value-of select="concat($namespace, ':FareZone:', ogr:Zone)"/>
            </xsl:attribute>
            <Name><xsl:value-of select="concat($namespace, ' ', ogr:Zone)"/></Name>
            <PrivateCode><xsl:value-of select="ogr:Zone"/></PrivateCode>
            <ScopingMethod>implicitSpatialProjection</ScopingMethod>
            <ZoneTopology>tiled</ZoneTopology>
            <AuthorityRef version="1">
                <xsl:attribute name="id">
                    <xsl:value-of select="concat('FSR:Authority:', $namespace)"/>
                </xsl:attribute>
            </AuthorityRef>
            <!-- Copy the geometry -->
            <xsl:copy-of select="*/gml:MultiSurface"/>
        </FareZone>
    </xsl:template>
</xsl:stylesheet>