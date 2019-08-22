FROM postgres:11
COPY postgressetup.sql /docker-entrypoint-initdb.d/