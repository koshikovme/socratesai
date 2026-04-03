CREATE TABLE IF NOT EXISTS tasks (
     id BIGSERIAL PRIMARY KEY,
     title VARCHAR(180) NOT NULL,
     topic VARCHAR(120) NOT NULL,
     difficulty VARCHAR(20) NOT NULL,
     language VARCHAR(30) NOT NULL,
     description TEXT NOT NULL,
     starter_code TEXT,
     published BOOLEAN NOT NULL,
     created_at TIMESTAMP NOT NULL
);