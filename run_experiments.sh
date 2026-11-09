#!/usr/bin/env bash
# Запускати на VM "app" з каталогу проєкту (після налаштування out/parcs.jar та out/server).
# Збирає:
#   results/cloud_seq.csv      — послідовні тести: naive проти blocked та вплив розміру блоку
#   results/cloud_results.csv  — ПАРКС: різна кількість воркерів, режими flat/tree, розміри n
# Використання: bash run_experiments.sh [повторів=3]
# Змінні середовища: SIZES="512 1024 2048"  BS=64  JOPTS="-Xmx2g"
# Рекомендовано запускати так, щоб серія не обірвалась при розриві SSH:
#   nohup bash run_experiments.sh 3 > exp.log 2>&1 &
set -euo pipefail
cd "$(dirname "$0")"
REPS="${1:-3}"
SIZES="${SIZES:-512 1024 2048}"
BS="${BS:-64}"
JOPTS="${JOPTS:-}"
mkdir -p results
make -f parcs.mk build >/dev/null
cd out
CP='parcs.jar:MatMul.jar'

# ---------- Частина A: послідовні тести ----------
SEQ=../results/cloud_seq.csv
echo "rep,mode,n,bs,time_s,checksum" > "$SEQ"
for R in $(seq 1 "$REPS"); do
  for N in 256 512 1024; do                          # наївний алгоритм (для 2048 надто повільний)
    LINE=$(java $JOPTS -cp "$CP" SequentialMatMul "$N" naive | grep '^SEQCSV,' | tail -1)
    echo "$R,${LINE#SEQCSV,}" | tee -a "$SEQ"
  done
  for N in $SIZES; do                                # блоковий алгоритм
    LINE=$(java $JOPTS -cp "$CP" SequentialMatMul "$N" blocked "$BS" | grep '^SEQCSV,' | tail -1)
    echo "$R,${LINE#SEQCSV,}" | tee -a "$SEQ"
  done
  for B in 8 16 32 64 128 256; do                    # вплив розміру блоку (n = 1024)
    LINE=$(java $JOPTS -cp "$CP" SequentialMatMul 1024 blocked "$B" | grep '^SEQCSV,' | tail -1)
    echo "$R,${LINE#SEQCSV,}" | tee -a "$SEQ"
  done
done

# ---------- Частина B: ПАРКС ----------
OUT=../results/cloud_results.csv
echo "rep,mode,n,workers,bs,parcs_time_s,seq_time_s,speedup,correct" > "$OUT"
for N in $SIZES; do
  for MODE in flat tree; do
    for W in 1 2 4 8; do
      for R in $(seq 1 "$REPS"); do
        LINE=$(java $JOPTS -cp "$CP" MatMul "$N" "$W" "$MODE" "$BS" | grep '^CSV,' | tail -1)
        echo "$R,${LINE#CSV,}" | tee -a "$OUT"
      done
    done
  done
done
echo "Готово: results/cloud_seq.csv, results/cloud_results.csv"
