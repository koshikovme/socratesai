# Compatibility changes around the pinned upstream Judge0/Isolate implementation.
# Execution still uses Isolate; this file does not introduce a process runner or scheduler.
path = '/api/app/controllers/sessions_controller.rb'
source = File.read(path)
%w[authn_token authn_header authz_token authz_header].each do |key|
  source = source.gsub("Rails.application.secrets.#{key}", "ENV.fetch('#{key.upcase}', '')")
end
File.write(path, source)
path = '/api/config/initializers/resque.rb'
File.write(path, File.read(path).sub(',\n  thread_safe: true', '').gsub(/,\s*thread_safe: true/, ''))
path = '/api/app/jobs/isolate_job.rb'
source = File.read(path).gsub('#{submission.token}', '[ticket omitted]').gsub('Dir.exists?', 'Dir.exist?').gsub('File.exists?', 'File.exist?')
entrypoint = 'f.write("#{submission.language.run_cmd} #{command_line_arguments}")'
raise 'Unrecognized upstream execution entrypoint' unless source.include?(entrypoint)
File.write(path, source.sub(entrypoint, 'f.write("exec env #{submission.language.run_cmd} #{command_line_arguments}")'))
path = '/api/config/environments/production.rb'
File.write(path, File.read(path).gsub(/config\.log_level\s*=\s*:\w+/, 'config.log_level = :warn'))
File.open('/api/config/initializers/filter_parameter_logging.rb', 'a') do |file|
  file.puts
  file.puts 'Rails.application.config.filter_parameters += [:source_code, :stdin, :stdout, :stderr, :compile_output, :expected_output, :token, :authn_token, :authz_token, :x_auth_token, :secret_key_base]'
end
File.delete('/api/config/secrets.yml') if File.exist?('/api/config/secrets.yml')
File.delete('/api/judge0.conf') if File.exist?('/api/judge0.conf')
path = '/api/scripts/load-config'
source = File.read(path)
defaults = source.index('# Now set defaults if needed...')
raise 'Unsupported upstream configuration loader' unless defaults
File.write(path, "#!/bin/bash\n" + source[defaults..])
File.open('/api/config/initializers/filter_parameter_logging.rb', 'a') do |file|
  file.puts 'ActiveRecord::Base.filter_attributes += [:source_code, :stdin, :stdout, :stderr, :compile_output, :expected_output, :token]'
end
File.write('/api/db/seeds.rb', File.read('/api/runtime-seeds.rb'))
