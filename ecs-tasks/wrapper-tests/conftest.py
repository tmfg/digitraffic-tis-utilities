import os
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'wrapper'))

import app  # noqa: E402,F401

# app points tempfile at /app/tmp on import, which does not exist outside the container
tempfile.tempdir = None
