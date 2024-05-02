ADD TABLE VEDTAK_STATUS (
   id                 SERIAL PRIMARY KEY,
   uuid               CHAR(36)    NOT NULL UNIQUE,
   created_at         timestamptz NOT NULL,
   vedtak_id          INTEGER     NOT NULL UNIQUE REFERENCES VEDTAK (id) ON CASCADE
   status             TEXT        NOT NULL,
   veilederident      CHAR(7)     NOT NULL,
   published_at       timestamptz
);

ALTER TABLE VEDTAK DROP COLUMN published_at;
ALTER TABLE VEDTAK DROP COLUMN ferdigbehandlet_at;
ALTER TABLE VEDTAK DROP COLUMN veilederident;
ALTER TABLE VEDTAK DROP COLUMN ferdigbehandlet_by;
