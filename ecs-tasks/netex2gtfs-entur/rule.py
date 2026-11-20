import sh
import logging
import os
import json
import shutil

logger = logging.getLogger()

def run(job, input_dir, output_dir):
    # save configuration to inputs if any available in job
    configuration = job["configuration"]
    if configuration is None:
        configuration = dict()

    stops_only = configuration.get('stopsOnly') is True
    netex_package_path = os.path.join(input_dir, 'netex.zip')
    stop_and_quays_path = os.path.join(input_dir, 'stopsAndQuays.zip')

    ssim_converter_dir = os.path.join(input_dir, "ssim2netex.fintraffic")
    ssim_converter_result = os.path.join(ssim_converter_dir, "result.zip")
    ssim_conversion_result_exists = os.path.exists(ssim_converter_result)

    if ssim_conversion_result_exists:
        shutil.copy(ssim_converter_result, netex_package_path)

    if ssim_conversion_result_exists:
        flat_path = netex_package_path + '.flat'
        with zipfile.ZipFile(netex_package_path) as src, \
                zipfile.ZipFile(flat_path, 'w', zipfile.ZIP_DEFLATED) as dst:
            for info in src.infolist():
                if info.is_dir():
                    continue
                name = info.filename
                if name.startswith('output/'):
                    name = name[len('output/'):]
                dst.writestr(name, src.read(info))
        os.replace(flat_path, netex_package_path)

    timetable_dataset = None if stops_only else netex_package_path
    stops_and_quays_dataset = netex_package_path if stops_only else stop_and_quays_path

    with open(os.path.join(input_dir, 'config.json'), 'w') as config_file:
        json.dump({
            'codespace': configuration['codespace'],
            'stopsOnly': stops_only,
            'timetableDataset': timetable_dataset,
            'stopsAndQuaysDataset': stops_and_quays_dataset
        }, config_file)
    try:
        logger.info(f"Rule run for publicID {str(job["entry"]["publicId"])}")
        sh.java("-Djava.io.tmpdir=/app/tmp",  # JVM temp dir option
                "-jar", "conversion-netex2gtfs-entur.jar",
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
        logger.warning(f"Failed rule run for publicID {str(job["entry"]["publicId"])}")
        logger.exception("failed to run subprocess")
        return dict()
