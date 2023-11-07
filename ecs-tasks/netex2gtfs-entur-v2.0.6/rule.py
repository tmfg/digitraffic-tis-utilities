import sh
import logging
import os

logger = logging.getLogger()


def run(input_dir, output_dir):
    try:
        sh.java("-jar", "conversion-netex-entur.jar",
                "-i", os.path.realpath(input_dir),
                "-o", os.path.realpath(output_dir))
        return {
            'stdout.log': ['debug'],
            'stderr.log': ['debug'],
            'reports.json': ['report']
        }
    except sh.ErrorReturnCode as e:
        logger.exception("failed to run subprocess")
        return dict()
