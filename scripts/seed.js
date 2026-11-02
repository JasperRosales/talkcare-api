/**
 * Seeds the four role test accounts from docs/schema.md §5 with application-generated bcrypt
 * hashes. Idempotent: accounts whose username or email already exists are skipped.
 * Refuses to run when NODE_ENV=production unless ALLOW_SEED=true.
 */
import bcrypt from 'bcryptjs';
import 'dotenv/config';

if (process.env.NODE_ENV === 'production' && process.env.ALLOW_SEED !== 'true') {
  console.error('Refusing to seed test accounts in production. Set ALLOW_SEED=true to override.');
  process.exit(1);
}

const { pool, withTransaction } = await import('../src/db.js');

const ACCOUNTS = [
  {
    role: 'CLIENT', username: 'test.user', email: 'test.user@talkcare.com',
    password: process.env.SEED_CLIENT_PASSWORD || 'TalkCareUser#2026', fullName: 'TalkCare Test User',
    async subtype(tx, id) {
      await tx.query('INSERT INTO clients (user_id, consent_to_terms_at) VALUES ($1, now())', [id]);
    },
  },
  {
    role: 'ATTORNEY', username: 'test.attorney', email: 'test.attorney@talkcare.com',
    password: process.env.SEED_ATTORNEY_PASSWORD || 'TalkCareCaseHandler#2026', fullName: 'Atty. Sofia Ramos',
    async subtype(tx, id) {
      await tx.query(
        `INSERT INTO professionals (user_id, professional_type, display_title, bio, years_of_experience)
         VALUES ($1, 'ATTORNEY', 'Atty.', 'JusticeCare legal counsel', 8)`, [id]);
      await tx.query(
        `INSERT INTO attorneys (user_id, bar_roll_no, ibp_no, law_firm, jurisdiction)
         VALUES ($1, 'BAR-0001', 'IBP-0001', 'JusticeCare Center', 'Metro Manila')`, [id]);
    },
  },
  {
    role: 'PSYCHIATRIST', username: 'test.psychiatrist', email: 'test.psychiatrist@talkcare.com',
    password: process.env.SEED_PSYCHIATRIST_PASSWORD || 'TalkCareTherapist#2026', fullName: 'Dr. Eleanor Reyes',
    async subtype(tx, id) {
      await tx.query(
        `INSERT INTO professionals (user_id, professional_type, display_title, bio, years_of_experience)
         VALUES ($1, 'PSYCHIATRIST', 'Dr.', 'ThoMatt mental health consultant', 10)`, [id]);
      await tx.query(
        `INSERT INTO psychiatrists (user_id, prc_license_no, clinic_name, accepts_medication_management)
         VALUES ($1, 'PRC-0001', 'ThoMatt Mental Health Clinic', TRUE)`, [id]);
    },
  },
  {
    role: 'ADMIN', username: 'test.admin', email: 'test.admin@talkcare.com',
    password: process.env.SEED_ADMIN_PASSWORD || 'TalkCareAdmin#2026', fullName: 'TalkCare Test Admin',
    async subtype(tx, id) {
      await tx.query(
        `INSERT INTO admins (user_id, job_title, department, is_super_admin)
         VALUES ($1, 'System Administrator', 'Operations', TRUE)`, [id]);
    },
  },
];

try {
  for (const account of ACCOUNTS) {
    const created = await withTransaction(async (tx) => {
      const exists = await tx.query(
        'SELECT 1 FROM users WHERE LOWER(username) = LOWER($1) OR LOWER(email) = LOWER($2)',
        [account.username, account.email]);
      if (exists.rows.length) return false;
      const hash = await bcrypt.hash(account.password, 10);
      const { rows } = await tx.query(
        `INSERT INTO users (role_id, username, email, password_hash, status)
         SELECT role_id, $2, $3, $4, 'VERIFIED' FROM roles WHERE code = $1 RETURNING id`,
        [account.role, account.username, account.email, hash]);
      await tx.query('INSERT INTO user_profiles (user_id, full_name) VALUES ($1, $2)', [rows[0].id, account.fullName]);
      await account.subtype(tx, rows[0].id);
      return true;
    });
    console.log(`${created ? 'Created' : 'Skipped (exists)'} ${account.role.padEnd(12)} ${account.username}`);
  }
} catch (err) {
  console.error('Seeding failed:', err.message);
  process.exitCode = 1;
} finally {
  await pool.end();
}
