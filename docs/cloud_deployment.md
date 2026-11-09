# Розгортання ПАРКС-Java у Google Cloud та запуск експериментів

Інструкція за методичкою «PARCS-Java_Google_Cloud_0925» (платформа `github.com/lionell/labs/tree/master/parcs`). Усі команди виконуються у власному акаунті Google Cloud (безкоштовна підписка: https://cloud.google.com/free), у **Cloud Shell**.

Кластер ПАРКС не залежить від задачі: змінюється тільки код на VM `app`. Якщо VM та демони вже є (від попередньої роботи), крок 1 пропускається.

## 0. Архітектура

| VM | Роль | Внутрішній IP (наш кластер) |
|---|---|---|
| `hosts-server` | HostsServer — видає вільні демони при створенні точок | 10.166.0.4 |
| `daemon-1`, `daemon-2` | Daemon — виконують точки (AM) | 10.166.0.5, 10.166.0.3 |
| `app` | застосунок: збирає `MatMul.jar` і запускає завдання | 10.166.0.2 |

Проєкт: `sublime-state-514000-f9`, зона `europe-north1-b`, тип VM `e2-medium`, Debian 12, OpenJDK 17.

## 1. Створення VM (лише якщо їх ще немає)

```bash
gcloud config set project <PROJECT_ID>
gcloud config set compute/zone europe-north1-b
gcloud compute instances create hosts-server daemon-1 daemon-2 app
gcloud compute firewall-rules create allow-all --direction=INGRESS --priority=1000 \
  --network=default --action=ALLOW --rules=all --source-ranges=0.0.0.0/0
gcloud compute instances list      # запишіть INTERNAL_IP кожної VM
```
На кожній VM: `sudo apt-get update && sudo apt-get install -y openjdk-17-jdk` (на `app` ще `make git`).
Демони: `wget https://github.com/lionell/labs/raw/master/parcs/Daemon/Daemon.jar`.
HostsServer: `wget https://github.com/lionell/labs/raw/master/parcs/HostsServer/TCPHostsServer.jar`.

## 2. Підготовка наявного кластера

**2.1. Перевірте, що VM працюють** (`STATUS: RUNNING`). Якщо ні — `gcloud compute instances start <ім'я> --zone=europe-north1-b`.

```bash
gcloud compute instances list
```

**2.2. HostsServer** (процеси не переживають вимкнення/розрив сесії, тому запускаємо через `setsid nohup`):

```bash
gcloud compute ssh hosts-server --zone=europe-north1-b --command='cat ~/hosts.list; pgrep -af TCPHostsServer'
```
У `hosts.list` мають бути рівно дві IP-адреси демонів (без коментарів). Якщо процесу немає — запустіть:

```bash
gcloud compute ssh hosts-server --zone=europe-north1-b --command='cd ~ && mv -f hs.log hs_prev.log 2>/dev/null; setsid nohup java -jar TCPHostsServer.jar > hs.log 2>&1 < /dev/null & sleep 2; pgrep -af TCPHostsServer'
```

**2.3. Демони** (мають бути процеси `Daemon.jar`):

```bash
gcloud compute ssh daemon-1 --zone=europe-north1-b --command='pgrep -af Daemon.jar'
gcloud compute ssh daemon-2 --zone=europe-north1-b --command='pgrep -af Daemon.jar'
```
Якщо процесу немає:
```bash
gcloud compute ssh daemon-1 --zone=europe-north1-b --command='cd ~ && setsid nohup java -jar Daemon.jar > daemon.log 2>&1 < /dev/null & sleep 2; pgrep -af Daemon.jar'
```

## 3. Застосунок на VM `app`

Код проєкту має бути у вашому репозиторії GitHub. Далі `<LOGIN>/<REPO>` — ваш репозиторій.

```bash
gcloud compute ssh app --zone=europe-north1-b
# --- далі команди на VM app ---
java -version; javac -version; make --version | head -1; git --version
# якщо чогось немає:  sudo apt-get update && sudo apt-get install -y openjdk-17-jdk make git

cd ~
git clone https://github.com/<LOGIN>/<REPO>.git parks-matmul
cd parks-matmul
cp ~/parks-montecarlo/out/parcs.jar out/ 2>/dev/null || wget -O out/parcs.jar https://github.com/lionell/labs/raw/master/parcs/Fibon/out/parcs.jar
echo 10.166.0.4 > out/server           # внутрішня IP hosts-server
```

**Збірка і перевірка:**
```bash
make -f parcs.mk build                           # має завершитись без помилок
make -f parcs.mk seq N=512                       # послідовні naive і blocked, "naive == blocked : true"
make -f parcs.mk run N=512 W=4 MODE=tree         # у виводі шукайте correct=true
```
Якщо `make ... build` дає помилки компіляції — надішліть текст помилки.

## 4. Серія експериментів

```bash
nohup bash run_experiments.sh 3 > exp.log 2>&1 &
tail -f exp.log                                  # стежити за ходом; вийти з перегляду — Ctrl+C
```
Серія не зупиниться, якщо закрити SSH. Орієнтовна тривалість — 15–30 хвилин. Вона складається з:

* **Частина A — послідовні тести** → `results/cloud_seq.csv` (очікується 37 рядків): naive проти blocked для різних n та вплив розміру блоку bs.
* **Частина B — ПАРКС** → `results/cloud_results.csv` (очікується 73 рядки): n = 512, 1024, 2048; режими flat і tree; воркерів 1, 2, 4, 8; 3 повтори.

Перевірка після завершення (в `exp.log` має бути «Готово»):
```bash
wc -l results/cloud_seq.csv results/cloud_results.csv
cut -d, -f9 results/cloud_results.csv | sort | uniq -c       # усі значення мають бути true
```
Інші розміри/блок: `SIZES="512 1024 2048 4096" BS=32 JOPTS="-Xmx3g" bash run_experiments.sh 3` (для n = 4096 потрібно більше пам'яті й часу).

## 5. Збереження результатів і GitHub

Із Cloud Shell (не з VM):
```bash
gcloud compute scp --zone=europe-north1-b app:~/parks-matmul/results/cloud_results.csv ~/
gcloud compute scp --zone=europe-north1-b app:~/parks-matmul/results/cloud_seq.csv ~/
gcloud compute scp --zone=europe-north1-b app:~/parks-matmul/exp.log ~/
cloudshell download cloud_results.csv
cloudshell download cloud_seq.csv
```
Покладіть CSV у `results/` свого репозиторію (GitHub → Add file → Upload files).

Таблиці й графіки для звіту: `python3 tools/report_tables.py` (запускати в корені проєкту, де є папка `results/` із двома CSV).

## 6. Після завершення

Щоб не витрачати кредити, зупиніть (не видаляйте) VM, поки робота не здана:
```bash
gcloud compute instances stop app hosts-server daemon-1 daemon-2 --zone=europe-north1-b
```
Видалення після здачі:
```bash
gcloud compute instances delete app hosts-server daemon-1 daemon-2 --zone=europe-north1-b --quiet
gcloud compute firewall-rules delete allow-all --quiet
```
Після повторного `start` процеси `Daemon` та `TCPHostsServer` потрібно запустити знову (крок 2).

## 7. Скриншоти для звіту

1. `gcloud compute instances list` — 4 VM, тип, зона, RUNNING (+ ім'я проєкту в запрошенні).
2. `hosts.list` та процес `TCPHostsServer` на hosts-server.
3. Процес `Daemon.jar` на обох демонах.
4. На `app`: `nproc; lscpu | grep "Model name"; java -version; cat out/server`.
5. `make -f parcs.mk seq N=1024` — час naive і blocked.
6. `make -f parcs.mk run N=1024 W=8 MODE=tree` — час ПАРКС, speedup, `correct=true`.
7. `wc -l results/*.csv` та `head results/cloud_results.csv`.
8. `tail -15 hs.log` на hosts-server — розподіл точок між демонами.
9. Сторінка репозиторію на GitHub.
