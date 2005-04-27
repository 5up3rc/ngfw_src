CREATE TABLE admin_settings (
    admin_settings_id int8 NOT NULL,
    summary_period_id int8,
    PRIMARY KEY (admin_settings_id));

CREATE TABLE mvvm_user (
    id int8 NOT NULL,
    login varchar(24) NOT NULL,
    password bytea NOT NULL,
    name varchar(64) NOT NULL,
    notes varchar(256),
    send_alerts bool,
    admin_setting_id int8,
    PRIMARY KEY (ID));

CREATE TABLE upgrade_settings (
    upgrade_settings_id int8 NOT NULL,
    auto_upgrade bool NOT NULL,
    period int8 NOT NULL,
    PRIMARY KEY (upgrade_settings_id));

CREATE TABLE mail_settings (
    mail_settings_id int8 NOT NULL,
    report_email varchar(255),
    smtp_host varchar(255),
    from_address varchar(255),
    PRIMARY KEY (mail_settings_id));

CREATE TABLE transform_args (
    transform_desc_id int8 NOT NULL,
    arg varchar(255) NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (transform_desc_id, position));

CREATE TABLE transform_manager_state (
    id int8 NOT NULL,
    last_tid int8,
    PRIMARY KEY (id));

CREATE TABLE uri_rule (
    rule_id int8 NOT NULL,
    uri varchar(255),
    name varchar(255),
    category varchar(255),
    description varchar(255),
    live bool,
    alert bool,
    log bool,
    PRIMARY KEY (rule_id));

CREATE TABLE transform_desc (id int8 NOT NULL,
    tid int8,
    name varchar(64) NOT NULL,
    class_name varchar(80) NOT NULL,
    public_key bytea NOT NULL,
    casing bool,
    parent_transform varchar(64),
    single_instance bool,
    target_state varchar(255) NOT NULL,
    read_only bool NOT NULL,
    tcp_client_read_buffer_size int4 NOT NULL,
    tcp_server_read_buffer_size int4 NOT NULL,
    udp_max_packet_size int4 NOT NULL,
    display_name varchar(255) NOT NULL,
    gui_class_name varchar(255) NOT NULL,
    red int4,
    green int4,
    blue int4,
    alpha int4,
    PRIMARY KEY (id));

CREATE TABLE transform_desc_parent (
    desc_id int8 NOT NULL,
    parent varchar(64) NOT NULL,
    PRIMARY KEY (desc_id, parent));

CREATE TABLE period (period_id int8 NOT NULL,
    hour int4 NOT NULL,
    minute int4 NOT NULL,
    sunday bool,
    monday bool,
    tuesday bool,
    wednesday bool,
    thursday bool,
    friday bool,
    saturday bool,
    PRIMARY KEY (period_id));

CREATE TABLE string_rule (rule_id int8 NOT NULL,
    string varchar(255),
    name varchar(255),
    category varchar(255),
    description varchar(255),
    live bool,
    alert bool,
    log bool,
    PRIMARY KEY (rule_id));

CREATE TABLE mvvm_evt_pipeline (
    event_id int8 NOT NULL,
    pipeline_info int8,
    time_stamp timestamp,
    PRIMARY KEY (event_id));

CREATE TABLE tid (
    id int8 NOT NULL,
    PRIMARY KEY (id));

CREATE TABLE rule (
    rule_id int8 NOT NULL,
    name varchar(255),
    category varchar(255),
    description varchar(255),
    live bool,
    alert bool,
    log bool,
    PRIMARY KEY (rule_id));

CREATE TABLE ipmaddr_dir (
    id int8 NOT NULL,
    notes varchar(255),
    PRIMARY KEY (id));

CREATE TABLE mimetype_rule (
    rule_id int8 NOT NULL,
    mime_type varchar(255),
    name varchar(255),
    category varchar(255),
    description varchar(255),
    live bool,
    alert bool,
    log bool,
    PRIMARY KEY (rule_id));

CREATE TABLE ipmaddr_dir_entries (
    ipmaddr_dir_id int8 NOT NULL,
    rule_id int8 NOT NULL,
    position int4 NOT NULL,
    PRIMARY KEY (ipmaddr_dir_id, position));

CREATE TABLE ipmaddr_rule (
    rule_id int8 NOT NULL,
    ipmaddr inet,
    name varchar(255),
    category varchar(255),
    description varchar(255),
    live bool,
    alert bool,
    log bool,
    PRIMARY KEY (rule_id));

CREATE TABLE pipeline_info (
    id int8 NOT NULL,
    session_id int4,
    proto int2,
    create_date timestamp,
    raze_date timestamp,
    c2p_bytes int8,
    s2p_bytes int8,
    p2c_bytes int8,
    p2s_bytes int8,
    c2p_chunks int8,
    s2p_chunks int8,
    p2c_chunks int8,
    p2s_chunks int8,
    client_intf int2,
    server_intf int2,
    c_client_addr inet,
    s_client_addr inet,
    c_server_addr inet,
    s_server_addr inet,
    c_client_port int4,
    s_client_port int4,
    c_server_port int4,
    s_server_port int4,
    PRIMARY KEY (id));

CREATE TABLE mvvm_login_evt (
    event_id int8 NOT NULL,
    login varchar(255),
    local bool,
    succeeded bool,
    reason char(1),
    time_stamp timestamp,
    PRIMARY KEY (event_id));

ALTER TABLE admin_settings ADD CONSTRAINT FK71B1F7333C031EE0 FOREIGN KEY (summary_period_id) REFERENCES period;

ALTER TABLE mvvm_user ADD CONSTRAINT FKCC5A228ACD112C9A FOREIGN KEY (admin_setting_id) REFERENCES admin_settings;

ALTER TABLE upgrade_settings ADD CONSTRAINT FK4DC4F2E68C7669C1 FOREIGN KEY (period) REFERENCES period;

ALTER TABLE transform_args ADD CONSTRAINT FK1C0835F0A8A3B796 FOREIGN KEY (transform_desc_id) REFERENCES transform_desc;

ALTER TABLE transform_desc ADD CONSTRAINT FK1C0963A41446F FOREIGN KEY (tid) REFERENCES tid;

ALTER TABLE transform_desc_parent ADD CONSTRAINT FKC61FF5A58797A189 FOREIGN KEY (desc_id) REFERENCES transform_desc;

CREATE INDEX idx_string_rule ON string_rule (string);

ALTER TABLE mvvm_evt_pipeline ADD CONSTRAINT FK9CF995D62F5A0D7 FOREIGN KEY (pipeline_info) REFERENCES pipeline_info ON DELETE CASCADE;

ALTER TABLE ipmaddr_dir_entries ADD CONSTRAINT FKC67DE356B5257E75 FOREIGN KEY (ipmaddr_dir_id) REFERENCES ipmaddr_dir;

ALTER TABLE ipmaddr_dir_entries ADD CONSTRAINT FKC67DE356871AAD3E FOREIGN KEY (rule_id) REFERENCES ipmaddr_rule;

CREATE SEQUENCE hibernate_sequence;
