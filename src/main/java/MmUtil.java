import java.util.SplittableRandom;

/**
 * Спільна логіка множення матриць для послідовної та паралельної версій.
 *
 * Матриці квадратні n x n, зберігаються як одновимірні масиви double[] (рядок за рядком).
 * Елементи — цілі числа 0..9 (у вигляді double), тому всі суми точні й результат не залежить
 * від порядку додавання: паралельний C збігається з послідовним ТОЧНО (перевірка Arrays.equals).
 *
 * Матриця B передається у ТРАНСПОНОВАНОМУ вигляді Bt (Bt[j*n + l] = B[l][j]). Тоді і рядок A,
 * і "стовпець" B лежать у пам'яті підряд, а смугу стовпців B можна вирізати як суцільний шматок масиву.
 */
public final class MmUtil {
    private MmUtil() { }

    /** Розмір блоку (плитки) за замовчуванням. 3 плитки 64x64 double = 96 КБ — вміщуються в L2. */
    public static final int DEFAULT_BS = 64;

    /** Матриця n x n з псевдовипадкових цілих 0..9 (відтворювана за seed). */
    public static double[] generate(int n, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] m = new double[n * n];
        for (int i = 0; i < m.length; i++) m[i] = r.nextInt(10);
        return m;
    }

    /** Транспонування квадратної матриці n x n (блоками, щоб не промахуватись повз кеш). */
    public static double[] transpose(double[] m, int n) {
        double[] t = new double[n * n];
        final int B = 32;
        for (int ii = 0; ii < n; ii += B)
            for (int jj = 0; jj < n; jj += B)
                for (int i = ii; i < Math.min(ii + B, n); i++)
                    for (int j = jj; j < Math.min(jj + B, n); j++)
                        t[j * n + i] = m[i * n + j];
        return t;
    }

    /** Класичне множення "i-j-l" (три цикли, B читається за стовпцями) — НАЇВНИЙ еталон. */
    public static double[] naive(double[] a, double[] b, int n) {
        double[] c = new double[n * n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double s = 0;
                for (int l = 0; l < n; l++) s += a[i * n + l] * b[l * n + j];
                c[i * n + j] = s;
            }
        return c;
    }

    /**
     * БЛОКОВЕ множення: C (ra x cb) = A (ra x n) * B (n x cb), де B задано як Bt (cb x n).
     * Три цикли по плитках (ii, jj, kk) розміром bs; усередині плитки — скалярні добутки
     * рядків A і Bt. Плитки A і Bt розміром bs x bs багаторазово використовуються з кешу.
     * Це ядро виконує як послідовна версія, так і кожен листок дерева ПАРКС.
     */
    public static double[] blocked(double[] a, int ra, double[] bt, int cb, int n, int bs) {
        double[] c = new double[ra * cb];
        for (int ii = 0; ii < ra; ii += bs) {
            int iMax = Math.min(ii + bs, ra);
            for (int jj = 0; jj < cb; jj += bs) {
                int jMax = Math.min(jj + bs, cb);
                for (int kk = 0; kk < n; kk += bs) {
                    int kMax = Math.min(kk + bs, n);
                    for (int i = ii; i < iMax; i++) {
                        int ai = i * n;
                        for (int j = jj; j < jMax; j++) {
                            int bj = j * n;
                            double s = 0;
                            for (int l = kk; l < kMax; l++) s += a[ai + l] * bt[bj + l];
                            c[i * cb + j] += s;
                        }
                    }
                }
            }
        }
        return c;
    }

    /** Контрольна сума результату (сума всіх елементів; елементи цілі, тому точна). */
    public static long checksum(double[] c) {
        double s = 0;
        for (double v : c) s += v;
        return (long) s;
    }

    /** Ділить [0,n) на parts смуг (різниця довжин <= 1); повертає parts+1 меж. */
    public static int[] bounds(int n, int parts) {
        int[] b = new int[parts + 1];
        int base = n / parts, rest = n % parts;
        for (int i = 0; i < parts; i++) b[i + 1] = b[i] + base + (i < rest ? 1 : 0);
        return b;
    }

    /** Сітка воркерів gr x gc для p воркерів: gr — найбільший дільник p, що не перевищує sqrt(p). */
    public static int[] grid(int p) {
        int gr = 1;
        for (int d = 1; (long) d * d <= p; d++) if (p % d == 0) gr = d;
        return new int[]{gr, p / gr};
    }
}
