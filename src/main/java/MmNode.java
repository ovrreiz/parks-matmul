import parcs.*;
import java.util.Arrays;

/**
 * Алгоритмічний модуль (AM) MmNode — виконується в кожній точці керуючого простору ПАРКС.
 *
 * Завдання вузла: обчислити блок C (ra x cb) = (смуга рядків A, ra x n) * (смуга стовпців B, n x cb).
 *
 * Протокол (канал info.parent). Читає: int levels, int n, int ra, int cb, int bs,
 *   double[] a (ra*n, рядками), double[] bt (cb*n, рядки Bt = стовпці B).
 * Пише: double[] c (ra*cb, рядками).
 *
 * Внутрішній вузол (levels > 0) ділить свій блок навпіл уздовж більшого виміру між двома дочірніми
 * точками, листок рахує сам блоковим ядром MmUtil.blocked.
 */
public class MmNode implements AM {

    @Override
    public void run(AMInfo info) {
        int levels = info.parent.readInt();
        int n = info.parent.readInt();
        int ra = info.parent.readInt();
        int cb = info.parent.readInt();
        int bs = info.parent.readInt();
        double[] a = (double[]) info.parent.readObject();
        double[] bt = (double[]) info.parent.readObject();

        double[] c;
        if (levels > 0 && (ra >= 2 || cb >= 2)) c = splitTwo(info, levels, n, ra, cb, bs, a, bt);
        else c = MmUtil.blocked(a, ra, bt, cb, n, bs);          // листок: власне обчислення
        info.parent.write(c);
    }

    /** Надсилає дочірній точці завдання (параметри + дві смуги). */
    static void send(channel ch, int levels, int n, int ra, int cb, int bs, double[] a, double[] bt) {
        ch.write(levels);
        ch.write(n);
        ch.write(ra);
        ch.write(cb);
        ch.write(bs);
        ch.write(a);
        ch.write(bt);
    }

    /**
     * Ділить блок ra x cb навпіл уздовж більшого виміру, віддає половини двом новим точкам
     * (рівень дерева levels-1) і збирає результат. Використовується і вузлами, і коренем у режимі tree.
     */
    static double[] splitTwo(AMInfo info, int levels, int n, int ra, int cb, int bs, double[] a, double[] bt) {
        boolean byRows = ra >= cb;
        int[] bd = MmUtil.bounds(byRows ? ra : cb, 2);
        channel[] ch = new channel[2];
        for (int i = 0; i < 2; i++) {
            int len = bd[i + 1] - bd[i];
            int rows = byRows ? len : ra, cols = byRows ? cb : len;
            double[] ai = byRows ? Arrays.copyOfRange(a, bd[i] * n, bd[i + 1] * n) : a;
            double[] bi = byRows ? bt : Arrays.copyOfRange(bt, bd[i] * n, bd[i + 1] * n);
            point p = info.createPoint();
            ch[i] = p.createChannel();
            p.execute("MmNode");
            send(ch[i], levels - 1, n, rows, cols, bs, ai, bi);
        }
        double[] c = new double[ra * cb];
        for (int i = 0; i < 2; i++) {
            double[] part = (double[]) ch[i].readObject();
            if (byRows) {
                System.arraycopy(part, 0, c, bd[i] * cb, part.length);      // рядки йдуть підряд
            } else {
                int w = bd[i + 1] - bd[i];
                for (int r = 0; r < ra; r++) System.arraycopy(part, r * w, c, r * cb + bd[i], w);
            }
        }
        return c;
    }
}
