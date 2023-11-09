import logging

logger = logging.getLogger()


def run(job, input_dir, output_dir):
    logger.warning(f"Echo rule :: {job} :: {input_dir} -> {output_dir}")
    return dict()
