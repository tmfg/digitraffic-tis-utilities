import sh
import logging
import os

logger = logging.getLogger()


def run(job, input_dir, output_dir):
    try:
        sh.npm("run", "convert", "--",
               "--gtfs", os.path.realpath(os.path.join(input_dir, "gtfs.zip")),
               "--netex", os.path.realpath(output_dir),
               _out=os.path.join(output_dir, "stdout.log"),
               _err=os.path.join(output_dir, "stderr.log"))
        return {
            'stdout.log': ['debug'],
            'stderr.log': ['debug'],
            'report.json': ['report'],
            'report.html': ['report']
        }
    except sh.ErrorReturnCode as e:
        logger.exception("failed to run subprocess")
        return dict()
