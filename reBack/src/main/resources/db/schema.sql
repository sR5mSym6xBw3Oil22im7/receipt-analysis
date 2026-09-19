CREATE TABLE IF NOT EXISTS admin (
    "userId" VARCHAR(100) PRIMARY KEY,
    "password" VARCHAR(255) NOT NULL
);

UPDATE admin
SET "password" = 'edix'
WHERE "userId" = 'edix';

INSERT INTO admin ("userId", "password")
SELECT 'edix', 'edix'
WHERE NOT EXISTS (
    SELECT 1 FROM admin WHERE "userId" = 'edix'
);
