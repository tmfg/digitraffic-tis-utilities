#!/bin/bash

# Check if exactly three arguments are provided
if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <input_file> <output_file_base> <namespace>" >&2
    exit 1
fi

INPUT_FILE=$1
OUTPUT_BASE=$2
NAMESPACE=$3
GML_OUTPUT_FILE="${OUTPUT_BASE}.gml"
XML_OUTPUT_FILE="${OUTPUT_BASE}.xml"

# Check if the input file exists in the current directory
if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' not found in the current directory." >&2
    exit 1
fi

# Check if the xslt file exists in the current directory
if [ ! -f "farezone.xslt" ]; then
    echo "Error: 'transform.xslt' not found in the current directory." >&2
    exit 1
fi

# Check if the namespace is given
if [ ! "$NAMESPACE" ]; then
    echo "Error: Parameter <namespace> is not defined" >&2
    exit 1
fi

# Run the docker command with both steps
docker run --rm -v "$(pwd)":/data ghcr.io/osgeo/gdal:alpine-small-3.12.2 sh -c "
    # Step 1: Run ogr2ogr
    ogr2ogr -f GML \
            -nlt MULTIPOLYGON \
            -s_srs EPSG:4326 \
            -t_srs EPSG:4326 \
            '/data/${GML_OUTPUT_FILE}' \
            '/data/${INPUT_FILE}'
"
xsltproc --stringparam namespace $NAMESPACE --stringparam current-timestamp "$(date -u +'%Y-%m-%dT%H:%M:%S')" --stringparam current-epoch "$(date +%s)" farezone.xslt $GML_OUTPUT_FILE > $XML_OUTPUT_FILE

echo "Processing complete. Output written to '${XML_OUTPUT_FILE}'."