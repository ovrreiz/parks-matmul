# Збірка і запуск у стилі прикладів lionell/labs (Fibon, Bluck).
# Потрібно: out/parcs.jar та out/server (внутрішня IP-адреса hosts-server).
#   make -f parcs.mk build                   — збірка out/MatMul.jar
#   make -f parcs.mk run N=1024 W=8 MODE=flat BS=64
#   make -f parcs.mk seq N=1024              — послідовна версія (naive і blocked)
# Префікс рецептів ">" замість табуляції — файл не ламається при копіюванні/завантаженні.
.RECIPEPREFIX = >
.PHONY: all clean build run seq

N ?= 512
W ?= 4
MODE ?= tree
BS ?= 64

all: run

clean:
> rm -rf cls out/MatMul.jar

out/MatMul.jar: out/parcs.jar src/main/java/*.java
> @mkdir -p cls
> @javac -encoding UTF-8 -cp out/parcs.jar -d cls src/main/java/*.java
> @jar cf out/MatMul.jar -C cls .

build: out/MatMul.jar

run: out/MatMul.jar
> @cd out && java -cp 'parcs.jar:MatMul.jar' MatMul $(N) $(W) $(MODE) $(BS)

seq: out/MatMul.jar
> @cd out && java -cp 'parcs.jar:MatMul.jar' SequentialMatMul $(N) both $(BS)
