package parcs;
import java.io.*;
import java.util.concurrent.*;
/** Двоспрямований канал (імітація): повідомлення серіалізуються, як у мережі. */
public class channel {
    private final BlockingQueue<byte[]> in, out;
    private channel(BlockingQueue<byte[]> in, BlockingQueue<byte[]> out) { this.in = in; this.out = out; }
    static channel[] pair() {
        BlockingQueue<byte[]> a = new LinkedBlockingQueue<>(), b = new LinkedBlockingQueue<>();
        return new channel[]{new channel(a, b), new channel(b, a)};
    }
    public void write(Object o) {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream(); ObjectOutputStream oo = new ObjectOutputStream(bo)) {
            oo.writeObject(o); oo.flush(); out.put(bo.toByteArray());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    public void write(int v) { write((Object) Integer.valueOf(v)); }
    public void write(long v) { write((Object) Long.valueOf(v)); }
    public Object readObject() {
        try (ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(in.take()))) {
            return oi.readObject();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    public int readInt() { return (Integer) readObject(); }
    public long readLong() { return (Long) readObject(); }
}
