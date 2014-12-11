import reports.engine
import reports.sql_helper as sql_helper

import mx
import sys

from psycopg2.extensions import DateFromMx
from psycopg2.extensions import TimestampFromMx
from reports.engine import Column
from reports.engine import FactTable
from reports.engine import Node
from reports.sql_helper import print_timing

class SmtpCasing(Node):
    def __init__(self):
        Node.__init__(self, 'untangle-casing-smtp', 'SMTP')

    def parents(self):
        return ['untangle-vm']

    @print_timing
    def setup(self):
        self.__create_mail_msgs()
        self.__create_mail_addrs()

        ft = FactTable('reports.mail_msg_totals', 'reports.mail_msgs',
                       'time_stamp',
                       [Column('hostname', 'text'), Column('username', 'text'),
                        Column('client_intf', 'smallint'),
                        Column('server_type', 'char(1)')],
                       [Column('msgs', 'bigint', 'count(*)'),
                        Column('msg_bytes', 'bigint', 'sum(msg_bytes)')])
        reports.engine.register_fact_table(ft)

        ft = FactTable('reports.mail_addr_totals', 'reports.mail_addrs',
                       'time_stamp',
                       [Column('hostname', 'text'), Column('username', 'text'),
                        Column('client_intf', 'smallint'),
                        Column('server_type', 'char(1)'),
                        Column('addr_pos', 'text'), Column('addr', 'text'),
                        Column('addr_kind', 'char(1)')],
                       [Column('msgs', 'bigint', 'count(*)'),
                        Column('msg_bytes', 'bigint', 'sum(msg_bytes)')])
        reports.engine.register_fact_table(ft)

    def post_facttable_setup(self, start_date, end_date):
        self.__make_email_table(start_date, end_date)

    def reports_cleanup(self, cutoff):
        sql_helper.drop_fact_table("mail_addrs", cutoff)
        sql_helper.drop_fact_table("mail_addr_totals", cutoff)
        sql_helper.drop_fact_table("mail_msgs", cutoff)
        sql_helper.drop_fact_table("mail_msg_totals", cutoff)
        sql_helper.drop_fact_table("email", cutoff)

    @print_timing
    def __create_mail_addrs(self):
        sql_helper.create_fact_table("""\
CREATE TABLE reports.mail_addrs (
    time_stamp timestamp without time zone,
    session_id bigint, client_intf smallint,
    server_intf smallint,
    c_client_addr inet, s_client_addr inet, c_server_addr inet,
    s_server_addr inet,
    c_client_port integer, s_client_port integer, c_server_port integer,
    s_server_port integer,
    policy_id bigint,
    username text,
    msg_id bigint,
    subject text,
    server_type char(1),
    addr_pos integer,
    addr text,
    addr_name text,
    addr_kind char(1),
    msg_bytes bigint,
    msg_attachments integer,
    hostname text,
    event_id bigserial,
    sender text,
    clam_clean boolean,
    clam_name text,
    spamassassin_score real,
    spamassassin_is_spam boolean,
    spamassassin_action character,
    spamassassin_tests_string text,
    spamblocker_score real,
    spamblocker_is_spam boolean,
    spamblocker_action character,
    spamblocker_tests_string text,
    phish_score real,
    phish_is_spam boolean,
    phish_tests_string text,
    phish_action character,
    virusblocker_clean boolean,
    virusblocker_name text)""")

        # remove obsolete columns
        sql_helper.drop_column('reports', 'mail_addrs', 'policy_inbound')
        sql_helper.drop_column('reports', 'mail_addrs', 'c2p_chunks')
        sql_helper.drop_column('reports', 'mail_addrs', 's2p_chunks')
        sql_helper.drop_column('reports', 'mail_addrs', 'p2c_chunks')
        sql_helper.drop_column('reports', 'mail_addrs', 'p2s_chunks')
        sql_helper.drop_column('reports', 'mail_addrs', 'c2p_bytes')
        sql_helper.drop_column('reports', 'mail_addrs', 's2p_bytes')
        sql_helper.drop_column('reports', 'mail_addrs', 'p2c_bytes')
        sql_helper.drop_column('reports', 'mail_addrs', 'p2s_bytes')

        # rename old commtouch columns
        sql_helper.rename_column('reports', 'mail_addrs', 'commtouchas_score', 'spamblocker_score')
        sql_helper.rename_column('reports', 'mail_addrs', 'commtouchas_is_spam', 'spamblocker_is_spam')
        sql_helper.rename_column('reports', 'mail_addrs', 'commtouchas_tests_string', 'spamblocker_tests_string')
        sql_helper.rename_column('reports', 'mail_addrs', 'commtouchas_action', 'spamblocker_action')
        sql_helper.rename_column('reports', 'mail_addrs', 'commtouchav_clean', 'virusblocker_clean');
        sql_helper.rename_column('reports', 'mail_addrs', 'commtouchav_name', 'virusblocker_name')

        sql_helper.add_column('reports', 'mail_addrs', 'event_id', 'bigserial')
        sql_helper.add_column('reports', 'mail_addrs', 'sender', 'text')
        sql_helper.add_column('reports', 'mail_addrs', 'clam_clean', 'boolean')
        sql_helper.add_column('reports', 'mail_addrs', 'clam_name', 'text')
        sql_helper.add_column('reports', 'mail_addrs', 'spamassassin_score', 'real')
        sql_helper.add_column('reports', 'mail_addrs', 'spamassassin_is_spam', 'boolean')
        sql_helper.add_column('reports', 'mail_addrs', 'spamassassin_tests_string', 'text')
        sql_helper.add_column('reports', 'mail_addrs', 'spamassassin_action', 'character')
        sql_helper.add_column('reports', 'mail_addrs', 'spamblocker_score', 'real')
        sql_helper.add_column('reports', 'mail_addrs', 'spamblocker_is_spam', 'boolean')
        sql_helper.add_column('reports', 'mail_addrs', 'spamblocker_tests_string', 'text')
        sql_helper.add_column('reports', 'mail_addrs', 'spamblocker_action', 'character')
        sql_helper.add_column('reports', 'mail_addrs', 'phish_score', 'real')
        sql_helper.add_column('reports', 'mail_addrs', 'phish_is_spam', 'boolean')
        sql_helper.add_column('reports', 'mail_addrs', 'phish_tests_string', 'text')
        sql_helper.add_column('reports', 'mail_addrs', 'phish_action', 'character')
        sql_helper.add_column('reports', 'mail_addrs', 'virusblocker_clean', 'boolean')
        sql_helper.add_column('reports', 'mail_addrs', 'virusblocker_name', 'text')

        # we used to create event_id as serial instead of bigserial - convert if necessary
        sql_helper.convert_column("reports","mail_addrs","event_id","integer","bigint");
        sql_helper.convert_column("reports","mail_addrs","session_id","integer","bigint");
        sql_helper.convert_column("reports","mail_addrs","msg_id","integer","bigint");

        # If the new index does not exist, create it
        if not sql_helper.index_exists("reports","mail_addrs","event_id", unique=True):
            sql_helper.create_index("reports","mail_addrs","event_id", unique=True);
        # If the new index does exist, delete the old one
        if sql_helper.index_exists("reports","mail_addrs","event_id", unique=True):
            sql_helper.drop_index("reports","mail_addrs","event_id", unique=False);

        sql_helper.create_index("reports","mail_addrs","policy_id");
        sql_helper.create_index("reports","mail_addrs","time_stamp");
        sql_helper.create_index("reports","mail_addrs","addr_kind");
        sql_helper.create_index("reports","mail_addrs","msg_id", unique=False);

        # virus blocker event log query indexes
        # sql_helper.create_index("reports","mail_addrs","virusblocker_clean");
        # sql_helper.create_index("reports","mail_addrs","clam_clean");

        # spam blocker event log query indexes
        # sql_helper.create_index("reports","mail_addrs","addr_kind");
        # sql_helper.create_index("reports","mail_addrs","spamassassin_action");
        # sql_helper.create_index("reports","mail_addrs","spamblocker_action");
        # sql_helper.create_index("reports","mail_addrs","phish_action");

    @print_timing
    def __make_email_table(self, start_date, end_date):
        sql_helper.create_fact_table("""\
CREATE TABLE reports.email (
        date date NOT NULL,
        email text NOT NULL,
        PRIMARY KEY (date, email));
""")

        sd = sql_helper.get_max_timestamp_with_interval('reports.email')

        conn = sql_helper.get_connection()
        try:
            sql_helper.run_sql("""\
INSERT INTO reports.email (date, email)
    SELECT DISTINCT date_trunc('day', time_stamp)::date, addr
    FROM reports.mail_addr_totals
    WHERE time_stamp >= %s::timestamp without time zone
    AND client_intf = 0 AND addr_kind = 'T'
    AND NOT addr ISNULL""", (sd,), connection=conn, auto_commit=False)
            conn.commit()
        except Exception, e:
            print e
            conn.rollback()
            raise e

    @print_timing
    def __create_mail_msgs(self):

        sql_helper.create_fact_table("""\
CREATE TABLE reports.mail_msgs (
    time_stamp timestamp without time zone,
    session_id bigint, client_intf smallint,
    server_intf smallint,
    c_client_addr inet, s_client_addr inet, c_server_addr inet,
    s_server_addr inet,
    c_client_port integer, s_client_port integer, c_server_port integer,
    s_server_port integer,
    policy_id bigint,
    username text,
    msg_id bigint,
    subject text,
    server_type char(1),
    msg_bytes bigint,
    msg_attachments integer,
    hostname text,
    event_id bigserial,
    sender text,
    receiver text,
    clam_clean boolean,
    clam_name text,
    spamassassin_score real,
    spamassassin_is_spam boolean,
    spamassassin_tests_string text,
    spamassassin_action character,
    spamblocker_score real,
    spamblocker_is_spam boolean,
    spamblocker_tests_string text,
    spamblocker_action character,
    phish_score real,
    phish_is_spam boolean,
    phish_tests_string text,
    phish_action character,
    virusblocker_clean boolean,
    virusblocker_name text)""")

        # remove obsolete columns
        sql_helper.drop_column('reports', 'mail_msgs', 'policy_inbound')
        sql_helper.drop_column('reports', 'mail_msgs', 'c2p_chunks')
        sql_helper.drop_column('reports', 'mail_msgs', 's2p_chunks')
        sql_helper.drop_column('reports', 'mail_msgs', 'p2c_chunks')
        sql_helper.drop_column('reports', 'mail_msgs', 'p2s_chunks')
        sql_helper.drop_column('reports', 'mail_msgs', 'c2p_bytes')
        sql_helper.drop_column('reports', 'mail_msgs', 's2p_bytes')
        sql_helper.drop_column('reports', 'mail_msgs', 'p2c_bytes')
        sql_helper.drop_column('reports', 'mail_msgs', 'p2s_bytes')

        # rename old commtouch columns
        sql_helper.rename_column('reports', 'mail_msgs', 'commtouchas_score', 'spamblocker_score')
        sql_helper.rename_column('reports', 'mail_msgs', 'commtouchas_is_spam', 'spamblocker_is_spam')
        sql_helper.rename_column('reports', 'mail_msgs', 'commtouchas_tests_string', 'spamblocker_tests_string')
        sql_helper.rename_column('reports', 'mail_msgs', 'commtouchas_action', 'spamblocker_action')
        sql_helper.rename_column('reports', 'mail_msgs', 'commtouchav_clean', 'virusblocker_clean');
        sql_helper.rename_column('reports', 'mail_msgs', 'commtouchav_name', 'virusblocker_name')

        sql_helper.add_column('reports', 'mail_msgs', 'event_id', 'bigserial')
        sql_helper.add_column('reports', 'mail_msgs', 'sender', 'text')
        sql_helper.add_column('reports', 'mail_msgs', 'receiver', 'text')
        sql_helper.add_column('reports', 'mail_msgs', 'clam_clean', 'boolean')
        sql_helper.add_column('reports', 'mail_msgs', 'clam_name', 'text')
        sql_helper.add_column('reports', 'mail_msgs', 'spamassassin_score', 'real')
        sql_helper.add_column('reports', 'mail_msgs', 'spamassassin_is_spam', 'boolean')
        sql_helper.add_column('reports', 'mail_msgs', 'spamassassin_tests_string', 'text')
        sql_helper.add_column('reports', 'mail_msgs', 'spamassassin_action', 'character')
        sql_helper.add_column('reports', 'mail_msgs', 'spamblocker_score', 'real')
        sql_helper.add_column('reports', 'mail_msgs', 'spamblocker_is_spam', 'boolean')
        sql_helper.add_column('reports', 'mail_msgs', 'spamblocker_tests_string', 'text')
        sql_helper.add_column('reports', 'mail_msgs', 'spamblocker_action', 'character')
        sql_helper.add_column('reports', 'mail_msgs', 'phish_score', 'real')
        sql_helper.add_column('reports', 'mail_msgs', 'phish_is_spam', 'boolean')
        sql_helper.add_column('reports', 'mail_msgs', 'phish_tests_string', 'text')
        sql_helper.add_column('reports', 'mail_msgs', 'phish_action', 'character')
        sql_helper.add_column('reports', 'mail_msgs', 'virusblocker_clean', 'boolean')
        sql_helper.add_column('reports', 'mail_msgs', 'virusblocker_name', 'text')

        # we used to create event_id as serial instead of bigserial - convert if necessary
        sql_helper.convert_column("reports","mail_msgs","event_id","integer","bigint");
        sql_helper.convert_column("reports","mail_msgs","session_id","integer","bigint");

        # If the new index does not exist, create it
        if not sql_helper.index_exists("reports","mail_msgs","msg_id", unique=True):
            sql_helper.create_index("reports","mail_msgs","msg_id", unique=True);
        # If the new index does exist, delete the old one
        if sql_helper.index_exists("reports","mail_msgs","msg_id", unique=True):
            sql_helper.drop_index("reports","mail_msgs","msg_id", unique=False);

        # If the new index does not exist, create it
        if not sql_helper.index_exists("reports","mail_msgs","event_id", unique=True):
            sql_helper.create_index("reports","mail_msgs","event_id", unique=True);
        # If the new index does exist, delete the old one
        if sql_helper.index_exists("reports","mail_msgs","event_id", unique=True):
            sql_helper.drop_index("reports","mail_msgs","event_id", unique=False);

        sql_helper.create_index("reports","mail_msgs","policy_id");
        sql_helper.create_index("reports","mail_msgs","time_stamp");

reports.engine.register_node(SmtpCasing())
