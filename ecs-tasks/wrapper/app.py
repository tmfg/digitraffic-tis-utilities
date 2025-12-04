import argparse
import json
import logging
import os
import tempfile
import time
from logging.config import fileConfig

import boto3
from botocore.exceptions import ClientError
from uritools import urisplit, uriunsplit, urijoin

# NOTE: this rule script will be overwritten in Docker containers! The default is just an echo function.
import rule

fileConfig('logging_config.ini', disable_existing_loggers=False)
logger = logging.getLogger()
tempfile.tempdir = '/app/tmp'


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
    prefix = str(s3_folder).removeprefix('/')
    for obj in bucket.objects.filter(Prefix=prefix):
        target = os.path.join(local_dir, os.path.relpath(obj.key, prefix))
        if not os.path.exists(os.path.dirname(target)):
            logger.debug(f"creating path {os.path.dirname(target)}")
            os.makedirs(os.path.dirname(target))
        if obj.key[-1] == '/':
            continue
        logger.info(f"Downloading file to {target} from s3://{bucket_name}/{obj.key}")
        with open(target, 'wb') as data:
            bucket.download_fileobj(obj.key, data)
    return local_dir


def upload_s3_file(s3_client, file_name, bucket, object_name):
    """Upload a file to an S3 bucket

    :param s3_client: boto3 AWS S3 client reference
    :param file_name: File to upload
    :param bucket: Bucket to upload to
    :param object_name: S3 object name. If not specified then file_name is used
    :return: S3 URI of uploaded file, else False
    """

    logger.info(f"Uploading {str(file_name)} to s3://{str(bucket)}/{object_name}")

    # Upload the file
    try:
        response = s3_client.upload_file(file_name, bucket, object_name)
    except ClientError as e:
        logger.error(e)
        return False
    return True


def process_job(rule_name, aws, workdir, job):
    logger.info(f"process_job")
    s3_input_uri = urisplit(job["inputs"])
    s3_output_uri = urisplit(job["outputs"])
    logger.debug('S3 input URI is ' + str(s3_input_uri))
    logger.debug('S3 output URI is ' + str(s3_output_uri))
    s3_client = aws['s3']['client']
    s3_resource = aws['s3']['resource']

    # prepare local paths
    input_dir = os.path.join(workdir, "input")
    output_dir = os.path.join(workdir, "output")
    os.makedirs(output_dir)
    # download inputs
    download_s3_folder(s3_resource,
                       s3_input_uri.authority,
                       s3_input_uri.path,
                       local_dir=input_dir)
    # run command
    outputs_meta = rule.run(job, input_dir, output_dir)
    logger.info(f"Rule produced for publicID {str(job["entry"]["publicId"])} :> {outputs_meta}")
    # map of uploaded result files with list of package scopes
    uploaded_files = {}
    for filename in os.listdir(output_dir):
        # add packages metadata to produced files, default to nothing
        packages = list(outputs_meta.get(filename, list()))
        # add all produced files to 'all' package
        packages.extend(['all'])
        # resolve full path of the produced file for uploading to S3
        full_path = os.path.join(output_dir, filename)
        if not os.path.isfile(full_path):
            continue
        if not (upload_s3_file(s3_client,
                               full_path,
                               s3_output_uri.authority,
                               s3_output_uri.path.removeprefix('/') + "/" + filename)):
            logger.warning(f"Failed to upload file for publicID: {str(job["entry"]["publicId"])} :> {full_path} to {s3_output_uri}")
            continue
        logger.info(f"{filename} -> {full_path}")
        # the /output/ is dropped here despite seemingly correct way of appending, should investigate+fix
        uploaded_files[urijoin(uriunsplit(s3_output_uri), f"output/{filename}")] = packages

    # ^-- list of Notices
    logger.info(f"uploaded_files for publicID {str(job["entry"]["publicId"])} :> {uploaded_files}")
    result_message = {
        'entryId': job['entry']['publicId'],        # note the use of publicId instead of internal id
        'taskId': job['task']['id'],                # TODO: should have publicId for tasks as well
        'ruleName': rule_name,
        'inputs': uriunsplit(s3_input_uri),
        'outputs': uriunsplit(s3_output_uri),
        'uploadedFiles': uploaded_files
    }
    sqs_resource = aws['sqs']['resource']
    results_queue = sqs_resource.get_queue_by_name(QueueName='rules-results')
    results_queue.send_message(MessageBody=json.dumps(result_message))


def get_aws_resource(resource_name):
    if os.environ.get("LOCALSTACK_ENDPOINT_URL"):
        logger.debug(f"returning AWS resource '{resource_name}' with Localstack configuration")
        return boto3.resource(resource_name, endpoint_url=os.environ.get("LOCALSTACK_ENDPOINT_URL"))
    else:
        logger.debug(f"returning AWS resource '{resource_name}'")
        return boto3.resource(resource_name)


def get_aws_client(resource_name):
    if os.environ.get("LOCALSTACK_ENDPOINT_URL"):
        logger.debug(f"returning AWS client '{resource_name}' with Localstack configuration")
        return boto3.client(resource_name, endpoint_url=os.environ.get("LOCALSTACK_ENDPOINT_URL"))
    else:
        logger.debug(f"returning AWS client '{resource_name}'")
        return boto3.client(resource_name)


def munge(rule_name):
    return f'rules-processing-{rule_name.replace(".", "-")}'.lower()


def run_task(workdir, rule_name):
    aws = {
        's3': {
            'client': get_aws_client('s3'),
            'resource': get_aws_resource('s3')
        },
        'sqs': {
            'client': get_aws_client('sqs'),
            'resource': get_aws_resource('sqs')
        },
    }

    job_queue = aws['sqs']['resource'].get_queue_by_name(QueueName=munge(rule_name))  # TODO: the correct name
    logger.info(f"Run task for jobQueue: {str(job_queue)} ")
    contains_messages = True
    while contains_messages:
        messages = job_queue.receive_messages(MaxNumberOfMessages=1)
        contains_messages = len(messages) > 0
        for job_message in messages:
            logger.info(f"Processing message {str(job_message)}")
            job = json.loads(job_message.body)
            logger.info(f"Processing job for publicID: {str(job["entry"]["publicId"])} :> {str(job)}")
            process_job(rule_name, aws, os.path.join(workdir, job["entry"]["publicId"], str(int(time.time()))), job)
            job_message.delete()


def main(rule_name):
    if os.environ.get("LOCALSTACK_ENDPOINT_URL"):
        # we're in Localstack environment
        logger.info(f"Process running in Localstack, using {os.environ.get('LOCALSTACK_ENDPOINT_URL')} as endpoint URL with hardcoded AWS dummy credentials")
        boto3.setup_default_session(aws_access_key_id='localstack', aws_secret_access_key='localstack', region_name='eu-north-1')

    with tempfile.TemporaryDirectory() as workdir:
        logger.info(f"Running task with work directory {workdir}")
        run_task(workdir, rule_name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('-r', '--rule-name', action='store')
    args = parser.parse_args()
    main(args.rule_name)
