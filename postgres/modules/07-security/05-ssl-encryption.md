# SSL & Encryption

## Concept

PostgreSQL protects data at two layers: **in-transit encryption** via TLS (when connections traverse a network) and **at-rest encryption** via column-level encryption using `pgcrypto` (for specific sensitive fields) or filesystem-level encryption for the entire data directory. This lesson covers both layers and the practical patterns StoreForge uses to protect customer payment card data and PII.

---

## SSL/TLS for Connections

```sql
-- postgresql.conf settings:
-- ssl = on
-- ssl_cert_file = '/etc/ssl/certs/postgresql.crt'
-- ssl_key_file  = '/etc/ssl/private/postgresql.key'
-- ssl_ca_file   = '/etc/ssl/certs/ca.crt'      # for client cert auth

-- Check SSL is active:
SHOW ssl;                          -- on

-- Inspect the current connection:
SELECT ssl, version, cipher, bits, client_dn
FROM pg_stat_ssl
WHERE pid = pg_backend_pid();
-- ssl=t, version='TLSv1.3', cipher='TLS_AES_256_GCM_SHA384', bits=256

-- Inspect ALL current connections:
SELECT a.pid, a.usename, a.client_addr, s.ssl, s.version, s.cipher
FROM pg_stat_activity a
JOIN pg_stat_ssl s ON s.pid = a.pid
WHERE a.backend_type = 'client backend';
```

---

## Enforcing SSL via pg_hba.conf

```
# Only accept SSL connections from the application subnet:
hostssl  storeforge_dev  api_service     10.0.1.0/24   scram-sha-256
hostssl  storeforge_dev  admin_rohan     192.168.1.0/24  scram-sha-256

# Reject non-SSL:
hostnossl  all  all  0.0.0.0/0  reject
```

On managed services (RDS, Cloud SQL, Supabase), SSL enforcement is a single configuration toggle rather than an hba edit.

---

## Client Certificate Authentication

The most secure method: requires a signed TLS certificate in addition to (or instead of) a password:

```
# pg_hba.conf: require client cert + password:
hostssl  storeforge_dev  api_service  10.0.1.0/24  cert clientcert=verify-full
```

```bash
# Generate CA and server/client certificates (development):
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 3650 -key ca.key -out ca.crt -subj "/CN=StoreForgeCA"

openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr -subj "/CN=storeforge-db"
openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key \
    -CAcreateserial -out server.crt

# Client certificate for api_service:
openssl genrsa -out api_service.key 2048
openssl req -new -key api_service.key -out api_service.csr -subj "/CN=api_service"
openssl x509 -req -days 365 -in api_service.csr -CA ca.crt -CAkey ca.key \
    -CAcreateserial -out api_service.crt
```

---

## Column-Level Encryption with pgcrypto

For specific high-sensitivity fields (credit card numbers, government IDs), encrypt the value before storing it — the database never sees plaintext:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Store payment method with encrypted card number:
CREATE TABLE payment_method (
    id                  SERIAL PRIMARY KEY,
    customer_id         INTEGER NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    card_type           TEXT    NOT NULL,                -- 'visa', 'mastercard', etc.
    last_four           CHAR(4) NOT NULL,                -- always plaintext for display
    card_number_enc     BYTEA   NOT NULL,                -- AES-encrypted full PAN
    expiry_enc          BYTEA   NOT NULL,                -- AES-encrypted MMYY
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- The encryption key should come from the application or a secret manager
-- (e.g., AWS Secrets Manager, HashiCorp Vault). Never store it in the DB.
-- For demonstration, we use a hardcoded key — NEVER do this in production:
\set ENC_KEY 'supersecretkey32chars!!'

-- Insert encrypted payment method:
INSERT INTO payment_method (customer_id, card_type, last_four, card_number_enc, expiry_enc)
VALUES (
    1,
    'visa',
    '4242',
    pgp_sym_encrypt('4111111111114242', :'ENC_KEY'),
    pgp_sym_encrypt('1228', :'ENC_KEY')
);

-- Decrypt for authorised operations only (inside a SECURITY DEFINER function):
CREATE OR REPLACE FUNCTION get_payment_details(
    p_payment_id INTEGER,
    p_enc_key    TEXT
) RETURNS TABLE(card_type TEXT, last_four CHAR(4), full_pan TEXT, expiry TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    RETURN QUERY
    SELECT
        pm.card_type,
        pm.last_four,
        pgp_sym_decrypt(pm.card_number_enc, p_enc_key),
        pgp_sym_decrypt(pm.expiry_enc, p_enc_key)
    FROM payment_method pm
    WHERE pm.id = p_payment_id;
END;
$$;

-- Only payment processor role should have EXECUTE on this function.
GRANT EXECUTE ON FUNCTION get_payment_details(INTEGER, TEXT) TO storeforge_admin;
```

---

## Hashing vs Encryption

```sql
-- For passwords: use bcrypt (one-way — you can verify but not reverse):
-- (already covered in Module 06 Extensions — pgcrypto)
SELECT crypt('mypassword', gen_salt('bf', 10));

-- For searchable unique values (email, national ID): hash + salt:
-- You cannot search encrypted data without decrypting. Instead, store a
-- salted SHA-256 hash of the email for deduplication WITHOUT storing it plaintext.
-- However, for a general e-commerce app, email plain-storage with TLS is usually sufficient.

-- Symmetric encryption (AES via pgp_sym_encrypt): reversible, use for card data, DOBs.
-- Asymmetric encryption (pgp_pub_encrypt): encrypt with public key, decrypt with private.
-- Only the payment processor holds the private key — even DB admins cannot decrypt.

-- Example: asymmetric encryption (public key only in DB):
-- SELECT pgp_pub_encrypt('4111111111114242', dearmor('-----BEGIN PGP PUBLIC KEY...'));
```

---

## Data Masking for Non-Production Environments

```sql
-- A view that masks sensitive data for use in staging/dev:
CREATE VIEW customer_masked AS
SELECT
    id,
    'Customer_' || id::TEXT AS name,                      -- masked name
    'user' || id::TEXT || '@example.com' AS email,        -- masked email
    LEFT(phone, 3) || '****' || RIGHT(phone, 2) AS phone, -- masked phone
    is_active,
    created_at
FROM customer;

-- Grant dev teams access only to the masked view:
GRANT SELECT ON customer_masked TO storeforge_readonly;
REVOKE SELECT ON customer FROM storeforge_readonly;
```

---

## Try It Yourself

```sql
-- 1. Check the SSL status of your current connection using pg_stat_ssl.

-- 2. Create the payment_method table.
--    Insert one row for customer id 1 with encrypted card number '4111111111114242'
--    and expiry '12/28', using your own encryption key.
--    Verify you can decrypt it with pgp_sym_decrypt.

-- 3. Create the customer_masked view.
--    Query both customer and customer_masked for id = 1.
--    Confirm the masked view hides real name and email.
```

<details>
<summary>Show solutions</summary>

```sql
-- 1. SSL status:
SELECT ssl, version, cipher, bits
FROM pg_stat_ssl
WHERE pid = pg_backend_pid();

-- 2. Encrypted payment method:
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS payment_method (
    id              SERIAL PRIMARY KEY,
    customer_id     INTEGER NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    card_type       TEXT    NOT NULL,
    last_four       CHAR(4) NOT NULL,
    card_number_enc BYTEA   NOT NULL,
    expiry_enc      BYTEA   NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO payment_method (customer_id, card_type, last_four, card_number_enc, expiry_enc)
VALUES (
    1, 'visa', '4242',
    pgp_sym_encrypt('4111111111114242', 'my-dev-key-32chars!!'),
    pgp_sym_encrypt('1228', 'my-dev-key-32chars!!')
);

-- Decrypt and verify:
SELECT
    last_four,
    pgp_sym_decrypt(card_number_enc, 'my-dev-key-32chars!!') AS pan,
    pgp_sym_decrypt(expiry_enc, 'my-dev-key-32chars!!') AS expiry
FROM payment_method WHERE customer_id = 1;

-- 3. Masked view:
CREATE OR REPLACE VIEW customer_masked AS
SELECT
    id,
    'Customer_' || id::TEXT AS name,
    'user' || id::TEXT || '@example.com' AS email,
    LEFT(COALESCE(phone, '000'), 3) || '****' AS phone,
    is_active,
    created_at
FROM customer;

SELECT name, email, phone FROM customer WHERE id = 1;
SELECT name, email, phone FROM customer_masked WHERE id = 1;
```

</details>

---

## Capstone Connection

StoreForge's encryption strategy:

| Data | Method | Key Location |
|---|---|---|
| Customer passwords | bcrypt (pgcrypto) | Not stored — one-way hash |
| Payment card numbers | AES-256 (pgp_sym_encrypt) | Application env / Vault |
| Connection traffic | TLS 1.3 | Server cert from CA |
| Data directory | Filesystem encryption (LUKS/dm-crypt) | Managed by ops team |
| Dev/staging data | Masked views | — |

PCI-DSS compliance for StoreForge requires that full PANs are never stored in plaintext and that all network connections use TLS — this lesson implements both.
