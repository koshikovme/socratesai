CREATE TABLE IF NOT EXISTS users (
     id BIGSERIAL PRIMARY KEY,
     email VARCHAR(120) NOT NULL UNIQUE,
     password_hash TEXT NOT NULL,
     full_name VARCHAR(120) NOT NULL,
     role VARCHAR(20) NOT NULL,
     university VARCHAR(120),
     group_name VARCHAR(80),
     created_at TIMESTAMP NOT NULL,
     dark_mode BOOLEAN,
     email_notifications BOOLEAN,
     preferred_language VARCHAR(255)
);