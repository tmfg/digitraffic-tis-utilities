import sh
import logging
import os

logger = logging.getLogger()


def run(input_dir, output_dir):
    logger.warning(f"Echo rule :: {input_dir} -> {output_dir}")
