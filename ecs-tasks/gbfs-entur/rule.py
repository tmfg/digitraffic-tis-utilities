import sh
import logging
import os

logger = logging.getLogger()


def run(job, input_dir, output_dir):
    try:
        sh.java("-jar", "validation-gbfs-entur.jar",
                "-i", os.path.realpath(input_dir),
                "-o", os.path.realpath(output_dir),
                _out=os.path.join(output_dir, "stdout.log"),
                _err=os.path.join(output_dir, "stderr.log"))
        return {
            'stdout.log': ['debug'],
            'stderr.log': ['debug'],
            'reports.json': ['report']
        }
    except sh.ErrorReturnCode as e:
        logger.exception("failed to run subprocess")
        return dict()
