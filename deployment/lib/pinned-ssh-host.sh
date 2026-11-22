#!/usr/bin/env bash

configure_pinned_ssh_host() {
  local server="$1"
  local known_hosts_file="$2"
  local host key_file key_type key_blob ignored
  host="${server##*@}"
  if command -v cygpath >/dev/null 2>&1; then
    known_hosts_file="$(cygpath -u "$known_hosts_file")"
  fi
  : "${DEPLOY_SSH_HOST_KEY:?Set DEPLOY_SSH_HOST_KEY to the independently verified public SSH host key from the selected GitHub Environment}"
  [[ "$host" =~ ^[a-zA-Z0-9.-]+$ ]] || { echo 'SSH target host is invalid.' >&2; return 1; }
  [[ "$DEPLOY_SSH_HOST_KEY" != *$'\n'* && "$DEPLOY_SSH_HOST_KEY" != *$'\r'* ]] || {
    echo 'DEPLOY_SSH_HOST_KEY must be one public-key line.' >&2; return 1;
  }
  read -r key_type key_blob ignored <<< "$DEPLOY_SSH_HOST_KEY"
  [[ "$key_type" =~ ^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521))$ && -n "$key_blob" ]] || {
    echo 'DEPLOY_SSH_HOST_KEY must contain a supported OpenSSH public host key.' >&2; return 1;
  }
  key_file="${known_hosts_file}.pub"
  umask 077
  mkdir -p "$(dirname "$known_hosts_file")"
  printf '%s %s\n' "$key_type" "$key_blob" > "$key_file"
  if ! ssh-keygen -lf "$key_file" >/dev/null 2>&1; then
    rm -f "$key_file"
    echo 'DEPLOY_SSH_HOST_KEY is not a valid SSH public key.' >&2
    return 1
  fi
  printf '%s %s %s\n' "$host" "$key_type" "$key_blob" > "$known_hosts_file"
  chmod 600 "$known_hosts_file" "$key_file"
  ssh-keygen -F "$host" -f "$known_hosts_file" >/dev/null || {
    echo 'Pinned SSH host key could not be installed.' >&2; return 1;
  }
  DEPLOY_SSH_KNOWN_HOSTS_FILE="$known_hosts_file"
  SSH_OPTIONS=(-o BatchMode=yes -o StrictHostKeyChecking=yes
    -o "UserKnownHostsFile=$known_hosts_file" -o GlobalKnownHostsFile=/dev/null -o CheckHostIP=no)
}
