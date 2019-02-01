--
-- (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
--
-- This file is part of the Kitodo project.
--
-- It is licensed under GNU General Public License version 3 or later.
--
-- For the full copyright and license information, please read the
-- GPL3-License.txt file that was distributed with this source code.
--

--
-- Migration: Add columns table

-- 1. Create listColumn table
CREATE TABLE listColumn (
  id INT(11) NOT NULL AUTO_INCREMENT,
  title VARCHAR (255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET = utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. Add standard columns
--- projects page
---- project columns
INSERT INTO listColumn (title) VALUES ('project.title');
INSERT INTO listColumn (title) VALUES ('project.mets_rights_owner');
INSERT INTO listColumn (title) VALUES ('project.active');
---- template columns
INSERT INTO listColumn (title) VALUES ('template.title');
INSERT INTO listColumn (title) VALUES ('template.active');
---- workflow columns
INSERT INTO listColumn (title) VALUES ('workflow.title');
INSERT INTO listColumn (title) VALUES ('workflow.filename');
INSERT INTO listColumn (title) VALUES ('workflow.active');
INSERT INTO listColumn (title) VALUES ('workflow.ready');
---- docket columns
INSERT INTO listColumn (title) VALUES ('docket.title');
INSERT INTO listColumn (title) VALUES ('docket.filename');
---- ruleset columns
INSERT INTO listColumn (title) VALUES ('ruleset.title');
INSERT INTO listColumn (title) VALUES ('ruleset.filename');
INSERT INTO listColumn (title) VALUES ('ruleset.sorting');

--- tasks page
---- task columns
INSERT INTO listColumn (title) VALUES ('task.title');
INSERT INTO listColumn (title) VALUES ('task.process');
INSERT INTO listColumn (title) VALUES ('task.project');
INSERT INTO listColumn (title) VALUES ('task.status');

--- processes pages
---- process columns
INSERT INTO listColumn (title) VALUES ('process.title');
INSERT INTO listColumn (title) VALUES ('process.status');
INSERT INTO listColumn (title) VALUES ('process.project');

--- user page
---- user columns
INSERT INTO listColumn (title) VALUES ('user.username');
INSERT INTO listColumn (title) VALUES ('user.location');
INSERT INTO listColumn (title) VALUES ('user.roles');
INSERT INTO listColumn (title) VALUES ('user.clients');
INSERT INTO listColumn (title) VALUES ('user.projects');
INSERT INTO listColumn (title) VALUES ('user.active');
---- role columns
INSERT INTO listColumn (title) VALUES ('role.role');
INSERT INTO listColumn (title) VALUES ('role.client');
---- client columns
INSERT INTO listColumn (title) VALUES ('client.client');
---- ldap columns
INSERT INTO listColumn (title) VALUES ('ldapgroup.ldapgroup');
INSERT INTO listColumn (title) VALUES ('ldapgroup.home_directory');
INSERT INTO listColumn (title) VALUES ('ldapgroup.gidNumber');

-- 3. Create client_x_listcolumn table
CREATE TABLE client_x_listColumn (
  client_id INT(11) NOT NULL,
  column_id INT(11) NOT NULL,
  PRIMARY KEY ( client_id, column_id ),
  KEY FK_client_id (client_id),
  KEY FK_column_id (column_id),
  CONSTRAINT FK_client_id FOREIGN KEY (client_id) REFERENCES client(id),
  CONSTRAINT FK_column_id FOREIGN KEY (column_id) REFERENCES listColumn(id)
);

-- 4. Add standard mappings
INSERT IGNORE INTO client_x_listColumn (client_id, column_id)
  VALUES ((SELECT id FROM client), (SELECT id FROM listColumn));
