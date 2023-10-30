import sh
import logging
import os

logger = logging.getLogger()


def run(input_dir, output_dir):
    try:
        sh.java("-jar", "gtfs-validator-cli.jar",
                "-i", os.path.realpath(os.path.join(input_dir, "gtfs.zip")),
                "-o", os.path.realpath(output_dir),
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
