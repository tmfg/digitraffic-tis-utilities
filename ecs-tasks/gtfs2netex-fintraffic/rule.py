import sh
import logging
import os
import fnmatch

logger = logging.getLogger()


def run(job, input_dir, output_dir):
    try:
        sh.npm("run", "convert", "--",
               "--gtfs", os.path.realpath(os.path.join(input_dir, "gtfs.zip")),
               "--netex", os.path.realpath(output_dir),
               _out=os.path.join(output_dir, "stdout.log"),
               _err=os.path.join(output_dir, "stderr.log"))
        # add all route XMLs as result
        routes = dict.fromkeys(fnmatch.filter(os.listdir(output_dir), '*_line_*.xml'), ['result'])
        stops = dict.fromkeys(fnmatch.filter(os.listdir(output_dir), '*_all_stops.xml'), ['stops'])
        stats = dict.fromkeys(fnmatch.filter(os.listdir(output_dir), '*_stats.xml'), ['stats'])
        defaults = {
            'stdout.log': ['debug'],
            'stderr.log': ['debug'],
            'report.json': ['report'],
            'report.html': ['report'],
            'errors.json': ['debug']
        }
        return {**routes, **stops, **stats, **defaults}
    except sh.ErrorReturnCode as e:
        logger.exception("failed to run subprocess")
        return dict()
