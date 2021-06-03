require "rake"
require "rake/clean"

def prompt(*args)
  print(*args)
  STDIN.gets.chomp
end

OUTPUT_JAR = "pg-info.jar"

task :default => :test

file OUTPUT_JAR do |f|
  version = ENV["VERSION"] || prompt("Version (e.g. 1.2.3) > ")
  sh "clojure", "-Xjar", ":version", "\"#{version}\""
end

CLOBBER << "classes"

CLEAN << OUTPUT_JAR

desc "Build the whole thing to a jar"
task :jar => OUTPUT_JAR

task :deploy => :jar do
  sh "clojure", "-Xdeploy"
end

namespace :deps do
  desc "Check for outdated dependencies"
  task :outdated do
    sh "clojure", "-Sdeps", "{:deps {olical/depot {:mvn/version \"RELEASE\"}}}",
      "-M", "-m", "depot.outdated.main", "--every"
  end
end

namespace :test do
  desc "Run clj tests"
  task :clj do
    sh "clojure", "-M:test:test-clj"
  end

  desc "Run all tests"
  task :all => :clj
end

task :test => "test:all"
