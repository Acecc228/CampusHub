import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location('operations', pathlib.Path(__file__).with_name('operations.py'))
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


class OperationsTests(unittest.TestCase):
    def test_rejects_development_database_and_account(self):
        for database, account in [('campushub', 'ops@localhost'), ('campushub_test', 'ops@localhost'),
                                  ('campushub_prod', 'campushub_app@localhost')]:
            with self.assertRaises(ValueError):
                ops.validate_target(database, account)
        ops.validate_target('campushub_prod', 'campushub_prod_operator@localhost')

    def test_password_strength_and_spring_hash_shape(self):
        with self.assertRaises(ValueError):
            ops.password_hash('short')
        digest = ops.password_hash('unique long operator password', salt=bytes(range(16)))
        self.assertEqual(len(digest), 96)
        self.assertEqual(digest[:32], bytes(range(16)).hex())
        self.assertEqual(digest, ops.password_hash('unique long operator password', salt=bytes(range(16))))

    def test_provision_transaction_never_accepts_client_sql_or_student(self):
        for login, role in [("a'; DROP TABLE app_user", 'ADMIN'), ('owner', 'STUDENT')]:
            with self.assertRaises(ValueError):
                ops.create_sql(login, 'Name', 'a' * 96, role)
        sql = ops.create_sql('operator_1', "张三'", 'a' * 96, 'ORGANIZER')
        self.assertIn('START TRANSACTION', sql)
        self.assertIn('COMMIT', sql)
        self.assertIn('FALSE', sql)
        self.assertNotIn("张三'", sql)
        self.assertNotIn('ON DUPLICATE', sql)

    def test_disable_and_reset_never_touch_demo_accounts(self):
        self.assertIn('demo_account=FALSE', ops.disable_sql('operator_1'))
        self.assertIn('demo_account=FALSE', ops.reset_sql('operator_1', 'a' * 96))


if __name__ == '__main__':
    unittest.main()
