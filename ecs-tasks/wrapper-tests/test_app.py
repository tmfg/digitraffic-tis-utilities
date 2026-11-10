import logging
import os
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

import app

RULE = 'gtfs2netex.fintraffic'
EPOCH = 1_700_000_000.0


class FakeClock:
    """Replaces the time module in app; only moves when a test or the fake queue moves it."""

    def __init__(self):
        self.now = 0.0

    def time(self):
        return EPOCH + self.now

    def monotonic(self):
        return self.now


class FakeMessage:
    def __init__(self, body, sent_ms=None):
        self.body = body
        self.attributes = {'SentTimestamp': str(sent_ms)} if sent_ms is not None else {}
        self.deleted = False

    def delete(self):
        self.deleted = True


class FakeQueue:
    """SQS queue with scripted arrivals: (arrival time in seconds on the fake clock, message)."""

    def __init__(self, clock, arrivals=()):
        self.clock = clock
        self.arrivals = sorted(arrivals, key=lambda a: a[0])
        self.calls = []

    def receive_messages(self, **kwargs):
        self.calls.append(kwargs)
        assert len(self.calls) < 500, 'receive loop does not stop'
        wait = kwargs.get('WaitTimeSeconds', 0)
        if self.arrivals and self.arrivals[0][0] <= self.clock.now:
            return [self.arrivals.pop(0)[1]]
        if wait and self.arrivals and self.arrivals[0][0] <= self.clock.now + wait:
            arrival, message = self.arrivals.pop(0)
            self.clock.now = arrival
            return [message]
        self.clock.now += wait
        return []

    def waits(self):
        return [c.get('WaitTimeSeconds') for c in self.calls]


def job_message(public_id='entry-1', sent_ms=None):
    body = '{"entry": {"publicId": "%s"}, "task": {"id": 1}}' % public_id
    return FakeMessage(body, sent_ms)


@pytest.fixture
def clock(monkeypatch):
    c = FakeClock()
    monkeypatch.setattr(app, 'time', c)
    return c


@pytest.fixture
def temp_root(tmp_path, monkeypatch):
    root = tmp_path / 'app-tmp'
    root.mkdir()
    monkeypatch.setattr(app, 'TEMP_ROOT', str(root))
    return root


@pytest.fixture
def workdir(temp_root):
    path = temp_root / 'tmpworkdir'
    path.mkdir()
    return path


@pytest.fixture
def start_worker(monkeypatch, clock, workdir):
    """Runs app.run_task against a fake queue and returns the queue."""

    def start(arrivals=()):
        queue = FakeQueue(clock, arrivals)
        sqs_resource = MagicMock()
        sqs_resource.get_queue_by_name.return_value = queue
        monkeypatch.setattr(app, 'get_aws_client', lambda name: MagicMock())
        monkeypatch.setattr(app, 'get_aws_resource', lambda name: sqs_resource if name == 'sqs' else MagicMock())
        return queue

    return start


@pytest.fixture(autouse=True)
def no_idle_timeout(monkeypatch):
    monkeypatch.delenv('IDLE_TIMEOUT_SECONDS', raising=False)


def run(workdir):
    app.run_task(str(workdir), RULE)


# --- idle wait ---------------------------------------------------------------------------------

@pytest.mark.parametrize('value', [None, '0', ''])
def test_exits_on_first_empty_receive_when_idle_timeout_unset_or_zero(monkeypatch, start_worker, clock, workdir, value):
    if value is not None:
        monkeypatch.setenv('IDLE_TIMEOUT_SECONDS', value)
    queue = start_worker()

    run(workdir)

    assert len(queue.calls) == 1
    assert 'WaitTimeSeconds' not in queue.calls[0]
    assert clock.now == 0


@pytest.mark.parametrize('value', ['abc', '5m'])
def test_invalid_idle_timeout_is_logged_and_behaves_as_unset(monkeypatch, caplog, start_worker, clock, workdir, value):
    monkeypatch.setenv('IDLE_TIMEOUT_SECONDS', value)
    queue = start_worker()

    with caplog.at_level(logging.WARNING):
        run(workdir)

    assert len(queue.calls) == 1
    assert 'WaitTimeSeconds' not in queue.calls[0]
    assert clock.now == 0
    assert any('IDLE_TIMEOUT_SECONDS' in r.getMessage() for r in caplog.records if r.levelno >= logging.WARNING)


def test_without_idle_timeout_processes_messages_until_queue_is_empty(monkeypatch, start_worker, workdir):
    jobs = []
    monkeypatch.setattr(app, 'process_job', lambda rule_name, aws, wd, job: jobs.append(job['entry']['publicId']))
    first, second = job_message('a'), job_message('b')
    queue = start_worker([(0, first), (0, second)])

    run(workdir)

    assert jobs == ['a', 'b']
    assert first.deleted and second.deleted
    assert len(queue.calls) == 3


def test_waits_for_idle_timeout_with_long_polling_and_exits(monkeypatch, start_worker, clock, workdir):
    monkeypatch.setenv('IDLE_TIMEOUT_SECONDS', '60')
    queue = start_worker()

    run(workdir)

    assert queue.waits() == [20, 20, 20]
    assert clock.now == 60


def test_long_poll_never_waits_longer_than_remaining_idle_time(monkeypatch, start_worker, clock, workdir):
    monkeypatch.setenv('IDLE_TIMEOUT_SECONDS', '25')
    queue = start_worker()

    run(workdir)

    assert queue.waits() == [20, 5]
    assert clock.now == 25


def test_takes_message_that_arrives_during_idle_wait(monkeypatch, start_worker, clock, workdir):
    monkeypatch.setenv('IDLE_TIMEOUT_SECONDS', '60')
    jobs = []
    monkeypatch.setattr(app, 'process_job', lambda rule_name, aws, wd, job: jobs.append(job['entry']['publicId']))
    message = job_message('late')
    start_worker([(30, message)])

    run(workdir)

    assert jobs == ['late']
    assert message.deleted
    assert clock.now == 90  # message at 30, then a full idle time with nothing


def test_idle_timer_restarts_after_each_job(monkeypatch, start_worker, clock, workdir):
    monkeypatch.setenv('IDLE_TIMEOUT_SECONDS', '60')
    jobs = []

    def slow_job(rule_name, aws, wd, job):
        jobs.append(job['entry']['publicId'])
        if job['entry']['publicId'] == 'first':
            clock.now += 50

    monkeypatch.setattr(app, 'process_job', slow_job)
    # the first job ends at 60; the second message is 40 s later, past the 60 s counted from the start
    start_worker([(10, job_message('first')), (100, job_message('second'))])

    run(workdir)

    assert jobs == ['first', 'second']
    assert clock.now == 160


# --- cleanup -----------------------------------------------------------------------------------

def leave_files(temp_root, job_workdir):
    """What a job and its rule can leave behind under the temp root."""
    os.makedirs(os.path.join(job_workdir, 'output'), exist_ok=True)
    with open(os.path.join(job_workdir, 'output', 'result.xml'), 'w') as f:
        f.write('x')
    # gtfs2netex: TMPDIR=/app/tmp, Node writes temp dirs and files directly under it
    (temp_root / 'node-compile-cache').mkdir()
    (temp_root / 'node-compile-cache' / 'cache.bin').write_text('x')
    # Java: -Djava.io.tmpdir=/app/tmp
    (temp_root / 'hsperfdata_root').mkdir()
    (temp_root / 'libnetty-12345.so').write_text('x')


def test_removes_all_job_files_under_temp_root_after_each_job(monkeypatch, start_worker, workdir, temp_root):
    seen_at_start = []

    def job(rule_name, aws, job_workdir, message):
        seen_at_start.append(sorted(os.listdir(temp_root)))
        leave_files(temp_root, job_workdir)

    monkeypatch.setattr(app, 'process_job', job)
    start_worker([(0, job_message('a')), (0, job_message('b'))])

    run(workdir)

    # the second job starts with nothing left over from the first
    assert seen_at_start == [['tmpworkdir'], ['tmpworkdir']]
    # the run workdir itself belongs to the caller and stays, but it is empty
    assert os.listdir(temp_root) == ['tmpworkdir']
    assert os.listdir(workdir) == []


def test_removes_job_files_when_job_fails(monkeypatch, start_worker, workdir, temp_root):
    def failing_job(rule_name, aws, job_workdir, message):
        leave_files(temp_root, job_workdir)
        raise RuntimeError('boom')

    monkeypatch.setattr(app, 'process_job', failing_job)
    start_worker([(0, job_message())])

    with pytest.raises(RuntimeError):
        run(workdir)

    assert os.listdir(temp_root) == ['tmpworkdir']
    assert os.listdir(workdir) == []


def test_removes_symlinks_under_temp_root_without_touching_their_targets(
        monkeypatch, tmp_path, start_worker, workdir, temp_root):
    outside_dir = tmp_path / 'outside-dir'
    outside_dir.mkdir()
    (outside_dir / 'keep.txt').write_text('x')
    outside_file = tmp_path / 'outside-file.txt'
    outside_file.write_text('x')

    def job(rule_name, aws, job_workdir, message):
        os.symlink(outside_dir, temp_root / 'link-to-dir')
        os.symlink(outside_file, temp_root / 'link-to-file')
        os.symlink(outside_dir, workdir / 'link-in-workdir')

    monkeypatch.setattr(app, 'process_job', job)
    start_worker([(0, job_message())])

    run(workdir)

    assert os.listdir(temp_root) == ['tmpworkdir']
    assert os.listdir(workdir) == []
    assert (outside_dir / 'keep.txt').read_text() == 'x'
    assert outside_file.read_text() == 'x'


def test_failing_removal_is_logged_and_job_still_completes(monkeypatch, caplog, start_worker, workdir, temp_root):
    def job(rule_name, aws, job_workdir, message):
        leave_files(temp_root, job_workdir)

    monkeypatch.setattr(app, 'process_job', job)
    original_unlink = os.unlink

    def failing_unlink(path, *args, **kwargs):
        if os.path.basename(path) == 'libnetty-12345.so':
            raise PermissionError('denied')
        return original_unlink(path, *args, **kwargs)

    monkeypatch.setattr(os, 'unlink', failing_unlink)
    message = job_message()
    start_worker([(0, message)])

    with caplog.at_level(logging.WARNING):
        run(workdir)

    assert message.deleted
    assert any('libnetty-12345.so' in r.getMessage() for r in caplog.records if r.levelno >= logging.WARNING)
    # the other entries are still removed
    assert sorted(os.listdir(temp_root)) == ['libnetty-12345.so', 'tmpworkdir']


def test_failing_scan_of_temp_root_does_not_replace_job_exception(
        monkeypatch, caplog, start_worker, workdir, temp_root):
    def failing_job(rule_name, aws, job_workdir, message):
        raise RuntimeError('rule crashed')

    monkeypatch.setattr(app, 'process_job', failing_job)
    original_scandir = os.scandir

    def failing_scandir(path='.'):
        if str(path) == str(temp_root):
            raise PermissionError('denied')
        return original_scandir(path)

    monkeypatch.setattr(os, 'scandir', failing_scandir)
    message = job_message()
    start_worker([(0, message)])

    with caplog.at_level(logging.WARNING):
        with pytest.raises(RuntimeError, match='rule crashed'):
            run(workdir)

    assert not message.deleted
    assert any(str(temp_root) in r.getMessage() for r in caplog.records if r.levelno >= logging.WARNING)


# --- queue wait log ----------------------------------------------------------------------------

def test_receive_requests_sent_timestamp_attribute(monkeypatch, start_worker, workdir):
    monkeypatch.setenv('IDLE_TIMEOUT_SECONDS', '20')
    monkeypatch.setattr(app, 'process_job', lambda *args: None)
    queue = start_worker([(0, job_message())])

    run(workdir)

    assert len(queue.calls) >= 2
    assert all(c['AttributeNames'] == ['SentTimestamp'] for c in queue.calls)


def test_logs_queue_wait_per_message_in_aggregable_form(monkeypatch, caplog, clock, start_worker, workdir):
    monkeypatch.setattr(app, 'process_job', lambda *args: None)
    clock.now = 100
    sent_ms = int((EPOCH + 100 - 42.5) * 1000)
    start_worker([(100, job_message('entry-7', sent_ms))])

    with caplog.at_level(logging.INFO):
        run(workdir)

    lines = [r.getMessage() for r in caplog.records if r.getMessage().startswith('queue_wait ')]
    assert lines == [f'queue_wait rule={RULE} entryId=entry-7 wait_ms=42500']


def test_job_runs_when_message_has_no_sent_timestamp(monkeypatch, start_worker, workdir):
    jobs = []
    monkeypatch.setattr(app, 'process_job', lambda rule_name, aws, wd, job: jobs.append(job))
    message = job_message()
    start_worker([(0, message)])

    run(workdir)

    assert len(jobs) == 1
    assert message.deleted


# --- failure behavior --------------------------------------------------------------------------

def test_uncaught_rule_exception_exits_and_leaves_message_undeleted(monkeypatch, start_worker, workdir):
    def failing_job(rule_name, aws, job_workdir, message):
        raise RuntimeError('rule crashed')

    monkeypatch.setattr(app, 'process_job', failing_job)
    message = job_message()
    queue = start_worker([(0, message)])

    with pytest.raises(RuntimeError, match='rule crashed'):
        run(workdir)

    assert not message.deleted
    assert len(queue.calls) == 1


def test_uncaught_exception_from_real_process_job_leaves_message_undeleted(monkeypatch, start_worker, workdir):
    monkeypatch.setattr(app, 'download_s3_folder', lambda *args, **kwargs: None)
    monkeypatch.setattr(app, 'rule', SimpleNamespace(run=MagicMock(side_effect=ValueError('bad input'))))
    message = FakeMessage('{"entry": {"publicId": "e"}, "task": {"id": 1}, "inputs": "s3://b/in", "outputs": "s3://b/out"}')
    start_worker([(0, message)])

    with pytest.raises(ValueError, match='bad input'):
        run(workdir)

    assert not message.deleted
