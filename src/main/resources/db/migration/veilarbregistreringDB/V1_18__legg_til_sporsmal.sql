ALTER TABLE BRUKER_REGISTRERING add JOBBHISTORIKK varchar(30);

UPDATE BRUKER_REGISTRERING SET JOBBHISTORIKK='-';

ALTER TABLE BRUKER_REGISTRERING MODIFY JOBBHISTORIKK VARCHAR(30) NOT NULL;