import json
import logging
import os
import tempfile
from logging.config import fileConfig

import boto3
import sh
from botocore.exceptions import ClientError
from uritools import urisplit

fileConfig('logging_config.ini', disable_existing_loggers=False)
logger = logging.getLogger()


# original from https://stackoverflow.com/a/62945526/44523
def download_s3_folder(s3_resource, bucket_name, s3_folder, local_dir):
    """
    Download the contents of a folder directory

    :param s3_resource: boto3 S3 resource reference
    :param bucket_name: the name of the s3 bucket
    :param s3_folder: the folder path in the s3 bucket
    :param local_dir: a relative or absolute directory path in the local file system
    """
    bucket = s3_resource.Bucket(bucket_name)
    for obj in bucket.objects.filter(Prefix=s3_folder):
        target = os.path.join(local_dir, os.path.relpath(obj.key, s3_folder))
        if not os.path.exists(os.path.dirname(target)):
            os.makedirs(os.path.dirname(target))
        if obj.key[-1] == '/':
            continue
        logger.info("Downloading everything to {0} from s3://{1}/{2}".format(target, bucket_name, s3_folder))
        bucket.download_file(obj.key, target)
    return local_dir


def upload_s3_file(s3_client, file_name, bucket, object_name):
    """Upload a file to an S3 bucket

    :param s3_client: boto3 AWS S3 client reference
    :param file_name: File to upload
    :param bucket: Bucket to upload to
    :param object_name: S3 object name. If not specified then file_name is used
    :return: True if file was uploaded, else False
    """

    logger.info("Uploading {0} to s3://{1}/{2}".format(str(file_name), str(bucket), object_name))

    # Upload the file
    try:
        response = s3_client.upload_s3_file(file_name, bucket, object_name)
    except ClientError as e:
        logger.error(e)
        return False
    return True


def process_job(workdir, job):
    s3_input_uri = urisplit(job["inputs"])
    s3_output_uri = urisplit(job["outputs"])
    logger.debug('S3 input URI is ' + str(s3_input_uri))
    logger.debug('S3 output URI is ' + str(s3_output_uri))
    s3_resource = get_aws_resource('s3')
    s3_client = get_aws_client('s3')
    # download inputs
    downloaded_dir = download_s3_folder(s3_resource,
                                        s3_input_uri.authority,
                                        s3_input_uri.path,
                                        local_dir=os.path.join(workdir, "input"))
    output_dir = os.path.join(workdir, "output")
    os.makedirs(output_dir)
    # run command
    try:
        sh.java("-jar", "gtfs-validator-cli.jar",
                "-i", os.path.realpath(os.path.join(downloaded_dir, "gtfs.zip")),
                "-o", os.path.realpath(output_dir))
    except sh.ErrorReturnCode as e:
        logger.exception("failed to run java :(")

    # upload results
    for filename in os.listdir(output_dir):
        full_path = os.path.join(output_dir, filename)
        if os.path.isfile(full_path):
            upload_s3_file(s3_client,
                           full_path,
                           s3_output_uri.authority,
                           s3_output_uri.path + "/" + filename)


def get_aws_resource(resource_name):
    if os.environ.get("LOCALSTACK_ENDPOINT_URL"):
        logger.debug("returning AWS resource '" + resource_name + "' with Localstack configuration")
        return boto3.resource(resource_name, endpoint_url=os.environ.get("LOCALSTACK_ENDPOINT_URL"))
    else:
        logger.debug("returning AWS resource '" + resource_name + "'")
        return boto3.resource(resource_name)


def get_aws_client(resource_name):
    if os.environ.get("LOCALSTACK_ENDPOINT_URL"):
        logger.debug("returning AWS client '" + resource_name + "' with Localstack configuration")
        return boto3.client(resource_name, endpoint_url=os.environ.get("LOCALSTACK_ENDPOINT_URL"))
    else:
        logger.debug("returning AWS resource '" + resource_name + "'")
        return boto3.client(resource_name)


def run_task(workdir, queue_name):
    sqs_resource = get_aws_resource('sqs')
    sqs_client = get_aws_client('sqs')

    job_queue = sqs_resource.get_queue_by_name(QueueName=queue_name)  # TODO: the correct name
    for job_message in job_queue.receive_messages():
        logger.info("Processing message " + str(job_message))
        job = json.loads(job_message.body)
        logger.info("Processing job " + str(job))
        process_job(workdir, job)
        job_message.delete()


def main():
    if os.environ.get("LOCALSTACK_ENDPOINT_URL"):
        # we're in Localstack environment
        logger.info("Process running in Localstack, using " + str(os.environ.get("LOCALSTACK_ENDPOINT_URL")) + " as endpoint URL with AWS profile 'localstack'")
        boto3.setup_default_session(profile_name='localstack')

    with tempfile.TemporaryDirectory() as workdir:
        logger.info('Running task with work directory ' + str(workdir))
        run_task(workdir, 'vaco_rules_gtfs_canonical_v4-1-0')


if __name__ == "__main__":
    main()
