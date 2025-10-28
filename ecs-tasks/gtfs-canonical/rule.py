import sh
import logging
import os

logger = logging.getLogger()


def run(job, input_dir, output_dir):
    try:
        logger.info(f"Rule run for publicID {str(job["entry"]["publicId"])}")
        converter_result = os.path.realpath(os.path.join(input_dir, "netex2gtfs.entur", "result.zip"))
        if os.path.exists(converter_result):
            input_file = converter_result
        else:
            input_file = os.path.realpath(os.path.join(input_dir, "gtfs.zip"))

        sh.java("-jar", "gtfs-validator-cli.jar",
                "-i", input_file,
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
        logger.warning(f"Failed rule run for publicID {str(job["entry"]["publicId"])}")
        logger.exception("failed to run subprocess")
        return dict()
