# Fail closed if upstream scripts change. Never emit exported credentials or provider tickets.
%w[server workers].each do |name|
  path = "/api/scripts/#{name}"
  source = File.read(path)
  unsafe = 'export | sudo tee /api/environment'
  raise 'Unsupported Judge0 startup script' unless source.include?(unsafe)
  File.write(path, source.sub(unsafe, 'umask 077; export | sudo tee /api/environment >/dev/null; sudo chmod 0600 /api/environment'))
end
path = '/api/app/jobs/isolate_job.rb'
source = File.read(path)
raise 'Unsupported Judge0 job logger' unless source.include?('#{submission.token}')
entrypoint = 'f.write("#{submission.language.run_cmd} #{command_line_arguments}")'
raise 'Unsupported Judge0 run entrypoint' unless source.include?(entrypoint)
# Preserve the program's real signal in Isolate metadata instead of a shell's 128+signal exit code.
source = source.sub(entrypoint, 'f.write("exec env #{submission.language.run_cmd} #{command_line_arguments}")')
File.write(path, source.gsub('#{submission.token}', '[ticket omitted]'))
path = '/api/config/environments/production.rb'
source = File.read(path)
raise 'Unsupported Judge0 logging configuration' unless source.match?(/config\.log_level\s*=\s*:\w+/)
File.write(path, source.gsub(/config\.log_level\s*=\s*:\w+/, 'config.log_level = :warn'))
File.open('/api/config/initializers/filter_parameter_logging.rb', 'a') do |file|
  file.puts
  file.puts 'Rails.application.config.filter_parameters += [:source_code, :stdin, :stdout, :stderr, :compile_output, :expected_output, :token, :authn_token, :authz_token, :x_auth_token, :secret_key_base]'
end
