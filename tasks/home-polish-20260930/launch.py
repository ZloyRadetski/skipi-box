from pathlib import Path
import json, subprocess, datetime
base = Path('/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/home-polish-20260930')
jobs = json.loads((base / 'dispatch.json').read_text())
launched = []
for job in jobs:
    args = ['timeout', '--signal=TERM', '--kill-after=30s', '45m', '/home/vqsego/Downloads/antigravity', '--project', 'Skipi', '--model', 'gemini-3.8-flash-high', '--mode', 'accept-edits', '--sandbox', '--output-format', 'json', '--print-timeout', '40m', '-p', 'Read ' + job['prompt'] + ' and implement that task exactly. The prompt file is your complete specification. Write the final report at the specified path; do not message peers or wait for the orchestrator.']
    with open(job['log'], 'ab', buffering=0) as output:
        process = subprocess.Popen(args, cwd='/home/vqsego/TorvaldsVPN/Skipi/skipi-box', stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    launched.append(dict(job, pid=process.pid, dispatched_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), model='gemini-3.8-flash-high'))
    (base / 'runs/launch-manifest.json').write_text(json.dumps(launched, ensure_ascii=False, indent=2) + '\n')
    print(job['name'] + ': process launched, pid=' + str(process.pid), flush=True)
