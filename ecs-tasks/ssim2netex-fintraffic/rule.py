import sh
import logging
import os
import fnmatch

logger = logging.getLogger()


def run(job, input_dir, output_dir):
    try:
        logger.info(f"Rule run for publicID {str(job["entry"]["publicId"])}")
        converter_result = os.path.realpath(os.path.join(input_dir, "ssim.fintraffic", "result.zip"))

        if os.path.exists(converter_result):
            input_file = converter_result
        else:
            input_file = os.path.realpath(os.path.join(input_dir, "ssim.zip"))

        sh.java("-Djava.io.tmpdir=/app/tmp",  # JVM temp dir option
                "-jar", "digitraffic-tis-rules-ssim2netex.jar",
                "-i", input_file,
                "-o", os.path.realpath(output_dir),
                "-a", "convert2netex",
                "-c", "FI",
                _out=os.path.join(output_dir, "stdout.log"),
                _err=os.path.join(output_dir, "stderr.log"))

        results = dict.fromkeys(fnmatch.filter(os.listdir(output_dir), '*.xml'), ['result'])
        defaults = {
                   'stdout.log': ['debug'],
                   'stderr.log': ['debug'],
                }
        return {**results, **defaults}
    except sh.ErrorReturnCode as e:
        logger.warning(f"Failed rule run for publicID {str(job["entry"]["publicId"])}")
        logger.exception("failed to run subprocess")
        return dict()
