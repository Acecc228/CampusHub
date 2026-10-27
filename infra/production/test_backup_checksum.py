"""Filesystem-only recovery test; no database, encryption key or production host."""
import hashlib
import os
import pathlib
import shutil
import subprocess
import unittest
import uuid


class BackupChecksumTests(unittest.TestCase):
    def test_relocated_backup_verifies_and_tamper_is_rejected(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        folder = root / '.local' / 'backup-checksum-tests' / uuid.uuid4().hex
        folder.mkdir(parents=True)
        original = folder / 'original.age'
        copied = folder / 'offhost.age'
        original.write_bytes(b'non-secret encrypted-file stand-in')
        checksum = hashlib.sha256(original.read_bytes()).hexdigest() + '\n'
        shutil.copyfile(original, copied)
        pathlib.Path(str(copied) + '.sha256').write_text(checksum, encoding='ascii')
        # Make original content differ; verification must inspect the supplied copy.
        original.write_bytes(b'original source unavailable or changed')
        bash = 'D:/Git/bin/bash.exe' if os.name == 'nt' else 'bash'
        command = [bash, str(root / 'infra/production/verify-backup.sh'), str(copied)]
        self.assertEqual(subprocess.run(command, capture_output=True).returncode, 0)
        copied.write_bytes(b'tampered')
        self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)


if __name__ == '__main__':
    unittest.main()
