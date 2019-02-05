#!/bin/bash

PULSAR_LIB=/var/lib/cavirin
VAULT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VAULT_BIN=${VAULT_DIR}/vault
VAULT_INIT=${VAULT_DIR}/vault.init
VAULT_CONF=${VAULT_DIR}/vault-config.hcl
VAULT_LOG=/var/log/cavirin/vault.log
VAULT_URL=https://localhost:8200

function finish {
  PID=$(pidof ${VAULT_BIN})
  if [[ -n $PID ]]; then
    echo "Stopping Vault Services ($PID)"
    sudo kill -9 $PID
  fi
}
trap finish EXIT

${VAULT_BIN} server -config=${VAULT_CONF} 2>&1 >> ${VAULT_LOG} &
PID=$(pidof ${VAULT_BIN})
echo "Starting Vault Services ($PID)"
RETRY=10
while ! curl -k ${VAULT_URL}/v1/sys/health 2>/dev/null;
do
  RETRY=$RETRY-1
  sleep 2
  if [[ $RETRY -eq 0 ]]; then
    echo "Vault services unexpectedly failed to start...."
    exit 1
  fi
done


# Vault server must be initialized (once)
if [ ! -f ${VAULT_INIT} ]; then
  echo "Initializing Vault ...."
  #Vault is up now. Let's create vault.init.
  RETRY=3
  while ! curl -k --request PUT --data '{ "secret_shares" : 5, "secret_threshold": 3}' ${VAULT_URL}/v1/sys/init > ${VAULT_INIT} 2>/dev/null;
  do
    RETRY=$RETRY-1
    sleep 2
    if [[ $RETRY -eq 0 ]]; then
      break;
    fi
  done 
fi

if [ -f ${PULSAR_LIB}/conf/pulsar-global.properties ]; then
  # Add root token to pulsar-global.properties
  root_token=$(jq -r '.root_token' ${VAULT_INIT} )
  sed -i "s/[#]*VAULT_ROOT_TOKEN=.*/VAULT_ROOT_TOKEN=$root_token/" ${PULSAR_LIB}/conf/pulsar-global.properties
fi

# Unseal keys on service start (TODO: This should be in the Control Plane service init workflow)
for key in $(jq -r '.keys[]' ${VAULT_INIT}); do
  RETRY=3
  while ! curl -k -X POST -d "{\"key\": \"$key\", \"reset\": false}" ${VAULT_URL}/v1/sys/unseal >/dev/null 2>&1;
  do
    RETRY=$RETRY-1
    sleep 2
    if [[ $RETRY -eq 0 ]]; then
      break;
    fi
  done
done

# Now wait on the Vault PID
# TODO: Vault is dying unexpectedly? Seen during pulsar-install.sh execution
# ubuntu@cavirin:~$ cat /var/log/cavirin/vault-sv.log 
#2018/11/11 15:46:09.663960 [INFO ] identity: entities restored
#2018/11/11 15:46:09.664120 [INFO ] identity: groups restored
#2018/11/11 15:46:09.666984 [INFO ] core: post-unseal setup complete
#Vault Services stopped unexpectedly
#Stopping Vault Services (29)
while [[ -n $PID ]]; do
  sleep 2
  PID=$(pidof ${VAULT_BIN})
  #echo "Waiting for Vault ($PID) ...."
done

# We should never reach here unless the Vault server halted unexpectedly
echo "Vault Services stopped unexpectedly"