"""Bounded synthetic execution-boundary probes on the authorized dedicated Judge0 VM only."""
import argparse
import json
import uuid
from pathlib import Path
from live_acceptance import LiveApi


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--access-file', required=True, type=Path)
    args = parser.parse_args()
    access = json.loads(args.access_file.read_text())
    api = LiveApi(access['base_url'])
    token = api.login(access['accounts']['probe'])['token']
    resources = access['resources']
    boundaries = '''import os, socket
ok = not any(os.path.exists(p) for p in ['/judge0.conf', '/api/environment', '/run/config/runtime-catalog.yaml'])
ok = ok and not any(k in os.environ for k in ['AUTHN_TOKEN', 'POSTGRES_PASSWORD', 'REDIS_PASSWORD', 'GEMINI_API_KEY', 'APP_JWT_SECRET'])
try:
    socket.create_connection(('172.16.0.2', 80), timeout=1).close()
    ok = False
except OSError:
    pass
print(5 if ok else 0)
'''
    process_limit = '''#include <unistd.h>
#include <sys/wait.h>
#include <iostream>
int main() {
  int count=0;
  for(int i=0;i<80;i++) {
    pid_t pid=fork();
    if(pid<0) break;
    if(pid==0) { sleep(1); _exit(0); }
    count++;
  }
  while(wait(nullptr)>0) {}
  std::cout << ((count>0 && count<70)?5:0) << "\\n";
}
'''
    probes = [
        ('wrong-answer', 'python3', 'print(0)', 'WRONG_ANSWER'),
        ('compiler-error', 'java', 'public class Main { this is invalid }', 'COMPILATION_ERROR'),
        ('runtime-error', 'python3', 'raise RuntimeError("bounded synthetic probe")', 'RUNTIME_ERROR'),
        ('cpu-deadline', 'python3', 'while True: pass', 'TIME_LIMIT_EXCEEDED'),
        ('output-limit', 'cpp17', '#include <unistd.h>\n#include <signal.h>\nint main(){signal(SIGXFSZ,SIG_DFL);char b[4096]{};while(true)write(1,b,sizeof(b));}', 'OUTPUT_LIMIT_EXCEEDED'),
        ('filesystem-environment-network', 'python3', boundaries, 'ACCEPTED'),
        ('process-limit', 'cpp17', process_limit, 'ACCEPTED'),
        # Judge0 CE reports allocation failure as RE when measured memory does not cross its cap.
        ('memory-limit', 'python3', 'buffers=[bytearray(50*1024*1024) for _ in range(12)]\nprint(5)', 'RUNTIME_ERROR'),
    ]
    results = []
    for name, language, source, verdict in probes:
        run = api.request('POST', f"/api/problems/{resources['problem_id']}/runs", {
            'problemVersionId': resources['version_id'], 'languageKey': language,
            'sourceCode': source, 'idempotencyKey': str(uuid.uuid4()),
        }, token, (202,))
        result = api.poll('/api/runs/' + run['id'], token, verdict)
        assert all(not ({'stdout', 'stderr', 'compileOutput', 'sourceCode'} & set(case)) for case in result['cases'])
        results.append({'probe': name, 'verdict': verdict})
        print(json.dumps(results[-1]), flush=True)
    output = args.access_file.parent / 'production-boundaries.json'
    output.write_text(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
