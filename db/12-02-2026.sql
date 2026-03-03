CREATE TABLE token (
    id SERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    date_heure_expiration TIMESTAMP NOT NULL
);

CREATE INDEX idx_token_value ON token(token);
CREATE INDEX idx_token_expiration ON token(date_heure_expiration);