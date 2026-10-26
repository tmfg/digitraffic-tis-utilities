#!/bin/bash

# Check if exactly two arguments are provided
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <input_file> <output_file_base>" >&2
    exit 1
fi

INPUT_FILE=$1
OUTPUT_BASE=$2
GML_OUTPUT_FILE="${OUTPUT_BASE}.gml"
XML_OUTPUT_FILE="${OUTPUT_BASE}.xml"

# Check if the input file exists in the current directory
if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' not found in the current directory." >&2
    exit 1
fi

# Check if the xslt file exists in the current directory
if [ ! -f "transform.xslt" ]; then
    echo "Error: 'transform.xslt' not found in the current directory." >&2
    exit 1
fi

# Run the docker command with both steps
docker run --rm -v "$(pwd)":/data ghcr.io/osgeo/gdal:alpine-small-3.12.2 sh -c "
    # Step 1: Run ogr2ogr
    ogr2ogr -f GML \
            -s_srs EPSG:3067 \
            -t_srs EPSG:4326 \
            '/data/${GML_OUTPUT_FILE}' \
            '/data/${INPUT_FILE}' \
            Kunta
"
xsltproc --stringparam current-timestamp "$(date -u +'%Y-%m-%dT%H:%M:%S.%3Z')" --stringparam current-epoch "$(date +%s)" transform.xslt $GML_OUTPUT_FILE > $XML_OUTPUT_FILE

echo "Processing complete. Output written to '${XML_OUTPUT_FILE}'."
