import java.util.Arrays;
import java.util.Locale;

/**
 * Послідовна версія множення матриць — еталон для порівняння.
 * Запуск: java -cp MatMul.jar SequentialMatMul [n=512] [mode=blocked|naive|both] [blockSize=64] [seed=1]
 *  naive   — класичні три цикли (B читається за стовпцями);
 *  blocked — блоковий алгоритм (плитки bs x bs, B у транспонованому вигляді);
 *  both    — обидва + перевірка, що результати збігаються.
 */
public class SequentialMatMul {
    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 512;
        String mode = args.length > 1 ? args[1] : "blocked";
        int bs = args.length > 2 ? Integer.parseInt(args[2]) : MmUtil.DEFAULT_BS;
        long seed = args.length > 3 ? Long.parseLong(args[3]) : 1L;

        double[] a = MmUtil.generate(n, seed), b = MmUtil.generate(n, seed + 1);
        double[] bt = MmUtil.transpose(b, n);
        double[] cn = null, cb = null;

        if (mode.equals("naive") || mode.equals("both")) {
            long t0 = System.nanoTime();
            cn = MmUtil.naive(a, b, n);
            double t = (System.nanoTime() - t0) / 1e9;
            System.out.printf(Locale.ROOT, "SEQ naive   n=%d time=%.3f s checksum=%d%n", n, t, MmUtil.checksum(cn));
            System.out.println(String.format(Locale.ROOT, "SEQCSV,naive,%d,0,%.4f,%d", n, t, MmUtil.checksum(cn)));
        }
        if (mode.equals("blocked") || mode.equals("both")) {
            long t0 = System.nanoTime();
            cb = MmUtil.blocked(a, n, bt, n, n, bs);
            double t = (System.nanoTime() - t0) / 1e9;
            System.out.printf(Locale.ROOT, "SEQ blocked n=%d bs=%d time=%.3f s checksum=%d%n", n, bs, t, MmUtil.checksum(cb));
            System.out.println(String.format(Locale.ROOT, "SEQCSV,blocked,%d,%d,%.4f,%d", n, bs, t, MmUtil.checksum(cb)));
        }
        if (cn != null && cb != null) System.out.println("naive == blocked : " + Arrays.equals(cn, cb));
    }
}
