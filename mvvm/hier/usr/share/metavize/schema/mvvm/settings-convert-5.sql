-- settings convert for release-3.1

---------------
-- new tables |
---------------

-- com.metavize.mvvm.logging.LoggingSettings
CREATE TABLE settings.logging_settings (
    settings_id int8 NOT NULL,
    syslog_enabled bool NOT NULL,
    syslog_host text,
    syslog_port int4,
    syslog_facility int4,
    syslog_threshold int4,
    PRIMARY KEY (settings_id));

-- com.metavize.mvvm.snmp.SnmpSettings
CREATE TABLE settings.snmp_settings (
    snmp_settings_id int8 NOT NULL,
    enabled bool,
    port int4,
    com_str text,
    sys_contact text,
    sys_location text,
    send_traps bool,
    trap_host text,
    trap_com text,
    trap_port int4,
    PRIMARY KEY (snmp_settings_id));

---------------
-- old tables |
---------------

DROP TABLE settings.rule;
DROP TABLE settings.uri_rule;

------------------------
-- elimintate varchars |
------------------------

-- com.metavize.mvvm.security.User
DROP TABLE settings.mvvm_tmp;

CREATE TABLE settings.mvvm_tmp AS
    SELECT id, login::text, password, name::text, notes::text, send_alerts,
           admin_setting_id
    FROM settings.mvvm_user;

DROP TABLE settings.mvvm_user;
ALTER TABLE settings.mvvm_tmp RENAME TO mvvm_user;
ALTER TABLE settings.mvvm_user ALTER COLUMN id SET NOT NULL;
ALTER TABLE settings.mvvm_user ALTER COLUMN login SET NOT NULL;
ALTER TABLE settings.mvvm_user ALTER COLUMN password SET NOT NULL;
ALTER TABLE settings.mvvm_user ALTER COLUMN name SET NOT NULL;
ALTER TABLE settings.mvvm_user ADD PRIMARY KEY (id);

-- com.metavize.mvvm.MailSettings
DROP TABLE settings.mvvm_tmp;

CREATE TABLE settings.mvvm_tmp AS
    SELECT mail_settings_id, report_email::text, smtp_host::text,
           from_address::text, smtp_port, use_tls, auth_user::text,
           auth_pass::text, local_host_name::text
    FROM settings.mail_settings;

DROP TABLE settings.mail_settings;
ALTER TABLE settings.mvvm_tmp RENAME TO mail_settings;
ALTER TABLE settings.mail_settings ALTER COLUMN mail_settings_id SET NOT NULL;
ALTER TABLE settings.mail_settings ALTER COLUMN smtp_port SET NOT NULL;
ALTER TABLE settings.mail_settings ALTER COLUMN use_tls SET NOT NULL;
ALTER TABLE settings.mail_settings ADD PRIMARY KEY (mail_settings_id);

-- com.metavize.mvvm.policy.Policy
DROP TABLE settings.mvvm_tmp;

CREATE TABLE settings.mvvm_tmp AS
    SELECT id, is_default, name::text, notes::text
    FROM settings.policy;

DROP TABLE settings.policy CASCADE;
ALTER TABLE settings.mvvm_tmp RENAME TO policy;
ALTER TABLE settings.policy ALTER COLUMN id SET NOT NULL;
ALTER TABLE settings.policy ALTER COLUMN is_default SET NOT NULL;
ALTER TABLE settings.policy ALTER COLUMN name SET NOT NULL;
ALTER TABLE settings.policy ADD PRIMARY KEY (id);

-- com.metavize.mvvm.policy.UserPolicyRule
DROP TABLE settings.mvvm_tmp;

CREATE TABLE settings.mvvm_tmp AS
    SELECT rule_id, protocol_matcher::text, client_ip_matcher::text,
           server_ip_matcher::text, client_port_matcher::text,
           server_port_matcher::text, client_intf, server_intf, policy_id,
           is_inbound, name::text, category::text, description::text, live,
           alert, log, set_id, position
    FROM settings.user_policy_rule;

DROP TABLE settings.user_policy_rule;
ALTER TABLE settings.mvvm_tmp RENAME TO user_policy_rule;
ALTER TABLE settings.user_policy_rule ALTER COLUMN rule_id SET NOT NULL;
ALTER TABLE settings.user_policy_rule ALTER COLUMN client_intf SET NOT NULL;
ALTER TABLE settings.user_policy_rule ALTER COLUMN server_intf SET NOT NULL;
ALTER TABLE settings.user_policy_rule ALTER COLUMN is_inbound SET NOT NULL;
ALTER TABLE settings.user_policy_rule ADD PRIMARY KEY (rule_id);

-- com.metavize.mvvm.policy.SystemPolicyRule
DROP TABLE settings.mvvm_tmp;

CREATE TABLE settings.mvvm_tmp AS
    SELECT rule_id, client_intf, server_intf, policy_id, is_inbound, name,
           category, description, live, alert, log
    FROM settings.system_policy_rule;

DROP TABLE settings.system_policy_rule;
ALTER TABLE settings.mvvm_tmp RENAME TO system_policy_rule;
ALTER TABLE settings.system_policy_rule ALTER COLUMN rule_id SET NOT NULL;
ALTER TABLE settings.system_policy_rule ALTER COLUMN client_intf SET NOT NULL;
ALTER TABLE settings.system_policy_rule ALTER COLUMN server_intf SET NOT NULL;
ALTER TABLE settings.system_policy_rule ALTER COLUMN is_inbound SET NOT NULL;
ALTER TABLE settings.system_policy_rule ADD PRIMARY KEY (rule_id);

-- com.metavize.mvvm.engine.TransformPersistentState.args
DROP TABLE settings.mvvm_tmp;

CREATE TABLE settings.mvvm_tmp AS
    SELECT tps_id, arg::text, position
    FROM settings.transform_args;

DROP TABLE settings.transform_args;
ALTER TABLE settings.mvvm_tmp RENAME TO transform_args;
ALTER TABLE settings.transform_args ALTER COLUMN tps_id SET NOT NULL;
ALTER TABLE settings.transform_args ALTER COLUMN arg SET NOT NULL;
ALTER TABLE settings.transform_args ALTER COLUMN position SET NOT NULL;
ALTER TABLE settings.transform_args ADD PRIMARY KEY (tps_id, position);

-- com.metavize.mvvm.tran.StringRule
ALTER TABLE settings.string_rule ADD COLUMN tmp text;
UPDATE settings.string_rule SET tmp = string;
ALTER TABLE settings.string_rule DROP COLUMN string;
ALTER TABLE settings.string_rule RENAME COLUMN tmp TO string;

ALTER TABLE settings.string_rule ADD COLUMN tmp text;
UPDATE settings.string_rule SET tmp = name;
ALTER TABLE settings.string_rule DROP COLUMN name;
ALTER TABLE settings.string_rule RENAME COLUMN tmp TO name;

ALTER TABLE settings.string_rule ADD COLUMN tmp text;
UPDATE settings.string_rule SET tmp = category;
ALTER TABLE settings.string_rule DROP COLUMN category;
ALTER TABLE settings.string_rule RENAME COLUMN tmp TO category;

ALTER TABLE settings.string_rule ADD COLUMN tmp text;
UPDATE settings.string_rule SET tmp = description;
ALTER TABLE settings.string_rule DROP COLUMN description;
ALTER TABLE settings.string_rule RENAME COLUMN tmp TO description;

-- com.metavize.mvvm.engine.TransformPersistentState
DROP TABLE settings.mvvm_tmp;

CREATE TABLE settings.mvvm_tmp AS
    SELECT id, name::text, tid, public_key, target_state::text
    FROM settings.transform_persistent_state;

DROP TABLE settings.transform_persistent_state;
ALTER TABLE settings.mvvm_tmp RENAME TO transform_persistent_state;
ALTER TABLE settings.transform_persistent_state ALTER COLUMN id SET NOT NULL;
ALTER TABLE settings.transform_persistent_state ALTER COLUMN name SET NOT NULL;
ALTER TABLE settings.transform_persistent_state ALTER COLUMN public_key SET NOT NULL;
ALTER TABLE settings.transform_persistent_state ALTER COLUMN target_state SET NOT NULL;
ALTER TABLE settings.transform_persistent_state ADD PRIMARY KEY (id);

-- com.metavize.mvvm.tran.IPMaddrDirectory
ALTER TABLE settings.ipmaddr_dir ADD COLUMN tmp text;
UPDATE settings.ipmaddr_dir SET tmp = notes;
ALTER TABLE settings.ipmaddr_dir DROP COLUMN notes;
ALTER TABLE settings.ipmaddr_dir RENAME COLUMN tmp TO notes;

-- com.metavize.mvvm.tran.MimeTypeRule
ALTER TABLE settings.mimetype_rule ADD COLUMN tmp text;
UPDATE settings.mimetype_rule SET tmp = mime_type;
ALTER TABLE settings.mimetype_rule DROP COLUMN mime_type;
ALTER TABLE settings.mimetype_rule RENAME COLUMN tmp TO mime_type;

ALTER TABLE settings.mimetype_rule ADD COLUMN tmp text;
UPDATE settings.mimetype_rule SET tmp = name;
ALTER TABLE settings.mimetype_rule DROP COLUMN name;
ALTER TABLE settings.mimetype_rule RENAME COLUMN tmp TO name;

ALTER TABLE settings.mimetype_rule ADD COLUMN tmp text;
UPDATE settings.mimetype_rule SET tmp = category;
ALTER TABLE settings.mimetype_rule DROP COLUMN category;
ALTER TABLE settings.mimetype_rule RENAME COLUMN tmp TO category;

ALTER TABLE settings.mimetype_rule ADD COLUMN tmp text;
UPDATE settings.mimetype_rule SET tmp = description;
ALTER TABLE settings.mimetype_rule DROP COLUMN description;
ALTER TABLE settings.mimetype_rule RENAME COLUMN tmp TO description;

-- com.metavize.mvvm.tran.IPMaddrRule
ALTER TABLE settings.ipmaddr_rule ADD COLUMN tmp text;
UPDATE settings.ipmaddr_rule SET tmp = name;
ALTER TABLE settings.ipmaddr_rule DROP COLUMN name;
ALTER TABLE settings.ipmaddr_rule RENAME COLUMN tmp TO name;

ALTER TABLE settings.ipmaddr_rule ADD COLUMN tmp text;
UPDATE settings.ipmaddr_rule SET tmp = category;
ALTER TABLE settings.ipmaddr_rule DROP COLUMN category;
ALTER TABLE settings.ipmaddr_rule RENAME COLUMN tmp TO category;

ALTER TABLE settings.ipmaddr_rule ADD COLUMN tmp text;
UPDATE settings.ipmaddr_rule SET tmp = description;
ALTER TABLE settings.ipmaddr_rule DROP COLUMN description;
ALTER TABLE settings.ipmaddr_rule RENAME COLUMN tmp TO description;

-------------------------------
-- fixup old bad 3.0 defaults |
-------------------------------
UPDATE settings.system_policy_rule SET IS_INBOUND = false
  WHERE (client_intf = 1 AND server_intf = 2)
    AND policy_id = (SELECT id from settings.policy WHERE is_default);
UPDATE settings.system_policy_rule SET IS_INBOUND = true
  WHERE (client_intf = 2 AND server_intf = 1)
    AND policy_id = (SELECT id from settings.policy WHERE is_default);

-------------------------
-- recreate constraints |
-------------------------

-- foreign keys

ALTER TABLE settings.tid
    ADD CONSTRAINT fk_tid_policy
    FOREIGN KEY (policy_id) REFERENCES settings.policy;

ALTER TABLE settings.user_policy_rule
    ADD CONSTRAINT fk_user_policy_rule_policy
    FOREIGN KEY (policy_id) REFERENCES settings.policy;

ALTER TABLE settings.system_policy_rule
    ADD CONSTRAINT fk_system_policy_rule_policy
    FOREIGN KEY (policy_id) REFERENCES settings.policy;
