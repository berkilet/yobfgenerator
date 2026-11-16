package legal.erk.yobf.ai;

/** Yapay zekâ çağrısında kullanıcıya gösterilecek, anlaşılır hata. */
public class ModelHatasi extends Exception {
    public ModelHatasi(String m, Throwable t) { super(m, t); }
    public ModelHatasi(String m) { super(m); }
}
