# Лабораторна робота №1 — «Хмарні обчислення»
## Паралельне множення матриць (блочний алгоритм) на ПАРКС-Java

Мета: реалізувати задачу паралельного програмування — множення квадратних матриць C = A·B блоковим алгоритмом — у системі **ПАРКС-Java** за моделлю керуючого простору «Дерево», розгорнути її в Google Cloud, порівняти з послідовною версією та дослідити прискорення для різної кількості воркерів і розмірів матриць.

## Структура репозиторію

```
├── README.md                      цей файл
├── docs/
│   ├── algorithm_description.md   алгоритми, модель КП «Дерево», протокол, оцінка комунікацій
│   ├── cloud_deployment.md        розгортання в Google Cloud, запуск, список скриншотів
│   └── testing_report.md          звіт про тестування (заповнюється результатами з хмари)
├── src/main/java/
│   ├── MmUtil.java                спільна логіка: генерація, naive/blocked ядра, межі, сітка
│   ├── SequentialMatMul.java      послідовна версія (naive, blocked)
│   ├── MatMul.java                головний AM (корінь дерева), main()
│   └── MmNode.java                AM у вузлах/листках дерева
├── sim/                           локальна імітація API ПАРКС (parcs.*) і Check.java (перевірка коректності)
├── tools/report_tables.py         таблиці та графіки з CSV для звіту
├── results/                       cloud_seq.csv, cloud_results.csv (після запуску в хмарі)
├── hosts.list, parcs.mk, build.sh, run_experiments.sh
└── out/                           parcs.jar, server, MatMul.jar (створюються під час збірки)
```

## Алгоритм (коротко)

* **Послідовний:** `naive` (три цикли) і `blocked` (плитки bs × bs, B транспонована) — друге суттєво швидше завдяки кешу.
* **Паралельний:** матриця C ділиться на блоки C<sub>IJ</sub>; воркер отримує смугу рядків A<sub>I</sub> та смугу стовпців B<sub>J</sub>, рахує блок блоковим ядром і повертає його. Воркери між собою не спілкуються.
* **Режими КП:** `flat` — корінь створює p воркерів (сітка gr × gc); `tree` — бінарне дерево, кожен вузол ділить свій блок навпіл.
* **Перевірка:** матриці з цілих 0..9, суми точні, паралельний результат збігається з послідовним побітово (`correct=true`).

Деталі — у [docs/algorithm_description.md](docs/algorithm_description.md).

## Вимоги

JDK 8+ (у кластері OpenJDK 17), `make`, `parcs.jar` з платформи PARCS-Java (https://github.com/lionell/labs/tree/master/parcs) у `out/parcs.jar`.

## Збірка і запуск у ПАРКС-Java

Розгортання в Google Cloud — у [docs/cloud_deployment.md](docs/cloud_deployment.md). Коротко, на VM `app`:
```bash
wget -O out/parcs.jar https://github.com/lionell/labs/raw/master/parcs/Fibon/out/parcs.jar
echo <IP_hosts-server> > out/server
make -f parcs.mk build
make -f parcs.mk seq N=1024                               # послідовні naive і blocked
make -f parcs.mk run N=1024 W=8 MODE=tree BS=64           # ПАРКС: n=1024, 8 воркерів, дерево
nohup bash run_experiments.sh 3 > exp.log 2>&1 &          # серія експериментів
```
Перед цим мають працювати `TCPHostsServer` (з `hosts.list`) та `Daemon.jar` на вузлах. Вивід: час ПАРКС і послідовний час, прискорення, контрольна сума, `correct=true`.

## Локальна перевірка без кластера (імітація)

```bash
bash build.sh sim
java -Xmx2g -cp sim_out Check               # порівняння з наївним множенням на 81 конфігурації
java -Xmx2g -cp sim_out MatMul 512 4 tree
```
Імітація не відображає мережевих витрат і кількості ядер реального кластера, тому використовується лише для перевірки коректності.

## Результати

Хмарні вимірювання (Google Cloud, 2 демони `e2-medium`, 72 запуски, усі `correct=true`) та висновки — у [docs/testing_report.md](docs/testing_report.md); сирі дані — у `results/cloud_seq.csv`, `results/cloud_results.csv`.

Основні результати: блоковий алгоритм швидший за наївний у 2,4–3,7 раза; найкраще прискорення ПАРКС — S ≈ 2,30 (n = 2048, flat, 8 воркерів); для n = 512 паралельна версія повільніша за послідовну через вартість передачі даних.
