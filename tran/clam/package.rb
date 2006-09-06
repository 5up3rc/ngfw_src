# -*-ruby-*-

implDeps = []
guiDeps = []

%w(
    mail-casing
    ftp-casing
    http-casing
    ).each do |c|
  implDeps << Package[c]["localapi"]
  guiDeps << Package[c]["gui"]
end

TransformBuilder.makeTransform( "clam", implDeps, guiDeps, [], [ "virus", "clam-base" ] )
