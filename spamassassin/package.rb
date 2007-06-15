# -*-ruby-*-

mail = BuildEnv::SRC['untangle-casing-mail']

NodeBuilder.makeNode(BuildEnv::SRC, 'untangle-node-spamassassin', 'spamassassin',
                     [mail['localapi']], [ mail['gui']], [],
                     { 'spam-base' => BuildEnv::SRC['untangle-spam'] })
