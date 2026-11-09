#!/usr/bin/env python3
"""Будує markdown-таблиці (і, якщо є matplotlib, графіки) з results/cloud_results.csv та results/cloud_seq.csv.

Використання (з кореня проєкту):
    python3 tools/report_tables.py            # таблиці в stdout, графіки в docs/*.png
Таблиці можна вставити в docs/testing_report.md, розділ «Результати в хмарі».
Для прискорення береться медіана часу повторів: S = T_seq(медіана) / T_parcs(медіана), E = S / p.
"""
import csv, os, statistics, sys
from collections import defaultdict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "results")
DOCS = os.path.join(ROOT, "docs")


def read(name):
    path = os.path.join(RES, name)
    if not os.path.exists(path):
        sys.exit(f"Немає файлу {path}")
    with open(path, newline="") as f:
        return list(csv.DictReader(f))


def med(xs):
    return statistics.median(xs)


def main():
    par, seq = read("cloud_results.csv"), read("cloud_seq.csv")

    # ---- послідовні: naive проти blocked ----
    s = defaultdict(list)
    for r in seq:
        s[(r["mode"], int(r["n"]), int(r["bs"]))].append(float(r["time_s"]))
    seq_med = {k: med(v) for k, v in s.items()}

    print("### Послідовні версії: naive проти blocked\n")
    print("| n | naive, с | blocked (bs=64), с | Прискорення blocked/naive |")
    print("|---:|---:|---:|---:|")
    for n in sorted({k[1] for k in seq_med if k[0] == "naive"}):
        tn = seq_med[("naive", n, 0)]
        tb = seq_med.get(("blocked", n, 64))
        if tb:
            print(f"| {n} | {tn:.3f} | {tb:.3f} | {tn / tb:.1f} |")
    print()
    print("### Вплив розміру блоку (blocked, n = 1024)\n")
    print("| Розмір блоку bs | Час, с |")
    print("|---:|---:|")
    bs_rows = sorted((k[2], v) for k, v in seq_med.items() if k[0] == "blocked" and k[1] == 1024)
    for bs, t in bs_rows:
        print(f"| {bs} | {t:.3f} |")
    print()

    # ---- ПАРКС ----
    g = defaultdict(list)
    for r in par:
        g[(r["mode"], int(r["n"]), int(r["workers"]))].append(r)
    table = {}
    for k, rows in g.items():
        tp = med([float(r["parcs_time_s"]) for r in rows])
        ts = med([float(r["seq_time_s"]) for r in rows])
        ok = all(r["correct"] == "true" for r in rows)
        table[k] = (tp, ts, ts / tp, ts / tp / k[2], ok)

    for mode in ("flat", "tree"):
        print(f"### ПАРКС, режим {mode}\n")
        print("| n | Воркерів | Час ПАРКС, с | Час послід., с | S | E | Коректно |")
        print("|---:|---:|---:|---:|---:|---:|:--:|")
        for k in sorted(k for k in table if k[0] == mode):
            tp, ts, sp, ef, ok = table[k]
            print(f"| {k[1]} | {k[2]} | {tp:.3f} | {ts:.3f} | {sp:.2f} | {ef:.2f} | {'так' if ok else 'НІ'} |")
        print()
    total, good = len(par), sum(1 for r in par if r["correct"] == "true")
    print(f"Коректних запусків: {good} з {total}\n")

    # ---- графіки ----
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("(matplotlib не встановлено — графіки пропущено; pip install matplotlib)")
        return
    sizes = sorted({k[1] for k in table})
    ws = sorted({k[2] for k in table})
    fig, ax = plt.subplots(1, len(sizes), figsize=(4.6 * len(sizes), 4), sharey=True, squeeze=False)
    for a, n in zip(ax[0], sizes):
        for mode, style in (("flat", "o-"), ("tree", "s--")):
            pts = [(w, table[(mode, n, w)][2]) for w in ws if (mode, n, w) in table]
            a.plot([p[0] for p in pts], [p[1] for p in pts], style, label=mode)
        a.axhline(1, color="gray", lw=.8)
        a.set_title(f"n = {n}")
        a.set_xlabel("Кількість воркерів")
        a.set_xticks(ws)
        a.grid(alpha=.3)
    ax[0][0].set_ylabel("Прискорення S = Tпосл / Tпарал")
    ax[0][0].legend()
    fig.tight_layout()
    fig.savefig(os.path.join(DOCS, "speedup.png"), dpi=150)

    fig, ax = plt.subplots(figsize=(6.5, 4.2))
    nmax = sizes[-1]
    for mode, style in (("flat", "o-"), ("tree", "s--")):
        pts = [(w, table[(mode, nmax, w)][0]) for w in ws if (mode, nmax, w) in table]
        ax.plot([p[0] for p in pts], [p[1] for p in pts], style, label=f"ПАРКС {mode}")
    ts = med([v[1] for k, v in table.items() if k[1] == nmax])
    ax.axhline(ts, color="red", ls=":", label="послідовна (blocked)")
    ax.set_xticks(ws); ax.set_xlabel("Кількість воркерів"); ax.set_ylabel("Час, с")
    ax.set_title(f"Час виконання, n = {nmax}"); ax.grid(alpha=.3); ax.legend()
    fig.tight_layout()
    fig.savefig(os.path.join(DOCS, f"time_n{nmax}.png"), dpi=150)

    if bs_rows:
        fig, ax = plt.subplots(figsize=(6, 4))
        ax.plot([b for b, _ in bs_rows], [t for _, t in bs_rows], "o-")
        ax.set_xscale("log", base=2)
        ax.set_xlabel("Розмір блоку bs"); ax.set_ylabel("Час, с"); ax.set_title("Вплив розміру блоку (n = 1024)")
        ax.grid(alpha=.3)
        fig.tight_layout()
        fig.savefig(os.path.join(DOCS, "block_size.png"), dpi=150)
    print("Графіки збережено в docs/: speedup.png, time_n%d.png, block_size.png" % nmax)


if __name__ == "__main__":
    main()
