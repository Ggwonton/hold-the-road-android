package com.ashenlantern.holdtheroad;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Minimal reader for Android's native crash record (tombstone protobuf, Android 12+). */
final class Tomb {
    private final byte[] b; private int p; private final int end;
    private Tomb(byte[] b, int start, int end) { this.b = b; this.p = start; this.end = end; }

    private boolean more() { return p < end; }
    private long varint() { long r = 0; int s = 0; while (p < end) { int x = b[p++] & 0xff; r |= (long) (x & 0x7f) << s; if (x < 0x80) break; s += 7; } return r; }
    private int[] len() { int n = (int) varint(); int a = p; p = Math.min(end, p + n); return new int[]{a, p}; }
    private String str(int[] r) { return new String(b, r[0], r[1] - r[0], StandardCharsets.UTF_8); }
    private void skip(int wt) { if (wt == 0) varint(); else if (wt == 1) p += 8; else if (wt == 2) len(); else if (wt == 5) p += 4; else p = end; }

    static final class Frame { long relPc; String fn = "", file = ""; long off; }
    static final class Thr { long id; String name = ""; List<Frame> frames = new ArrayList<>(); List<String> notes = new ArrayList<>(); }
    static final class Log { String ts = "", tag = ""; String msg = ""; long pid, tid, prio; }

    static String decode(byte[] b, int n) {
        try {
            Tomb t = new Tomb(b, 0, n);
            long tid = -1, pid = -1, uptime = -1; String abort = "", sigName = "", codeName = ""; long fault = -1;
            List<String> causes = new ArrayList<>(); List<Thr> threads = new ArrayList<>(); List<Log> logs = new ArrayList<>();
            while (t.more()) {
                long key = t.varint(); int f = (int) (key >>> 3), wt = (int) (key & 7);
                if (f == 5 && wt == 0) pid = t.varint();
                else if (f == 6 && wt == 0) tid = t.varint();
                else if (f == 20 && wt == 0) uptime = t.varint();
                else if (f == 14 && wt == 2) abort = t.str(t.len());
                else if (f == 10 && wt == 2) {
                    int[] r = t.len(); Tomb s = new Tomb(b, r[0], r[1]);
                    while (s.more()) { long k = s.varint(); int ff = (int) (k >>> 3), w = (int) (k & 7);
                        if (ff == 2 && w == 2) sigName = s.str(s.len()); else if (ff == 4 && w == 2) codeName = s.str(s.len());
                        else if (ff == 9 && w == 0) fault = s.varint(); else s.skip(w); }
                } else if (f == 15 && wt == 2) {
                    int[] r = t.len(); Tomb s = new Tomb(b, r[0], r[1]);
                    while (s.more()) { long k = s.varint(); int ff = (int) (k >>> 3), w = (int) (k & 7);
                        if (ff == 1 && w == 2) causes.add(s.str(s.len())); else s.skip(w); }
                } else if (f == 16 && wt == 2) {
                    int[] r = t.len(); Tomb e = new Tomb(b, r[0], r[1]);
                    while (e.more()) { long k = e.varint(); int ff = (int) (k >>> 3), w = (int) (k & 7);
                        if (ff == 2 && w == 2) threads.add(thread(b, e.len())); else e.skip(w); }
                } else if (f == 18 && wt == 2) {
                    int[] r = t.len(); Tomb lb = new Tomb(b, r[0], r[1]);
                    while (lb.more()) { long k = lb.varint(); int ff = (int) (k >>> 3), w = (int) (k & 7);
                        if (ff == 2 && w == 2) {
                            int[] mr = lb.len(); Tomb m = new Tomb(b, mr[0], mr[1]); Log L = new Log();
                            while (m.more()) { long kk = m.varint(); int f3 = (int) (kk >>> 3), w3 = (int) (kk & 7);
                                if (f3 == 1 && w3 == 2) L.ts = m.str(m.len()); else if (f3 == 2 && w3 == 0) L.pid = m.varint();
                                else if (f3 == 3 && w3 == 0) L.tid = m.varint(); else if (f3 == 4 && w3 == 0) L.prio = m.varint();
                                else if (f3 == 5 && w3 == 2) L.tag = m.str(m.len()); else if (f3 == 6 && w3 == 2) L.msg = m.str(m.len()); else m.skip(w3); }
                            logs.add(L);
                        } else lb.skip(w); }
                } else t.skip(wt);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "  native crash: %s %s fault=0x%x pid=%d tid=%d uptime=%ds%n", sigName, codeName, fault, pid, tid, uptime));
            if (!abort.isEmpty()) sb.append("  abort: ").append(abort).append('\n');
            for (String c : causes) sb.append("  cause: ").append(c).append('\n');
            Thr crash = null; for (Thr th : threads) if (th.id == tid) crash = th;
            if (crash != null) {
                sb.append("  CRASHED THREAD: ").append(crash.name).append(" (").append(crash.id).append(")\n");
                for (String nt : crash.notes) sb.append("    note: ").append(nt).append('\n');
                int i = 0;
                for (Frame fr : crash.frames) {
                    if (i >= 40) break;
                    String file = fr.file; int sl = file.lastIndexOf('/'); if (sl >= 0) file = file.substring(sl + 1);
                    sb.append(String.format(Locale.US, "    #%02d %08x %s%s%n", i++, fr.relPc, file, fr.fn.isEmpty() ? "" : " (" + (fr.fn.length() > 90 ? fr.fn.substring(0, 90) : fr.fn) + "+" + fr.off + ")"));
                }
            } else sb.append("  crashed thread not found (").append(threads.size()).append(" threads)\n");
            StringBuilder names = new StringBuilder();
            for (Thr th : threads) if (th != crash && names.length() < 700) names.append(th.name).append(", ");
            sb.append("  other threads: ").append(names).append('\n');
            int from = Math.max(0, logs.size() - 70);
            if (!logs.isEmpty()) sb.append("  last log lines:\n");
            String pr = "??VDIWEFS";
            for (int i = from; i < logs.size(); i++) {
                Log L = logs.get(i); String m = L.msg.replace('\n', ' ');
                if (m.length() > 180) m = m.substring(0, 180);
                int cc = L.ts.indexOf(':'); String ts = cc >= 2 ? L.ts.substring(cc - 2, Math.min(L.ts.length(), cc + 10)) : L.ts;
                sb.append("   ").append(ts).append(' ').append(L.prio < pr.length() ? pr.charAt((int) L.prio) : '?')
                        .append(' ').append(L.tid == tid ? "*" : "").append(L.tag).append(": ").append(m).append('\n');
            }
            return sb.toString();
        } catch (Throwable e) { return "  (could not decode crash record: " + e + ")\n"; }
    }

    private static Thr thread(byte[] b, int[] r) {
        Tomb s = new Tomb(b, r[0], r[1]); Thr th = new Thr();
        while (s.more()) { long k = s.varint(); int f = (int) (k >>> 3), w = (int) (k & 7);
            if (f == 1 && w == 0) th.id = s.varint();
            else if (f == 2 && w == 2) th.name = s.str(s.len());
            else if (f == 7 && w == 2) th.notes.add(s.str(s.len()));
            else if (f == 4 && w == 2) {
                int[] fr = s.len(); Tomb q = new Tomb(b, fr[0], fr[1]); Frame F = new Frame();
                while (q.more()) { long kk = q.varint(); int ff = (int) (kk >>> 3), ww = (int) (kk & 7);
                    if (ff == 1 && ww == 0) F.relPc = q.varint(); else if (ff == 4 && ww == 2) F.fn = q.str(q.len());
                    else if (ff == 5 && ww == 0) F.off = q.varint(); else if (ff == 6 && ww == 2) F.file = q.str(q.len()); else q.skip(ww); }
                th.frames.add(F);
            } else s.skip(w); }
        return th;
    }
}
