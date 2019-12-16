#!/bin/bash

usage="\n
To create a sidecar security server instance you need to provide the three arguments described here below.

#1 Name for the container
#2 Local port number to bind the UI
#3 Token PIN code for autologin
"

if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]; then
    printf "$usage";
    exit;
fi

if [[ ! ( $2 =~ ^-?[0-9]+$ ) || ($2 -lt 1024) ]] ; then
    printf "Illegal port number parameter"
    exit 0;
fi

httpport=$(($2 + 1))

# Create xroad-network to provide container-to-container communication
docker network create -d bridge xroad-network

echo "=====> Build sidecar image"
docker build -f sidecar/Dockerfile -t xroad-sidecar-security-server-image sidecar/
printf "=====> Run container"
docker run --detach -p $2:4000 -p $httpport:80 --network xroad-network -e XROAD_TOKEN_PIN=$3 --name $1 xroad-sidecar-security-server-image


printf "\n
Sidecar security server admin UI should be accessible shortly in https://localhost:$2
$1-container port 80 is mapped to $httpport
"
