#!/usr/bin/env bash
# ./build.sh sim   — збірка з локальною імітацією API ПАРКС (каталог sim/) у sim_out/, без кластера
# Для реального ПАРКС використовуйте `make -f parcs.mk` (див. README.md та docs/cloud_deployment.md).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf sim_out && mkdir -p sim_out
javac -encoding UTF-8 -d sim_out sim/parcs/*.java sim/*.java src/main/java/*.java
echo "Зібрано в sim_out/."
echo "Перевірка коректності: java -Xmx2g -cp sim_out Check"
echo "Приклад:               java -Xmx2g -cp sim_out MatMul 512 4 tree"
