ALTER TABLE BRUKER_REGISTRERING ADD TEKSTER_FOR_BESVARELSE VARCHAR(4000);

UPDATE BRUKER_REGISTRERING SET TEKSTER_FOR_BESVARELSE='[]';

ALTER TABLE BRUKER_REGISTRERING MODIFY TEKSTER_FOR_BESVARELSE VARCHAR(4000) NOT NULL;

CREATE OR REPLACE VIEW DVH_BRUKER_REGISTRERING_TEKST AS
  (
  SELECT
    BRUKER_REGISTRERING_ID,
    TEKSTER_FOR_BESVARELSE
  FROM BRUKER_REGISTRERING
);