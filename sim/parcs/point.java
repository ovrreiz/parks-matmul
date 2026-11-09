package parcs;
/** Точка (імітація): окремий потік, що виконує AM; обмін даними — лише через канали з серіалізацією. */
public class point {
    private final task t;
    private final channel[] ends = channel.pair();
    point(task t) { this.t = t; }
    public channel createChannel() { return ends[0]; }
    public void execute(String className) {
        Thread th = new Thread(() -> {
            try {
                AM am = (AM) Class.forName(className).getDeclaredConstructor().newInstance();
                am.run(new AMInfo(t, ends[1]));
            } catch (Exception e) { e.printStackTrace(); }
        }, "point-" + className);
        th.setDaemon(true);
        th.start();
    }
}
