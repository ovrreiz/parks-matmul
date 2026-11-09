package parcs;
/** Контекст виконання AM: завдання та канал до батьківської точки. */
public class AMInfo {
    public final task curtask;
    public final channel parent;
    public AMInfo(task t, channel parent) { this.curtask = t; this.parent = parent; }
    public point createPoint() { return new point(curtask); }
}
