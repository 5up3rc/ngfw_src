# -*-ruby-*-

mail = BuildEnv::SRC['mail-casing']

NodeBuilder.makeNode(BuildEnv::SRC, 'spamassassin', [mail['localapi']], [ mail['gui']], [], BuildEnv::SRC['spam-base'])
