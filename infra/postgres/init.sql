-- One database and one user per service. Services never read each other's databases.
-- Runs once, when the postgres-data volume is first created.
-- Development credentials only: user = password = <service>.

CREATE USER auth WITH PASSWORD 'auth';
CREATE DATABASE auth OWNER auth;

CREATE USER profile WITH PASSWORD 'profile';
CREATE DATABASE profile OWNER profile;

CREATE USER account WITH PASSWORD 'account';
CREATE DATABASE account OWNER account;

CREATE USER transaction WITH PASSWORD 'transaction';
CREATE DATABASE transaction OWNER transaction;

CREATE USER notification WITH PASSWORD 'notification';
CREATE DATABASE notification OWNER notification;

-- Lock down the default PUBLIC privileges so one service cannot connect to another's database.
REVOKE CONNECT ON DATABASE auth, profile, account, transaction, notification FROM PUBLIC;
