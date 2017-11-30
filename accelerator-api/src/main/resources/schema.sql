--liquibase formatted sql

--changeset jhaber:1
CREATE TABLE IF NOT EXISTS latest_snapshots (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  groupId VARCHAR(64) NOT NULL,
  artifactId VARCHAR(64) NOT NULL,
  baseVersion VARCHAR(128) NOT NULL,
  resolvedVersion VARCHAR(128) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX (groupId, artifactId, baseVersion)
) ENGINE=InnoDB;

-- changeset jhaber:2 dbms:mysql
ALTER TABLE latest_snapshots DEFAULT CHARSET=ascii COLLATE ascii_bin ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE=8
