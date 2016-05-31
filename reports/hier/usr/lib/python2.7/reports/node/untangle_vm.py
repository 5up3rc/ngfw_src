import gettext
import logging
import mx
import reports.engine
import reports.sql_helper as sql_helper
import uvm.i18n_helper

from reports.engine import Node

class UvmNode(Node):
    def __init__(self):
        Node.__init__(self, 'untangle-vm', "Server")

    def create_tables(self):
        self.__build_admin_logins_table()
        self.__build_sessions_table()
        self.__build_session_min_table()
        self.__build_session_minutes_view()
        self.__build_penaltybox_table()
        self.__build_quotas_table()
        self.__build_host_table_updates_table()
        self.__build_device_table_updates_table()
        self.__build_alerts_events_table()
        self.__build_settings_changes_table()

    def reports_cleanup(self, cutoff):
        sql_helper.clean_table("admin_logins", cutoff)
        sql_helper.clean_table("sessions", cutoff)
        sql_helper.clean_table("session_min", cutoff)
        sql_helper.clean_table("penaltybox", cutoff)
        sql_helper.clean_table("quotas", cutoff)
        sql_helper.clean_table("host_table_updates", cutoff)
        sql_helper.clean_table("device_table_updates", cutoff)
        sql_helper.clean_table("alerts", cutoff)

    @sql_helper.print_timing
    def __build_admin_logins_table(self):
        sql_helper.create_table("""\
CREATE TABLE reports.admin_logins (
    time_stamp timestamp without time zone,
    login text,
    local boolean,
    client_addr inet,
    succeeded boolean,
    reason char(1) )""")

    @sql_helper.print_timing
    def __build_sessions_table( self ):
        sql_helper.create_table("""\
CREATE TABLE reports.sessions (
        session_id int8 NOT NULL,
        time_stamp timestamp NOT NULL,
        end_time timestamp NOT NULL,
        bypassed boolean,
        entitled boolean,
        protocol int2,
        icmp_type int2,
        hostname text,
        username text,
        policy_id int2,
        policy_rule_id int2,
        c_client_addr inet,
        c_server_addr inet,
        c_server_port int4,
        c_client_port int4,
        s_client_addr inet,
        s_server_addr inet,
        s_server_port int4,
        s_client_port int4,
        client_intf int2,
        server_intf int2,
        client_country text,
        client_latitude real,
        client_longitude real,
        server_country text,
        server_latitude real,
        server_longitude real,
        c2p_bytes int8 default 0,
        p2c_bytes int8 default 0,
        s2p_bytes int8 default 0,
        p2s_bytes int8 default 0,
        filter_prefix text,
        shield_blocked boolean,
        firewall_blocked boolean,
        firewall_flagged boolean,
        firewall_rule_index integer,
        application_control_lite_protocol text,
        application_control_lite_blocked boolean,
        captive_portal_blocked boolean,
        captive_portal_rule_index integer,
        application_control_application text,
        application_control_protochain text,
        application_control_category text,
        application_control_blocked boolean,
        application_control_flagged boolean,
        application_control_confidence integer,
        application_control_ruleid integer,
        application_control_detail text,
        bandwidth_control_priority integer,
        bandwidth_control_rule integer,
        ssl_inspector_ruleid integer,
        ssl_inspector_status text,
        ssl_inspector_detail text)""", 
                                ["session_id"],
                                ["time_stamp",
                                 "hostname",
                                 "username",
                                 "policy_id",
                                 "c_client_addr",
                                 "s_server_addr",
                                 "client_intf",
                                 "server_intf",
                                 "firewall_flagged",
                                 "firewall_blocked",
                                 "application_control_application",
                                 "application_control_blocked",
                                 "application_control_flagged"])

        sql_helper.add_column('sessions','policy_rule_id','int2') # 12.1
        sql_helper.add_column('sessions','client_country','text') # 12.1
        sql_helper.add_column('sessions','client_latitude','real') # 12.1
        sql_helper.add_column('sessions','client_longitude','real') # 12.1
        sql_helper.add_column('sessions','server_country','text') # 12.1
        sql_helper.add_column('sessions','server_latitude','real') # 12.1
        sql_helper.add_column('sessions','server_longitude','real') # 12.1

    @sql_helper.print_timing
    def __build_session_min_table( self ):
        sql_helper.create_table("""\
CREATE TABLE reports.session_min (
        session_id int8 NOT NULL,
        time_stamp timestamp NOT NULL,
        c2s_bytes int8,
        s2c_bytes int8)""",
                                [],
                                ["time_stamp",
                                 "session_id"])

    @sql_helper.print_timing
    def __build_session_minutes_view( self ):
        sql_helper.drop_view("session_minutes")
        sql_helper.create_view("""\
CREATE VIEW reports.session_minutes AS
        SELECT 
        sessions.session_id,
        sessions.end_time,
        sessions.bypassed,
        sessions.entitled,
        sessions.protocol,
        sessions.icmp_type,
        sessions.hostname,
        sessions.username,
        sessions.policy_id,
        sessions.policy_rule_id,
        sessions.c_client_addr,
        sessions.c_server_addr,
        sessions.c_server_port,
        sessions.c_client_port,
        sessions.s_client_addr,
        sessions.s_server_addr,
        sessions.s_server_port,
        sessions.s_client_port,
        sessions.client_intf,
        sessions.server_intf,
        sessions.client_country,
        sessions.client_latitude,
        sessions.client_longitude,
        sessions.server_country,
        sessions.server_latitude,
        sessions.server_longitude,
        sessions.c2p_bytes,
        sessions.p2c_bytes,
        sessions.s2p_bytes,
        sessions.p2s_bytes,
        sessions.filter_prefix,
        sessions.shield_blocked,
        sessions.firewall_blocked,
        sessions.firewall_flagged,
        sessions.firewall_rule_index,
        sessions.application_control_lite_protocol,
        sessions.application_control_lite_blocked,
        sessions.captive_portal_blocked,
        sessions.captive_portal_rule_index,
        sessions.application_control_application,
        sessions.application_control_protochain,
        sessions.application_control_category,
        sessions.application_control_blocked,
        sessions.application_control_flagged,
        sessions.application_control_confidence,
        sessions.application_control_ruleid,
        sessions.application_control_detail,
        sessions.bandwidth_control_priority,
        sessions.bandwidth_control_rule,
        sessions.ssl_inspector_ruleid,
        sessions.ssl_inspector_status,
        sessions.ssl_inspector_detail,
        session_min.time_stamp,
        session_min.c2s_bytes,
        session_min.s2c_bytes 
        FROM
        reports.session_min INNER JOIN reports.sessions USING (session_id)""")
        
    @sql_helper.print_timing
    def __build_alerts_events_table( self ):
        sql_helper.create_table("""\
CREATE TABLE reports.alerts (
        time_stamp timestamp NOT NULL,
        description text NOT NULL,
        summary_text text NOT NULL,
        json text NOT NULL)""")

    @sql_helper.print_timing
    def __build_settings_changes_table( self ):
        sql_helper.create_table("""\
CREATE TABLE reports.settings_changes (
        time_stamp timestamp NOT NULL,
        settings_file text NOT NULL,
        username text NOT NULL,
        hostname text NOT NULL)""")

    def __build_penaltybox_table( self ):
        sql_helper.create_table("""
CREATE TABLE reports.penaltybox (
        address inet,
        reason text,
        start_time timestamp,
        end_time timestamp,
        time_stamp timestamp)""", [], ["time_stamp","start_time"])

    def __build_quotas_table( self ):
        sql_helper.create_table("""
CREATE TABLE reports.quotas (
        time_stamp timestamp,
        address inet,
        action integer,
        size bigint,
        reason text)""", [], ["time_stamp"])

        sql_helper.drop_column("quotas","event_id") #11.2 conversion

    def __build_host_table_updates_table( self ):
        sql_helper.create_table("""
CREATE TABLE reports.host_table_updates (
        address inet,
        key text,
        value text,
        time_stamp timestamp)""",[],["time_stamp"])

    def __build_device_table_updates_table( self ):
        sql_helper.create_table("""
CREATE TABLE reports.device_table_updates (
        mac_address text,
        key text,
        value text,
        time_stamp timestamp)""",[],["time_stamp"])
        
    def teardown(self):
        pass

reports.engine.register_node(UvmNode())
