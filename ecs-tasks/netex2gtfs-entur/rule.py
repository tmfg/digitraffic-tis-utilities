import sh
import logging
import os
import json

logger = logging.getLogger()


def run(job, input_dir, output_dir):
    # save configuration to inputs if any available in job
    configuration = job["configuration"]
    if configuration is None:
        configuration = dict()

    with open(os.path.join(input_dir, 'config.json'), 'w') as config_file:
        json.dump({
            'codespace': configuration['codespace'],
            'timetableDataset': os.path.join(input_dir, 'netex.zip'),
            'stopsAndQuaysDataset': os.path.join(input_dir, 'stopsAndQuays.zip')
        }, config_file)

    try:
        sh.java("-jar", "conversion-netex2gtfs-entur.jar",
                "-i", os.path.realpath(input_dir),
                "-o", os.path.realpath(output_dir),
                _out=os.path.join(output_dir, "stdout.log"),
                _err=os.path.join(output_dir, "stderr.log"))
        return {
            'gtfs.zip': ['result'],
            'stdout.log': ['debug'],
            'stderr.log': ['debug'],
            'reports.json': ['report']
        }
    except sh.ErrorReturnCode as e:
        logger.exception("failed to run subprocess")
        return dict()
