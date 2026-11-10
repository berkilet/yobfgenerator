package legal.erk.yobf.rules;

public record Uyari(Seviye seviye, String alan, String mesaj) {
    public enum Seviye { SARI, KIRMIZI }

    public static Uyari sari(String alan, String mesaj) { return new Uyari(Seviye.SARI, alan, mesaj); }
    public static Uyari kirmizi(String alan, String mesaj) { return new Uyari(Seviye.KIRMIZI, alan, mesaj); }

    @Override public String toString() {
        return (seviye == Seviye.KIRMIZI ? "● " : "○ ") + alan + ": " + mesaj;
    }
}
