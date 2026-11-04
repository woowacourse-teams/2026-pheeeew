#!/usr/bin/env python3
"""Contract 2 admission and operator transitions, called by press-generation.py."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
from datetime import date
from uuid import UUID, uuid4


GATE = '/app/press-admission'
GATE_KEY = 'PHEEEEW_PRESS_RACE_ADMISSION_FILE'
REQUIRED_KEY = 'PHEEEEW_PRESS_RACE_ADMISSION_REQUIRED'
PROBE_PATH = '/api/v3/devices/nicknames/availability?nickname=probe'


def probe_host(container):
    if container.startswith('pheeeew-prod-app-'):
        return 'api.pheeeew.com'
    return 'api-dev.pheeeew.com'


CAS = r'''set -eu
path=$1; expected=$2; replacement=$3
flock -x -n "$path.lock" sh -c '
  set -eu
  path=$1; expected=$2; replacement=$3
  [ "$(cat "$path")" = "$expected" ] || exit 21
  umask 077
  temp="$path.$(printf "%s" "$replacement" | cut -d " " -f 2)"
  (set -C; printf "%s\n" "$replacement" > "$temp")
  sync "$temp"
  mv "$temp" "$path"
  sync "$(dirname "$path")"
' sh "$path" "$expected" "$replacement"
'''


class Operations:

    def __init__(self, core):
        self.c = core

    def version(self, inspected):
        return (inspected['Config'].get('Labels') or {}).get(self.c.CONTRACT_LABEL)

    def validate_gate_container(self, inspected):
        env = self.c.environment(inspected)
        if (self.version(inspected) != '2' or env.get(GATE_KEY) != GATE
                or env.get(REQUIRED_KEY) != 'true' or not env.get(self.c.GENERATION_KEY)):
            raise ValueError('Contract 2 requires the exact persistent admission configuration')
        for mounted in [item['Destination'] for item in inspected.get('Mounts', [])] + list(
                (inspected.get('HostConfig', {}).get('Tmpfs') or {}).keys()):
            if GATE == mounted.rstrip('/') or GATE.startswith(mounted.rstrip('/') + '/'):
                raise ValueError('Admission state must remain in the container writable layer')
        UUID(env[self.c.GENERATION_KEY])

    def gate(self, inspected, suffix=''):
        self.validate_gate_container(inspected)
        result = subprocess.run(['docker', 'exec', inspected['Id'], 'cat', GATE + suffix],
                                text=True, capture_output=True, timeout=8, check=True)
        value = result.stdout
        parts = value.removesuffix('\n').split(' ')
        if (not value.endswith('\n') or len(value) > 120 or len(parts) != 3
                or parts[0] not in ('OPEN', 'PAUSED', 'CLOSED')
                or str(UUID(parts[1])) != parts[1]
                or str(UUID(parts[2])) != self.c.environment(inspected)[self.c.GENERATION_KEY]):
            raise ValueError('Invalid admission state; preserve both servers')
        return value

    def set_gate(self, state, inspected, mode, purpose):
        self.validate_gate_container(inspected)
        commands = state.setdefault('gate_commands', [])
        command = next((item for item in commands if item['purpose'] == purpose), None)
        if command is None:
            expected = self.gate(inspected)
            replacement = f'{mode} {uuid4()} {self.c.environment(inspected)[self.c.GENERATION_KEY]}\n'
            command = dict(purpose=purpose, container_id=inspected['Id'], expected=expected,
                           replacement=replacement)
            commands.append(command)
            # The command's exact CAS tokens are durable before a possibly delayed docker exec.
            self.c.save_state(state)
        if command['container_id'] != inspected['Id'] or not command['replacement'].startswith(mode + ' '):
            raise ValueError('Admission command does not identify this transition')
        current = self.gate(inspected)
        if current != command['replacement']:
            if current != command['expected']:
                raise ValueError('Admission revision changed; refusing to replay a stale command')
            try:
                subprocess.run(['docker', 'exec', inspected['Id'], 'sh', '-c', CAS, 'sh', GATE,
                                command['expected'].rstrip('\n'), command['replacement'].rstrip('\n')],
                               capture_output=True, check=True, timeout=10)
            except (OSError, subprocess.SubprocessError):
                # A lost answer is not a lost write. Only the exact replacement proves the result.
                pass
        if self.gate(inspected) != command['replacement']:
            raise ValueError('Admission command outcome is uncertain; keep both servers and journal')
        command['confirmed'] = True
        self.c.save_state(state)
        return command['replacement']

    def wait_closed(self, inspected, token):
        if not token.startswith('CLOSED '):
            raise ValueError('Connection closure requires the exact CLOSED admission token')
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            current = self.c.container(inspected['Id'])
            if self.c.identity(current) != self.c.identity(inspected) or self.gate(current) != token:
                raise ValueError('Process or admission changed while closing connections')
            try:
                if self.gate(current, '.closed') == token:
                    return
            except (OSError, ValueError, subprocess.SubprocessError):
                pass
            time.sleep(0.1)
        raise ValueError('Logical WS closure could not be confirmed; keep inputs paused')

    def route(self, slot, port, host='api-dev.pheeeew.com'):
        if (slot not in ('app-blue', 'app-green') or not 1 <= port <= 65535
                or host not in ('api-dev.pheeeew.com', 'api.pheeeew.com')):
            raise ValueError('Invalid expected local route')
        pointer = Path('nginx/upstream/active.conf').read_text().strip()
        if pointer != f'set $upstream_app {slot};':
            raise ValueError('Proxy pointer does not match the expected owner')
        with tempfile.TemporaryDirectory(prefix='press-route-') as directory:
            headers_file, body_file = Path(directory) / 'headers', Path(directory) / 'body'
            result = subprocess.run(['curl', '-q', '--noproxy', '*', '--silent', '--show-error',
                '--connect-timeout', '2', '--max-time', '5', '--max-filesize', '65536', '--http1.1',
                '-o', str(body_file), '-D', str(headers_file), '-w', '%{http_code}',
                '--resolve', f'{host}:{port}:127.0.0.1',
                f'https://{host}:{port}{PROBE_PATH}'],
                text=True, capture_output=True, check=True, timeout=8)
            if body_file.stat().st_size > 65536 or headers_file.stat().st_size > 65536:
                raise ValueError('HTTPS proof exceeds response size limit')
            headers, body = headers_file.read_text(), body_file.read_text()
        lines = headers.splitlines()
        fields = {name.lower(): value.strip() for line in lines[1:] if ':' in line
                  for name, value in [line.split(':', 1)]}
        parsed = json.loads(body)
        if (result.stdout != '200' or not lines or len(lines[0].split()) < 2
                or lines[0].split()[1] != '200' or fields.get('x-upstream') != slot
                or not isinstance(parsed, dict) or not isinstance(parsed.get('available'), bool)):
            raise ValueError('Actual HTTPS route could not be proved; do not open inputs')

    def prepare(self, state, active):
        if self.version(active) == '2':
            if not self.gate(active).startswith('OPEN '):
                raise ValueError('Active admission is not OPEN; reconcile before deployment')
            self.route('app-blue' if state['target_slot'] == 'app-green' else 'app-green',
                       state['probe_port'], state.get('probe_host', 'api-dev.pheeeew.com'))

    def record_target(self, state, inspected):
        version = self.version(inspected)
        if state['enabled'] and version != '2':
            raise ValueError('Competition ON requires contract 2 on both servers')
        state['target_contract'] = version
        state['operations'] = state.get('operations', False) or version == '2'
        if version == '2':
            self.validate_gate_container(inspected)
            if inspected['State']['Running']:
                if not self.gate(inspected).startswith('CLOSED '):
                    raise ValueError('A warmed candidate must keep admission CLOSED')

    def check(self, state, target=False):
        prefix = 'target' if target else 'active'
        generation = state['target_generation'] if target else state['generation']
        inspected = self.c.check_instance(state[prefix + '_container'], state[prefix + '_identity'],
                                          generation, state.get('legacy', False) and not target)
        if list(self.c.db_target(self.c.environment(inspected))) != state['database']:
            raise ValueError('Prepared database target changed')
        if self.version(inspected) == '2':
            self.gate(inspected)
        return inspected

    def expected(self, state, generation):
        return dict(generation=generation, enabled=state['enabled'], activation_date=state['activation_date'])

    def change(self, env, expected, replacement, invalidate=True, activation_today=False):
        old = 'NULL' if expected['generation'] is None else f"'{UUID(expected['generation'])}'::uuid"
        new = 'NULL' if replacement['generation'] is None else f"'{UUID(replacement['generation'])}'::uuid"
        old_date = 'NULL' if expected['activation_date'] is None else f"DATE '{date.fromisoformat(expected['activation_date'])}'"
        new_date = 'NULL' if replacement['activation_date'] is None else f"DATE '{date.fromisoformat(replacement['activation_date'])}'"
        calendar_check = (f"IF {new_date} <> (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date "
                          "THEN RAISE EXCEPTION 'Activation calendar boundary changed'; END IF;"
                          if activation_today else '')
        invalidate_sql = ("UPDATE press_device_controls SET connection_generation = NULL, ready = FALSE "
                          "WHERE connection_generation IS NOT NULL;" if invalidate else '')
        sql = f"""BEGIN;
            DO $transition$ BEGIN
              PERFORM id FROM press_race_control WHERE id = TRUE FOR UPDATE;
              {calendar_check}
              UPDATE press_race_control SET active_server_generation = {new},
                  enabled = {'TRUE' if replacement['enabled'] else 'FALSE'}, activation_date = {new_date}
                WHERE id = TRUE AND active_server_generation IS NOT DISTINCT FROM {old}
                  AND enabled = {'TRUE' if expected['enabled'] else 'FALSE'}
                  AND activation_date IS NOT DISTINCT FROM {old_date};
              IF NOT FOUND THEN RAISE EXCEPTION 'Expected press control changed'; END IF;
              {invalidate_sql}
            END $transition$;
            {self.c.READ_STATE}
            COMMIT;"""
        try:
            self.c.query(env, sql)
        except (OSError, ValueError, subprocess.SubprocessError):
            # A no-op control value cannot prove that its drain/invalidation transaction ran.
            if expected == replacement or self.c.read_state(env) != replacement:
                raise ValueError('Control transition outcome is not the expected replacement')
        if self.c.read_state(env) != replacement:
            raise ValueError('Control changed after transition; keep both servers')

    def transition(self, action, target_name, state):
        if target_name != state['target_container']:
            raise ValueError('Unexpected candidate container')
        active = self.check(state)
        target = self.check(state, True)
        env = self.c.environment(active)
        current = self.c.read_state(env)
        old, new = self.expected(state, state['generation']), self.expected(state, state['target_generation'])
        if action == 'handoff':
            if current != old or state.get('open_attempted'):
                raise ValueError('Deployment state is not eligible for handoff')
            if self.version(target) == '2' and not self.gate(target).startswith('CLOSED '):
                raise ValueError('Candidate admission is not CLOSED')
            if self.version(active) == '2':
                self.set_gate(state, active, 'PAUSED', 'pause-active')
            state['phase'] = 'DRAINING'
            self.c.save_state(state)
            self.change(env, old, new)
            self.check(state)
            self.check(state, True)
            state['phase'] = 'HANDED_OFF'
            self.c.save_state(state)
        elif action == 'restore':
            if state.get('open_attempted') or state.get('restoration_open_attempted'):
                raise ValueError('Candidate OPEN was attempted; automatic ON restoration is forbidden')
            if current not in (old, new):
                raise ValueError('Unexpected control state; refusing to overwrite another owner')
            if self.version(target) == '2':
                closed = self.set_gate(state, target, 'CLOSED', 'restore-close-target')
                self.wait_closed(target, closed)
            if self.version(active) == '2':
                closed = self.set_gate(state, active, 'CLOSED', 'restore-close-active')
                self.wait_closed(active, closed)
            self.change(env, current, old)
            state['phase'] = 'RESTORED'
            self.c.save_state(state)
        elif action == 'verify':
            if current != new:
                raise ValueError('New owner could not be verified')

    def action(self, action, name):
        state = json.loads(self.c.STATE_FILE.read_text())
        if not state.get('operations'):
            if state.get('kind'):
                raise ValueError('A control operation journal cannot use deployment recovery actions')
            return
        if action == 'finalize':
            return self.finalize(state, name)
        if action == 'emergency-stop':
            if name != state['target_container']:
                raise ValueError('Emergency target does not match the recorded candidate')
            return self.emergency(state)
        target_action = action == 'open-target'
        expected_name = state['target_container'] if target_action else state['active_container']
        if name != expected_name:
            raise ValueError('Unexpected operation container')
        inspected = self.check(state, target_action)
        if self.version(inspected) != '2':
            return
        if action == 'close-active':
            closed = self.set_gate(state, inspected, 'CLOSED', 'close-active')
            self.wait_closed(inspected, closed)
            state['phase'] = 'OLD_CONNECTIONS_CLOSED'
            self.c.save_state(state)
            return
        expected = self.expected(state, state['target_generation'] if target_action else state['generation'])
        if self.c.read_state(self.c.environment(inspected)) != expected:
            raise ValueError('Expected storage ownership could not be proved')
        slot = state['target_slot'] if target_action else ('app-blue' if state['target_slot'] == 'app-green' else 'app-green')
        self.route(slot, state['probe_port'], state.get('probe_host', 'api-dev.pheeeew.com'))
        if not target_action:
            if state.get('phase') != 'RESTORED':
                raise ValueError('The original server has not been restored')
            self.wait_closed(inspected, self.gate(inspected))
            state['restoration_open_attempted'] = True
            state['phase'] = 'RESTORE_OPEN_ATTEMPTED'
            self.c.save_state(state)
        else:
            allowed_phases = ('OLD_CONNECTIONS_CLOSED',) if state['active_contract'] == '2' else ('HANDED_OFF',)
            if state.get('phase') not in allowed_phases:
                raise ValueError('Candidate cannot open before a verified handoff')
            state['open_attempted'] = True
            state['phase'] = 'OPEN_ATTEMPTED'
            self.c.save_state(state)
        self.set_gate(state, inspected, 'OPEN', action)
        self.check(state, target_action)
        self.route(slot, state['probe_port'], state.get('probe_host', 'api-dev.pheeeew.com'))
        if self.c.read_state(self.c.environment(inspected)) != expected:
            raise ValueError('Ownership changed while opening admission')
        state['phase'] = 'TARGET_OPEN' if target_action else 'ORIGINAL_OPEN'
        self.c.save_state(state)

    def emergency_candidate(self, state):
        target = self.c.container(state['target_container'], require_running=False)
        recorded = state['target_identity']
        if target['Id'] != recorded['id'] or target['Image'] != recorded['image']:
            raise ValueError('Emergency candidate was replaced; preserve the journal')
        env = self.c.environment(target)
        if (env.get(self.c.GENERATION_KEY) != state['target_generation']
                or list(self.c.db_target(env)) != state['database']):
            raise ValueError('Emergency candidate generation or database changed')
        if self.version(target) == '1':
            if state['enabled']:
                raise ValueError('Competition ON requires a contract-2 candidate')
            if not target['State']['Running']:
                return target, False
            try:
                return self.c.check_instance(state['target_container'], recorded, state['target_generation']), True
            except (OSError, ValueError, subprocess.SubprocessError):
                return target, False
        self.validate_gate_container(target)
        if not target['State']['Running']:
            return target, False
        snapshot = self.c.identity(target)
        try:
            generation = self.c.runtime_generation(target)
        except (OSError, ValueError, subprocess.SubprocessError):
            return target, False
        if generation != state['target_generation']:
            raise ValueError('Emergency candidate runtime generation changed')
        try:
            current = self.c.container(state['target_container'])
        except (OSError, ValueError, subprocess.SubprocessError):
            return target, False
        if current['Id'] != recorded['id'] or current['Image'] != recorded['image']:
            raise ValueError('Emergency candidate was replaced; preserve the journal')
        if self.c.identity(current) != snapshot:
            return target, False
        try:
            self.gate(current)
        except (OSError, ValueError, subprocess.SubprocessError):
            return target, False
        return current, True

    def emergency_incomplete(self, state, env, current, off):
        self.emergency_candidate(state)
        self.change(env, current, off)
        self.emergency_pending(state)

    def emergency_pending(self, state):
        state['phase'] = 'EMERGENCY_OFF_PENDING_CANDIDATE'
        self.c.save_state(state)
        raise ValueError('Competition OFF confirmed; admission and WS closure remain unconfirmed')

    def emergency_stopped_active(self, state):
        if (state.get('first_activation') and state.get('phase') != 'FINALIZED'
                and 'retired_control' not in state):
            raise ValueError('Initial activation is unconfirmed; preserve its journal because the original SQL may still commit')
        active = self.c.container(state['active_container'], require_running=False)
        recorded = state['active_identity']
        active_env = self.c.environment(active)
        if (active['Id'] != recorded['id'] or active['Image'] != recorded['image']
                or active['State']['Running']
                or (active_env.get(self.c.GENERATION_KEY) is not None
                    and active_env[self.c.GENERATION_KEY] != state['generation'])
                or list(self.c.db_target(active_env)) != state['database']):
            raise ValueError('Stopped original cannot be verified; preserve the journal')
        target, _ = self.emergency_candidate(state)
        env = self.c.environment(target)
        expected = self.expected(state, state['target_generation'])
        off = dict(expected, enabled=False)
        current = self.c.read_state(env)
        if current not in (expected, off):
            raise ValueError('Current candidate ownership changed; preserve the journal')
        return self.emergency_incomplete(state, env, current, off)

    def emergency(self, state):
        if not state.get('active_removed') and state.get('removal_intent', {}).get('role') == 'active':
            self.reconcile_removed_active(state)
        if state.get('active_removed'):
            return self.emergency_retired(state)
        # Superseding CAS tokens prevent delayed OPEN commands from reopening either process.
        if state.get('phase') != 'EMERGENCY_STOPPING':
            state['emergency_round'] = str(uuid4())
            state['phase'] = 'EMERGENCY_STOPPING'
            self.c.save_state(state)
        round_id = state['emergency_round']
        try:
            active = self.check(state)
        except (OSError, ValueError, subprocess.SubprocessError):
            return self.emergency_stopped_active(state)
        target, ready = self.emergency_candidate(state)
        processes = [('active', active)]
        if ready:
            processes.append(('target', target))
        closed_all = ready
        closed_tokens = []
        for role, inspected in processes:
            if self.version(inspected) == '2':
                try:
                    closed = self.set_gate(state, inspected, 'CLOSED', 'emergency-close-' + role + '-' + round_id)
                    self.wait_closed(inspected, closed)
                    closed_tokens.append((role, inspected, closed))
                except (OSError, ValueError, subprocess.SubprocessError):
                    closed_all = False
        env = self.c.environment(active)
        current = self.c.read_state(env)
        if (current['generation'] not in (state['generation'], state['target_generation'])
                or current['activation_date'] != state['activation_date']):
            raise ValueError('Emergency control owner changed; do not guess a replacement')
        replacement = dict(current, enabled=False)
        if not closed_all:
            return self.emergency_incomplete(state, env, current, replacement)
        self.change(env, current, replacement)
        try:
            for role, inspected in processes:
                current_process = self.c.container(state[role + '_container'])
                if self.c.identity(current_process, True) != self.c.identity(inspected, True):
                    raise ValueError('Emergency process changed after DB OFF')
            for _, inspected, closed in closed_tokens:
                self.wait_closed(inspected, closed)
        except (OSError, ValueError, subprocess.SubprocessError):
            return self.emergency_pending(state)
        state['phase'] = 'EMERGENCY_OFF_CONFIRMED'
        self.c.save_state(state)
        print('Competition OFF confirmed; contract-2 admissions stay CLOSED. Preserve both servers and reconcile before resume.')

    def reconcile_removed_active(self, state):
        intent = state['removal_intent']
        fixed_id = intent.get('container_id', '')
        if (intent.get('phase') != 'REMOVE_ATTEMPTED' or len(fixed_id) != 64
                or any(character not in '0123456789abcdef' for character in fixed_id)
                or intent.get('identity') != state['active_identity']
                or fixed_id != state['active_identity']['id']
                or intent.get('owner_control') != self.expected(state, state['target_generation'])):
            raise ValueError('The recorded removal intent cannot identify the previous container')
        owner, _ = self.emergency_candidate(state)
        current = self.c.read_state(self.c.environment(owner))
        if current not in (intent['owner_control'], dict(intent['owner_control'], enabled=False)):
            raise ValueError('Storage ownership changed while reconciling the removal')
        # Only a successful Docker API list response proves absence. inspect errors do not.
        observed = subprocess.run(['docker', 'container', 'ls', '--all', '--no-trunc',
            '--filter', 'id=' + fixed_id, '--format', '{{.ID}}'],
            text=True, capture_output=True, check=True, timeout=8)
        if observed.stdout.strip():
            return self.emergency_stopped_active(state)
        state['active_removed'] = True
        state['phase'] = 'REMOVAL_CONFIRMED'
        intent['phase'] = 'REMOVED_CONFIRMED'
        self.c.save_state(state)

    def emergency_retired(self, state):
        if (state.get('first_activation') and state.get('phase') != 'FINALIZED'
                and 'retired_control' not in state):
            raise ValueError('Initial activation is unconfirmed; preserve its journal because the original SQL may still commit')
        target, ready = self.emergency_candidate(state)
        if self.version(target) != '2':
            raise ValueError('The remaining server does not support safe admission control')
        env = self.c.environment(target)
        original = state.get('retired_control')
        if original is None:
            original = (state['first_activation']['desired_control'] if state.get('first_activation')
                        else self.expected(state, state['target_generation']))
            state['retired_control'] = original
        current = self.c.read_state(env)
        off = dict(original, enabled=False)
        if current not in (original, off):
            raise ValueError('The remaining server control changed; do not guess a replacement')
        if state.get('phase') != 'EMERGENCY_STOPPING':
            state['emergency_round'] = str(uuid4())
            state['phase'] = 'EMERGENCY_STOPPING'
            self.c.save_state(state)
        if not ready:
            return self.emergency_incomplete(state, env, current, off)
        try:
            closed = self.set_gate(state, target, 'CLOSED', 'emergency-close-target-' + state['emergency_round'])
            self.wait_closed(target, closed)
        except (OSError, ValueError, subprocess.SubprocessError):
            return self.emergency_incomplete(state, env, current, off)
        self.change(env, current, off)
        try:
            current_process = self.c.container(state['target_container'])
            if self.c.identity(current_process) != self.c.identity(target):
                raise ValueError('Emergency process changed after DB OFF')
            self.wait_closed(target, closed)
        except (OSError, ValueError, subprocess.SubprocessError):
            return self.emergency_pending(state)
        state['phase'] = 'EMERGENCY_OFF_CONFIRMED'
        self.c.save_state(state)
        print('Competition OFF and current admission CLOSED confirmed. The previous server is retired; preserve the current server and journal.')

    def cleanup(self, action, state):
        retiring_target = action == 'cleanup-target'
        owner = self.check(state, not retiring_target)
        expected = self.expected(state, state['generation'] if retiring_target else state['target_generation'])
        if self.c.read_state(self.c.environment(owner)) != expected:
            raise ValueError('Ownership changed before cleanup')
        if self.version(owner) == '2' and not self.gate(owner).startswith('OPEN '):
            raise ValueError('The verified owner is not admitting inputs')
        if retiring_target and state.get('open_attempted'):
            raise ValueError('Cannot automatically discard a candidate after OPEN was attempted')
        retired_name = state['target_container'] if retiring_target else state['active_container']
        snapshot = state['target_identity'] if retiring_target else state['active_identity']
        retired = self.c.container(retired_name, require_running=False)
        if self.c.identity(retired, state['legacy'] and not retiring_target) != snapshot:
            raise ValueError('Retiring process changed; preserve both servers')
        subprocess.run(['docker', 'stop', retired['Id']], check=True, capture_output=True, timeout=40)
        state['removal_intent'] = dict(role='target' if retiring_target else 'active',
            container_id=retired['Id'], identity=snapshot, owner_control=expected, phase='REMOVE_ATTEMPTED')
        self.c.save_state(state)
        subprocess.run(['docker', 'rm', retired['Id']], check=True, capture_output=True, timeout=10)
        state['removal_intent']['phase'] = 'REMOVED_CONFIRMED'
        if not retiring_target:
            state['active_removed'] = True
            state['phase'] = 'RETIRED'
        self.c.save_state(state)

    def finalize(self, state, name):
        if (name != state['target_container'] or not state.get('active_removed')
                or state.get('phase') not in ('RETIRED', 'FIRST_ACTIVATION_ATTEMPTED', 'FINALIZED')):
            raise ValueError('Deployment cannot finish before the recorded old container cleanup succeeds')
        target = self.check(state, True)
        env = self.c.environment(target)
        self.route(state['target_slot'], state['probe_port'], state.get('probe_host', 'api-dev.pheeeew.com'))
        if self.version(target) == '2' and not self.gate(target).startswith('OPEN '):
            raise ValueError('Deployment admission is not OPEN')
        expected = self.expected(state, state['target_generation'])
        first = (state.get('active_contract') == '1' and state.get('target_contract') == '2'
                 and state['generation'] is not None and not state['enabled']
                 and state['activation_date'] is None)
        if not first:
            if self.c.read_state(env) != expected:
                raise ValueError('Deployment changed the existing competition state')
            state['phase'] = 'FINALIZED'
            self.c.save_state(state)
            return
        host = subprocess.run(['hostname', '-s'], text=True, capture_output=True, check=True, timeout=5).stdout.strip()
        if (host != 'pheeeew-dev' or env.get('SPRING_PROFILES_ACTIVE') != 'dev'
                or not state['target_container'].endswith('-' + state['target_slot'])):
            raise ValueError('The development environment is not confirmed; preserve the deployment journal')
        intent = state.get('first_activation')
        if intent is None:
            if self.c.read_state(env) != expected:
                raise ValueError('Initial competition state changed before deployment completion')
            calendar = self.c.READ_STATE.replace('json_build_object(',
                "json_build_object('activation_day', (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, ")
            observed = self.c.query(env, 'BEGIN READ ONLY;\n' + calendar + '\nCOMMIT;')
            if {key: observed[key] for key in expected} != expected:
                raise ValueError('Initial competition owner changed while reading the DB calendar')
            desired = dict(expected, enabled=True, activation_date=observed['activation_day'])
            intent = dict(original_control=expected, desired_control=desired)
            state['first_activation'] = intent
            state['phase'] = 'FIRST_ACTIVATION_ATTEMPTED'
            self.c.save_state(state)
            self.change(env, expected, desired, invalidate=False, activation_today=True)
        elif self.c.read_state(env) != intent['desired_control']:
            raise ValueError('Initial activation outcome is unconfirmed; keep its intent and never replay it')
        self.check(state, True)
        self.route(state['target_slot'], state['probe_port'], state.get('probe_host', 'api-dev.pheeeew.com'))
        if (self.c.read_state(env) != intent['desired_control']
                or not self.gate(target).startswith('OPEN ')):
            raise ValueError('Initial competition activation could not be confirmed; keep the journal')
        state['phase'] = 'FINALIZED'
        self.c.save_state(state)
        print('Development deployment completed; competition is available.')

    def control(self, args):
        if args.action == 'resume-off':
            return self.resume_off(args)
        if args.action == 'reconcile-control':
            return self.reconcile_control(args)
        inspected = self.c.container(args.container, require_running=args.action != 'status')
        env = self.c.environment(inspected)
        control = self.c.read_state(env)
        if args.action == 'status':
            print(json.dumps(dict(container=args.container, identity=self.c.identity(inspected),
                contract=self.version(inspected), control=control, running=inspected['State']['Running'],
                restarting=inspected['State'].get('Restarting', False),
                admission=self.gate(inspected).strip() if self.version(inspected) == '2' and inspected['State']['Running'] else None,
                journal=json.loads(self.c.STATE_FILE.read_text()) if self.c.STATE_FILE.exists() else None), sort_keys=True))
            return
        if self.c.STATE_FILE.exists() or Path(str(self.c.STATE_FILE) + '.new').exists():
            raise ValueError('An incomplete operation exists; reconcile it before a control change')
        self.validate_gate_container(inspected)
        if not args.expected_generation or args.expected_activation_date is None:
            raise ValueError('Control changes require exact expected generation and activation date')
        expected_generation = str(UUID(args.expected_generation))
        expected_date = None if args.expected_activation_date == 'null' else date.fromisoformat(args.expected_activation_date).isoformat()
        if (control['generation'] != expected_generation or control['activation_date'] != expected_date
                or env.get(self.c.GENERATION_KEY) != expected_generation
                or self.c.runtime_generation(inspected) != expected_generation):
            raise ValueError('Expected active runtime/control state does not match')
        slot = 'app-blue' if args.container.endswith('-app-blue') else 'app-green' if args.container.endswith('-app-green') else None
        self.route(slot, args.probe_port, probe_host(args.container))
        if not self.gate(inspected).startswith('OPEN '):
            raise ValueError('Current admission is not OPEN')
        if args.action == 'activate' and control['enabled']:
            raise ValueError('Competition is already ON; preserve its existing activation state')
        if args.action == 'activate' and args.activation_date is not None:
            raise ValueError('ON does not accept a selected date')
        activation = None
        if args.action == 'activate':
            calendar = self.c.READ_STATE.replace('json_build_object(',
                "json_build_object('activation_day', (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, ")
            activation = self.c.query(env, 'BEGIN READ ONLY;\n' + calendar + '\nCOMMIT;')['activation_day']
        state = dict(kind=args.action, generation=expected_generation, enabled=control['enabled'],
                     activation_date=expected_date, active_container=args.container,
                     active_identity=self.c.identity(inspected), probe_port=args.probe_port,
                     probe_host=probe_host(args.container), phase='PREPARED',
                     database=list(self.c.db_target(env)), original_control=control,
                     requested_activation_date=activation, initial_admission=self.gate(inspected))
        replacement = (dict(control, enabled=True, activation_date=activation) if args.action == 'activate'
                       else dict(control, enabled=False))
        state['desired_control'] = replacement
        descriptor = os.open(self.c.STATE_FILE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'w') as output:
            json.dump(state, output)
            output.flush()
            os.fsync(output.fileno())
        self.c.save_state(state)
        if args.action == 'activate':
            self.change(env, control, replacement, invalidate=False, activation_today=True)
            state['phase'] = 'ACTIVATION_WRITTEN'
            self.c.save_state(state)
            state['completed_admission'] = state['initial_admission']
        else:
            self.finish_stop(state, inspected, slot)
        self.complete_control(state, inspected, slot, replacement, args.action)

    def finish_stop(self, state, inspected, slot):
        env = self.c.environment(inspected)
        desired = state['desired_control']
        if state['phase'] == 'PREPARED':
            self.set_gate(state, inspected, 'PAUSED', 'stop-pause')
            state['phase'] = 'STOP_PAUSED'
            self.c.save_state(state)
        if state['phase'] == 'STOP_PAUSED':
            self.set_gate(state, inspected, 'PAUSED', 'stop-pause')
            current = self.c.read_state(env)
            if current not in (state['original_control'], desired):
                raise ValueError('Stop control changed unexpectedly; preserve the intent')
            # Explicit reconciliation may repeat this internal drain, never a user's HTTP input.
            self.change(env, current, desired)
            state['phase'] = 'STOP_OFF_DRAINED'
            self.c.save_state(state)
        if state['phase'] == 'STOP_OFF_DRAINED':
            if self.c.read_state(env) != desired:
                raise ValueError('Competition OFF is no longer confirmed')
            valid_tokens = [command['replacement'] for command in state['gate_commands']
                            if command['purpose'] in ('stop-pause', 'stop-close')]
            if self.gate(inspected) not in valid_tokens:
                raise ValueError('Stop admission changed outside the recorded intent')
            closed = self.set_gate(state, inspected, 'CLOSED', 'stop-close')
            self.wait_closed(inspected, closed)
            state['phase'] = 'STOP_CLOSED'
            self.c.save_state(state)
        if state['phase'] == 'STOP_CLOSED':
            self.route(slot, state['probe_port'], state.get('probe_host', 'api-dev.pheeeew.com'))
            if self.c.read_state(env) != desired:
                raise ValueError('Stop outcome is uncertain; admission remains CLOSED')
            self.wait_closed(inspected, self.gate(inspected))
            state['phase'] = 'HTTP_OPEN_ATTEMPTED'
            self.c.save_state(state)
        if state['phase'] != 'HTTP_OPEN_ATTEMPTED' or self.c.read_state(env) != desired:
            raise ValueError('Stop is not eligible to complete HTTP admission')
        self.route(slot, state['probe_port'], state.get('probe_host', 'api-dev.pheeeew.com'))
        state['completed_admission'] = self.set_gate(state, inspected, 'OPEN', 'stop-http-open')

    def complete_control(self, state, inspected, slot, expected, action):
        self.c.check_instance(state['active_container'], state['active_identity'], state['generation'])
        if (self.c.read_state(self.c.environment(inspected)) != expected
                or self.gate(inspected) != state['completed_admission']):
            raise ValueError('Control/admission changed before completion; keep journal')
        self.route(slot, state['probe_port'], state.get('probe_host', 'api-dev.pheeeew.com'))
        self.c.STATE_FILE.unlink()
        print(json.dumps(dict(action=action, kind=state['kind'], control=expected,
                              admission=state['completed_admission'].strip()), sort_keys=True))

    def reconcile_control(self, args):
        state = json.loads(self.c.STATE_FILE.read_text())
        if state.get('kind') not in ('stop', 'activate'):
            raise ValueError('This journal is not a standalone control intent')
        if args.expected_activation_date is None:
            raise ValueError('Reconciliation requires the original expected activation date')
        day = None if args.expected_activation_date == 'null' else date.fromisoformat(args.expected_activation_date).isoformat()
        if (args.container != state['active_container'] or not args.expected_generation
                or str(UUID(args.expected_generation)) != state['generation']
                or day != state['activation_date'] or args.probe_port != state['probe_port']
                or args.activation_date not in (None, state['requested_activation_date'])):
            raise ValueError('Reconciliation must identify the exact original control intent')
        inspected = self.c.check_instance(args.container, state['active_identity'], state['generation'])
        self.validate_gate_container(inspected)
        env = self.c.environment(inspected)
        if list(self.c.db_target(env)) != state['database']:
            raise ValueError('The recorded database target changed')
        slot = 'app-blue' if args.container.endswith('-app-blue') else 'app-green'
        self.route(slot, state['probe_port'], state.get('probe_host', 'api-dev.pheeeew.com'))
        current = self.c.read_state(env)
        if state['kind'] == 'activate':
            if current == state['original_control']:
                # A read of the old value cannot rule out a still-running SQL command
                # committing later. Keep the intent and never replay activation here.
                raise ValueError('Activation outcome is unconfirmed; preserve the intent because the original SQL may still commit')
            if current != state['desired_control']:
                raise ValueError('Activation control changed unexpectedly; preserve the intent')
            state['completed_admission'] = state['initial_admission']
            self.complete_control(state, inspected, slot, current, args.action)
        else:
            self.finish_stop(state, inspected, slot)
            self.complete_control(state, inspected, slot, state['desired_control'], args.action)

    def resume_off(self, args):
        state = json.loads(self.c.STATE_FILE.read_text())
        if state.get('kind') in ('stop', 'activate'):
            raise ValueError('Use reconcile-control for a standalone control intent')
        if state.get('phase') not in ('EMERGENCY_OFF_CONFIRMED', 'HTTP_OPEN_ATTEMPTED'):
            raise ValueError('Only a confirmed emergency-OFF deployment can use explicit resume-off')
        if not args.expected_generation or args.expected_activation_date is None:
            raise ValueError('Resume requires exact expected generation and activation date')
        generation = str(UUID(args.expected_generation))
        day = None if args.expected_activation_date == 'null' else date.fromisoformat(args.expected_activation_date).isoformat()
        target = self.check(state, True)
        candidates = {state['target_container']: (target, state['target_generation'], state['target_slot'])}
        processes = [target]
        if not state.get('active_removed'):
            active = self.check(state)
            if (self.version(active) not in ('1', '2')
                    or self.c.identity(active, True) != state['active_process_identity']):
                raise ValueError('Emergency resume requires the recorded live storage-compatible processes')
            candidates[state['active_container']] = (active, state['generation'],
                'app-blue' if state['target_slot'] == 'app-green' else 'app-green')
            processes.append(active)
        if (self.version(target) not in ('1', '2')
                or self.c.identity(target, True) != state['target_process_identity']):
            raise ValueError('Emergency resume requires the recorded live target process')
        if args.container not in candidates or candidates[args.container][1] != generation:
            raise ValueError('Resume target does not identify the recorded current owner')
        owner, _, slot = candidates[args.container]
        if self.version(owner) != '2':
            raise ValueError('OFF resume can open only a contract-2 current owner')
        expected = dict(generation=generation, enabled=False, activation_date=day)
        if self.c.read_state(self.c.environment(owner)) != expected:
            raise ValueError('Resume requires the exact already-OFF control state')
        if args.probe_port != state['probe_port']:
            raise ValueError('Resume must use the recorded local HTTPS port')
        self.route(slot, args.probe_port, state.get('probe_host', 'api-dev.pheeeew.com'))
        purpose = 'resume-off-open-' + state['emergency_round']
        prior = next((command for command in state.get('gate_commands', []) if command['purpose'] == purpose), None)
        for inspected in processes:
            if self.version(inspected) == '1':
                if inspected['Id'] == owner['Id']:
                    raise ValueError('A contract-1 process cannot be the resumed owner')
                # Its exact recorded process supports the common DB fence and is not the owner.
                continue
            token = self.gate(inspected)
            if prior and inspected['Id'] == owner['Id'] and token == prior['replacement']:
                continue
            if not token.startswith('CLOSED '):
                raise ValueError('Both admissions must be CLOSED before explicit OFF resume')
            self.wait_closed(inspected, token)
            if any(command['container_id'] == inspected['Id'] and command['replacement'].startswith('OPEN ')
                   and command['expected'] == token for command in state.get('gate_commands', [])):
                raise ValueError('A delayed OPEN still has a valid token; do not resume')
        state['resume_container'] = args.container
        state['phase'] = 'HTTP_OPEN_ATTEMPTED'
        self.c.save_state(state)
        self.set_gate(state, owner, 'OPEN', purpose)
        self.check(state, args.container == state['target_container'])
        self.route(slot, args.probe_port, state.get('probe_host', 'api-dev.pheeeew.com'))
        if self.c.read_state(self.c.environment(owner)) != expected:
            raise ValueError('OFF resume ownership changed; retain journal and both servers')
        state['phase'] = 'HTTP_RECOVERED'
        self.c.save_state(state)
        print('OFF HTTP/recovery admission confirmed. The current server and journal remain for explicit review and cleanup.')
