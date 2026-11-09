import parcs.*;
import java.util.Arrays;
import java.util.Locale;

/**
 * Головний AM задачі «Паралельне множення матриць (блочний алгоритм)» у ПАРКС-Java.
 * Будує керуючий простір (КП) «Дерево», розподіляє блоки матриці C між воркерами, збирає
 * результат і звіряє його з послідовним блоковим обчисленням.
 *
 * Аргументи: n workers [flat|tree] [blockSize=64] [seed=1]
 * Приклад:   java -cp parcs.jar:MatMul.jar MatMul 1024 4 tree
 *
 *  - flat : корінь ділить C на сітку gr x gc блоків (gr*gc = workers) і напряму створює воркерів;
 *  - tree : бінарне дерево з `workers` листками (workers = 2^k); кожен вузол ділить свій блок навпіл.
 */
public class MatMul implements AM {

    private final String[] args;

    public MatMul(String[] args) { this.args = args; }
    public MatMul() { this(new String[0]); }

    public static void main(String[] args) throws Exception {
        task curtask = new task();
        curtask.addJarFile("MatMul.jar");              // jar з MatMul, MmNode, MmUtil
        new MatMul(args).run(new AMInfo(curtask, null));
        curtask.end();
    }

    @Override
    public void run(AMInfo info) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 512;
        int workers = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        boolean tree = args.length > 2 && args[2].equalsIgnoreCase("tree");
        int bs = args.length > 3 ? Integer.parseInt(args[3]) : MmUtil.DEFAULT_BS;
        long seed = args.length > 4 ? Long.parseLong(args[4]) : 1L;

        double[] a = MmUtil.generate(n, seed);
        double[] bt = MmUtil.transpose(MmUtil.generate(n, seed + 1), n);

        long t0 = System.nanoTime();
        double[] c = parallelMultiply(info, n, bs, a, bt, workers, tree);
        long t1 = System.nanoTime();
        double[] ref = MmUtil.blocked(a, n, bt, n, n, bs);       // послідовний еталон (те саме ядро)
        long t2 = System.nanoTime();

        boolean ok = Arrays.equals(c, ref);
        double par = (t1 - t0) / 1e9, seq = (t2 - t1) / 1e9;
        System.out.printf(Locale.ROOT, "PARCS n=%d workers=%d mode=%s bs=%d time=%.3f s | SEQ time=%.3f s | speedup=%.2f | checksum=%d | correct=%b%n",
                n, workers, tree ? "tree" : "flat", bs, par, seq, seq / par, MmUtil.checksum(c), ok);
        // Рядок для автоматичного збору результатів (run_experiments.sh)
        System.out.println(String.format(Locale.ROOT, "CSV,%s,%d,%d,%d,%.4f,%.4f,%.2f,%b",
                tree ? "tree" : "flat", n, workers, bs, par, seq, seq / par, ok));
    }

    /** Будує КП, запускає обчислення й повертає матрицю C (n x n, рядками). */
    public static double[] parallelMultiply(AMInfo info, int n, int bs, double[] a, double[] bt,
                                            int workers, boolean tree) {
        if (tree && workers > 1) {
            if (Integer.bitCount(workers) != 1)
                throw new IllegalArgumentException("Для режиму tree workers має бути степенем двійки");
            return MmNode.splitTwo(info, Integer.numberOfTrailingZeros(workers), n, n, n, bs, a, bt);
        }
        // flat: сітка gr x gc блоків C, воркер (I,J) отримує смугу рядків A_I і смугу стовпців B_J
        int[] g = MmUtil.grid(workers);
        int gr = g[0], gc = g[1];
        if (gr > n || gc > n) throw new IllegalArgumentException("Забагато воркерів для матриці n=" + n);
        int[] rb = MmUtil.bounds(n, gr), cbd = MmUtil.bounds(n, gc);
        channel[] ch = new channel[workers];
        for (int I = 0; I < gr; I++)
            for (int J = 0; J < gc; J++) {
                int k = I * gc + J;
                double[] ai = Arrays.copyOfRange(a, rb[I] * n, rb[I + 1] * n);
                double[] bi = Arrays.copyOfRange(bt, cbd[J] * n, cbd[J + 1] * n);
                point p = info.createPoint();
                ch[k] = p.createChannel();
                p.execute("MmNode");
                MmNode.send(ch[k], 0, n, rb[I + 1] - rb[I], cbd[J + 1] - cbd[J], bs, ai, bi);
            }
        double[] c = new double[n * n];
        for (int I = 0; I < gr; I++)
            for (int J = 0; J < gc; J++) {
                double[] part = (double[]) ch[I * gc + J].readObject();
                int w = cbd[J + 1] - cbd[J];
                for (int r = 0; r < rb[I + 1] - rb[I]; r++)
                    System.arraycopy(part, r * w, c, (rb[I] + r) * n + cbd[J], w);
            }
        return c;
    }
}
