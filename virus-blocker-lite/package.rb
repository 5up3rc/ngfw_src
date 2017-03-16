# -*-ruby-*-

deps = []

%w(smtp ftp http virus-blocker-base).each do |c|
  deps << BuildEnv::SRC[c]['src']
end

AppBuilder.makeApp(BuildEnv::SRC, 'virus-blocker-lite', 'virus-blocker-lite', deps, { 'clam-base' => BuildEnv::SRC['clam-base'] })
