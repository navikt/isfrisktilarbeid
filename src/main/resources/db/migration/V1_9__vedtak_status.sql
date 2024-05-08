CREATE TABLE VEDTAK_STATUS (
   id                 SERIAL PRIMARY KEY,
   uuid               CHAR(36)    NOT NULL UNIQUE,
   created_at         timestamptz NOT NULL,
   vedtak_id          INTEGER     NOT NULL REFERENCES VEDTAK (id) ON DELETE CASCADE,
   status             TEXT        NOT NULL,
   veilederident      CHAR(7)     NOT NULL,
   published_at       timestamptz
);

CREATE INDEX IX_VEDTAK_STATUS_VEDTAK_ID on VEDTAK_STATUS (vedtak_id);

ALTER TABLE VEDTAK DROP COLUMN published_at;
ALTER TABLE VEDTAK DROP COLUMN ferdigbehandlet_at;
ALTER TABLE VEDTAK DROP COLUMN veilederident;
ALTER TABLE VEDTAK DROP COLUMN ferdigbehandlet_by;
