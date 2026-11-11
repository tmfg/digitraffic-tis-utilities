import sh
import logging
import os

logger = logging.getLogger()


def run(job, input_dir, output_dir):
    try:
        logger.info(f"Rule run for publicID {str(job["entry"]["publicId"])}")

        input_file = os.path.realpath(os.path.join(input_dir, "ssim.zip"))

        sh.java("-Djava.io.tmpdir=/app/tmp",  # JVM temp dir option
                "-jar", "digitraffic-tis-rules-ssim2netex.jar",
                "-i", input_file,
                "-o", os.path.realpath(output_dir),
                "-a", "validate",
                "-c", "FI",
                _out=os.path.join(output_dir, "stdout.log"),
                _err=os.path.join(output_dir, "stderr.log"))
        return {
            'stdout.log': ['debug'],
            'stderr.log': ['debug'],
            'report.json': ['report'],
            'ssim_processed.json': ['result'],
        }
    except sh.ErrorReturnCode as e:
        logger.warning(f"Failed rule run for publicID {str(job["entry"]["publicId"])}")
        logger.exception("failed to run subprocess")
        return dict()
