CREATE TABLE PDF (
    id         SERIAL PRIMARY KEY,
    uuid       CHAR(36)    NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    pdf        bytea       NOT NULL
);

CREATE TABLE VEDTAK (
    id                 SERIAL PRIMARY KEY,
    uuid               CHAR(36)    NOT NULL UNIQUE,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    personident        VARCHAR(11) NOT NULL,
    veilederident      VARCHAR(7)  NOT NULL,
    fom                DATE        NOT NULL,
    tom                DATE        NOT NULL,
    begrunnelse        TEXT,
    document           JSONB       NOT NULL DEFAULT '[]'::jsonb,
    journalpost_id     VARCHAR(20),
    pdf_id             INTEGER     NOT NULL UNIQUE REFERENCES PDF (id) ON DELETE RESTRICT
);

CREATE INDEX IX_VEDTAK_PERSONIDENT on VEDTAK (personident);

CREATE TABLE BEHANDLER_MELDING (
    id                 SERIAL PRIMARY KEY,
    uuid               CHAR(36)    NOT NULL UNIQUE,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    behandler_ref      CHAR(36)    NOT NULL,
    document           JSONB       NOT NULL DEFAULT '[]'::jsonb,
    journalpost_id     VARCHAR(20),
    vedtak_id          INTEGER     NOT NULL REFERENCES VEDTAK (id) ON DELETE CASCADE,
    pdf_id             INTEGER     NOT NULL UNIQUE REFERENCES PDF (id) ON DELETE RESTRICT
);

CREATE INDEX IX_BEHANDLER_MELDING_VEDTAK_ID on BEHANDLER_MELDING (vedtak_id);
