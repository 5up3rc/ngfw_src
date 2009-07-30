import reports.i18n_helper
import gettext
import reports.node.untangle_base_spam

_ = reports.i18n_helper.get_translation('untangle-node-spamassassin').lgettext

reports.engine.register_node(reports.node.untangle_base_spam.SpamBaseNode('untangle-node-spamassassin', 'Spam Assassin', 'sa', 'SpamAssassin', _('spam'), _('ham'), _("Hourly Spam Rate"), _("Daily Spam Rate"), _("Top Ten Spammed")))
