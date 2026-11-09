import parcs.*;
import java.util.*;

/**
 * Перевірка коректності на локальній імітації ПАРКС: порівнює паралельний результат з наївним
 * множенням для різних n (зокрема непарних і таких, що не діляться націло), режимів і кількості воркерів.
 */
public class Check {
    public static void main(String[] args) {
        int[] ns = {1, 2, 7, 64, 97, 130, 256};
        int[] ws = {1, 2, 3, 4, 6, 8};
        int[] bss = {1, 8, 64};
        int bad = 0, total = 0;
        for (int n : ns) {
            double[] a = MmUtil.generate(n, 5), b = MmUtil.generate(n, 6);
            double[] bt = MmUtil.transpose(b, n);
            double[] ref = MmUtil.naive(a, b, n);
            for (int bs : bss) {
                if (!Arrays.equals(ref, MmUtil.blocked(a, n, bt, n, n, bs))) { bad++; System.out.println("SEQ blocked FAIL n=" + n + " bs=" + bs); }
                total++;
            }
            for (String mode : new String[]{"flat", "tree"})
                for (int w : ws) {
                    boolean tree = mode.equals("tree");
                    if (tree && Integer.bitCount(w) != 1) continue;
                    if (w > n && !tree) continue;
                    total++;
                    try {
                        double[] c = MatMul.parallelMultiply(new AMInfo(new task(), null), n, 8, a, bt, w, tree);
                        if (!Arrays.equals(ref, c)) { bad++; System.out.println("FAIL n=" + n + " " + mode + " w=" + w); }
                    } catch (Exception e) { bad++; System.out.println("ERR n=" + n + " " + mode + " w=" + w + " " + e); }
                }
        }
        System.out.println(bad == 0 ? "ALL OK: " + total + " конфігурацій" : "FAILED: " + bad + " з " + total);
        System.exit(bad == 0 ? 0 : 1);
    }
}
