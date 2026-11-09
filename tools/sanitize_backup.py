"""Turns a real SKIPI backup into a test fixture with the same shape and none of the personal data.

Usage: python tools/sanitize_backup.py <real-backup.json> <fixture.json>

Servers, subscription links, credentials, network names, routing rules and profile text are replaced by
synthetic values on example.test; settings keep their values. A profile's text is replaced by a minimal one,
so after sanitizing, restore the fixture once and copy each restored traffic config text back (the app
appends its own generated [SKIPI] section). Check the result by eye for anything still personal.
"""
import hashlib, json, re, sys

SECRET_KEYS = re.compile(r'(pass|secret|private|public|preshared|psk|token|key|uuid|^id$|user|name$|sni|host|address|server|endpoint|domain|url|path|serviceName|authority|pbk|sid|remarks|title|announce|email|ssid)', re.I)
KEEP_KEYS = re.compile(r'^(type|security|mode|fp|flow|encryption|headerType|network|protocol|strategy|displayMode|probeInterval|probeTimeout|tolerance|probeUrl|userAgent|alpn|method|transport)$')
UUID = re.compile(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', re.I)


def tag(value, key):
    return 'x-' + key[:10] + '-' + hashlib.sha1(value.encode('utf-8')).hexdigest()[:6]


def scrub_string(key, value):
    if value == '' or UUID.match(value) is None and re.fullmatch(r'[0-9]{1,6}', value):
        return value
    if KEEP_KEYS.match(key):
        return value
    if value.startswith('{') or value.startswith('['):
        return value  # structured json strings (fragment settings, extras) carry no identity
    if UUID.match(value):
        return '00000000-0000-4000-8000-' + hashlib.sha1(value.encode()).hexdigest()[:12]
    if SECRET_KEYS.search(key) or len(value) > 16 or re.search(r'[./:@]', value):
        return tag(value, key)
    return value


def scrub(obj, key=''):
    if isinstance(obj, dict):
        return {k: scrub(v, k) for k, v in obj.items()}
    if isinstance(obj, list):
        return [scrub(v, key) for v in obj]
    if isinstance(obj, str):
        return scrub_string(key, obj)
    return obj


MINIMAL_CUSTOM = json.dumps({
    'outbounds': [{
        'protocol': 'vless',
        'settings': {'vnext': [{'address': 'custom.example.test', 'port': 443,
                                'users': [{'id': '00000000-0000-4000-8000-000000000001', 'encryption': 'none'}]}]},
        'streamSettings': {'network': 'tcp', 'security': 'tls'},
    }],
}, indent=2)

SYNTHETIC_RAW = (
    '# SKIPI configuration\n[General]\ndns-server = 1.1.1.1\n[Proxy Group]\n'
    'Auto = url-test, Server 1, Server 2, url=https://probe.example.test/generate_204, interval=15, '
    'skipi-display=always, skipi-burst-probe=true\n[Rule]\nDOMAIN-SUFFIX,example.test,Auto\nFINAL,Auto\n'
    '[URL Rewrite]\nresource-geoip-url = https://geo.example.test/geoip.dat\n'
)


def main(src, dst, per_protocol=12):
    d = json.load(open(src, encoding='utf-8-sig'))
    data = d['data']

    # settings: keep the values (they are preferences), scrub credentials and network names
    s = data['settings']
    for k, v in list(s.items()):
        if isinstance(v, str) and re.search(r'(sername|assword|secret)', k):
            s[k] = tag(v, k)
    for rule in s.get('networkAutomationRules') or []:
        if rule.get('ssid'):
            rule['ssid'] = 'TestWifi'

    # subscription groups
    for i, g in enumerate(data['subscriptionGroups'], 1):
        g['name'] = g['name'] if g.get('builtIn') else 'Provider %d' % i
        for f in ('url', 'supportUrl', 'profileWebPageUrl', 'announceUrl'):
            if g.get(f):
                g[f] = 'https://sub%d.example.test/%s' % (i, f)
        for f in ('hwid', 'ageSecretKey'):
            if g.get(f):
                g[f] = ''
        if g.get('supportEmail'):
            g['supportEmail'] = 'support@example.test'
        for f in ('profileTitle', 'announce'):
            if g.get(f):
                g[f] = 'Provider %d %s' % (i, f)

    # servers: a handful per protocol, scrubbed; groups only keep members that stay
    kept, count = [], {}
    for p in data['proxyServers']:
        n = count.get(p['protocol'], 0)
        if n < per_protocol:
            count[p['protocol']] = n + 1
            kept.append(p)
    originals = {p['id']: p for p in data['proxyServers']}
    for p in list(kept):
        if p['protocol'] == 'strategy-group':
            members = p['payload'].get('proxyServerIds', [])[:3]
            p['payload']['proxyServerIds'] = members
            for m in members:
                if m in originals and originals[m] not in kept:
                    kept.append(originals[m])
            sel = p['payload'].get('selectedMemberId')
            if sel in originals and originals[sel] not in kept:
                kept.append(originals[sel])
    kept.sort(key=lambda p: data['proxyServers'].index(p))
    kept_ids = {p['id'] for p in kept}
    first_id = kept[0]['id']
    for i, p in enumerate(kept, 1):
        pl = p['payload']
        if p['protocol'] == 'custom':
            pl['remarks'] = 'Custom %d' % i
            pl['configJson'] = MINIMAL_CUSTOM
        elif p['protocol'] == 'strategy-group':
            pl['remarks'] = 'Group %d' % i
            pl['probeUrl'] = pl.get('probeUrl') and 'https://probe.example.test/generate_204'
            pl['sourceTrafficConfigId'] = None
            pl['sourcePolicyGroupName'] = ''
            pl['proxyServerIds'] = [m for m in pl.get('proxyServerIds', []) if m in kept_ids]
            if pl.get('selectedMemberId') not in kept_ids:
                pl['selectedMemberId'] = None
        else:
            p['payload'] = scrub(pl)
            p['payload']['remarks'] = 'Server %d' % i
    data['proxyServers'] = kept

    def fix_id(v):
        return v if v in kept_ids else first_id

    if 'selectedProxyServerId' in s and s['selectedProxyServerId'] is not None:
        s['selectedProxyServerId'] = fix_id(s['selectedProxyServerId'])
    for rule in s.get('networkAutomationRules') or []:
        if rule.get('targetServerId') is not None:
            rule['targetServerId'] = fix_id(rule['targetServerId'])

    # routing rules: keep shape, drop the user's own domains and apps
    for i, r in enumerate(data['routeRules'], 1):
        r['remarks'] = 'Rule %d' % i
        for f in ('domain', 'ip', 'process', 'port'):
            if isinstance(r.get(f), list) and r[f]:
                r[f] = ['example%d.test' % i] if f == 'domain' else r[f][:1]
        if str(r.get('outboundTag', '')).isdigit():
            r['outboundTag'] = str(fix_id(int(r['outboundTag'])))

    for i, c in enumerate(data['trafficConfigs'], 1):
        c['name'] = 'Profile %d' % i
        ua = re.search(r'resource-user-agent = (\S+)', c.get('rawConfig', ''))
        nl = chr(10)
        raw = ('# SKIPI configuration' + nl + '[General]' + nl + 'dns-server = 1.1.1.1' + nl + '[Proxy Group]' + nl +
               '[Rule]' + nl + 'FINAL,DIRECT' + nl + '[URL Rewrite]' + nl)
        if ua:
            raw += 'resource-user-agent = %s' % ua.group(1) + nl
        c['rawConfig'] = raw
        if c.get('sourceUrl'):
            c['sourceUrl'] = 'https://profile%d.example.test/config' % i
        if c.get('proxyAppListSelectedApps'):
            c['proxyAppListSelectedApps'] = [re.sub(r':.*', ':com.example.app', a) for a in c['proxyAppListSelectedApps'][:2]]
    data['proxyAppListSelectedApps'] = [re.sub(r':.*', ':com.example.app', a) for a in data['proxyAppListSelectedApps'][:2]]

    json.dump(d, open(dst, 'w', encoding='utf-8', newline='\n'), indent=2, ensure_ascii=False)
    out = json.dumps(d, ensure_ascii=False)
    print(dst, len(out), 'bytes;', len(kept), 'servers')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
