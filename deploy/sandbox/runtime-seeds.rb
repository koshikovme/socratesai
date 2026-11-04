versions = JSON.parse(File.read(Rails.root.join('runtime-versions.json')))
raise 'Missing runtime version' if versions.values.any? { |value| value.nil? || value.empty? }
runtimes = [
  {id: 1001, name: "Python (#{versions.fetch('python')})", source_file: 'main.py',
   compile_cmd: '/usr/local/bin/python3.12 -I -m py_compile %s main.py', run_cmd: '/usr/local/bin/python3.12 -I main.py'},
  {id: 1002, name: "Java (OpenJDK #{versions.fetch('java')})", source_file: 'Main.java',
   compile_cmd: '/usr/local/java21/bin/javac -J-Xms16m -J-Xmx256m -J-XX:ActiveProcessorCount=1 %s Main.java',
   run_cmd: '/usr/local/java21/bin/java -XX:ActiveProcessorCount=1 -XX:+UseSerialGC -Xss1m -Xms16m -Xmx128m Main'},
  {id: 1003, name: "C++ (GCC #{versions.fetch('cpp')})", source_file: 'main.cpp',
   compile_cmd: '/usr/bin/g++ -std=c++17 -O2 -pipe %s main.cpp -o main', run_cmd: './main'}
]
ActiveRecord::Base.transaction do
  Language.unscoped.where.not(id: runtimes.map { |runtime| runtime.fetch(:id) }).update_all(is_archived: true)
  runtimes.each do |runtime|
    language = Language.unscoped.find_or_initialize_by(id: runtime.fetch(:id))
    language.update!(runtime.merge(is_archived: false))
  end
end
