-- Runs once, as SYS, when the container creates its database — for compose.yaml
-- and for DatabaseTestcontainers alike.
--
-- Oracle has no schema apart from a user, so each module's migration creates
-- one (users, notifications) and its tables in it. The application user,
-- which Flyway and the application both connect as, needs these privileges to
-- do that and to read and write the modules' tables. In production a DBA
-- grants the same, or creates the module users up front.
ALTER SESSION SET CONTAINER = FREEPDB1;

GRANT CREATE USER, ALTER USER,
      CREATE ANY TABLE, CREATE ANY INDEX,
      SELECT ANY TABLE, INSERT ANY TABLE, UPDATE ANY TABLE, DELETE ANY TABLE
   TO blueprint;
